/**
 * Open Fuel Map
 * Copyright (C) 2026  David Wheatley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.davwheat.openfuelmap.list.impl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.davwheat.openfuelmap.common.location.LocationUpdatesProvider
import dev.davwheat.openfuelmap.common.location.UserLocation
import dev.davwheat.openfuelmap.data.DistanceUnit
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.data.repository.FuelTypeRepository
import dev.davwheat.openfuelmap.data.repository.SavedLocation
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtWithDistance
import dev.davwheat.openfuelmap.forecourts.api.model.PriceHistoryEntry
import dev.davwheat.openfuelmap.forecourts.api.repository.ForecourtRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Location used as the centre of the radius search. */
sealed interface SearchCenter {
    val latitude: Double
    val longitude: Double

    data class CurrentLocation(override val latitude: Double, override val longitude: Double) :
        SearchCenter

    data class Custom(override val latitude: Double, override val longitude: Double) : SearchCenter
}

/**
 * Minimum displacement (in metres) between successive device-location updates that we accept. Moves
 * smaller than this don't re-trigger a search — keeps the list steady when users are idle and
 * avoids battery churn from GPS noise. Roughly one city block.
 */
private const val MIN_LOCATION_UPDATE_DISTANCE_METERS: Float = 100f

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListViewModel
@Inject
constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val forecourtRepository: ForecourtRepository,
    fuelTypeRepository: FuelTypeRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val locationUpdatesProvider: LocationUpdatesProvider,
) : AndroidViewModel(application) {

    /** Flipped by the screen once it knows whether ACCESS_(FINE|COARSE)_LOCATION is granted. */
    private val _hasLocationPermission = MutableStateFlow(false)

    /**
     * Continuous device location, driven by [LocationUpdatesProvider]. Pauses when either location
     * permission is missing or the user has a custom pin set (in which case the device location
     * isn't used by the search). Exposed publicly so the custom-location picker can centre its map
     * on the user's last known position.
     */
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    val radiusMi: StateFlow<Float> =
        userPreferencesRepository.searchRadiusMi.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            10f,
        )

    val distanceUnit: StateFlow<DistanceUnit> =
        userPreferencesRepository.distanceUnit.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DistanceUnit.MILES,
        )

    val customLocation: StateFlow<SavedLocation?> =
        userPreferencesRepository.customSearchLocation.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    /** The effective centre of the search — user-picked pin when present, else device location. */
    val searchCenter: StateFlow<SearchCenter?> =
        combine(customLocation, _userLocation) { custom, user ->
                when {
                    custom != null -> SearchCenter.Custom(custom.latitude, custom.longitude)
                    user != null -> SearchCenter.CurrentLocation(user.latitude, user.longitude)
                    else -> null
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val excludedBrands: StateFlow<Set<String>> =
        userPreferencesRepository.excludedBrands.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptySet(),
        )

    val fuelTypes: StateFlow<List<FuelTypeEntity>> =
        fuelTypeRepository
            .getAllFuelTypes()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedFuelType: StateFlow<String?> =
        combine(fuelTypes, userPreferencesRepository.selectedFuelType) { types, saved ->
                when {
                    types.isEmpty() -> null
                    saved != null && types.any { it.id == saved } -> saved
                    else ->
                        types
                            .minBy { type ->
                                val idx = FuelTypeIds.PRIORITY_ORDER.indexOf(type.id)
                                if (idx >= 0) idx else FuelTypeIds.PRIORITY_ORDER.size
                            }
                            .id
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _results = MutableStateFlow<List<ForecourtWithDistance>>(emptyList())
    val results: StateFlow<List<ForecourtWithDistance>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedStation = MutableStateFlow<SelectedListStation?>(null)
    val selectedStation: StateFlow<SelectedListStation?> = _selectedStation.asStateFlow()

    private var detailFetchJob: Job? = null

    /**
     * Bumped by [retry] to re-run the search with unchanged parameters, which
     * [distinctUntilChanged] would otherwise swallow.
     */
    private val _refreshTrigger = MutableStateFlow(0)

    private val _priceHistory = MutableStateFlow<Map<String, List<PriceHistoryEntry>>>(emptyMap())
    val priceHistory: StateFlow<Map<String, List<PriceHistoryEntry>>> = _priceHistory.asStateFlow()

    private val _priceHistoryLoading = MutableStateFlow<Set<String>>(emptySet())
    val priceHistoryLoading: StateFlow<Set<String>> = _priceHistoryLoading.asStateFlow()

    /**
     * After the first successful result we sort by ascending price. Before that first result
     * arrives, [results] is empty and the screen shows its loading/empty state.
     */
    init {
        viewModelScope.launch {
            combine(searchCenter.filterNotNull(), radiusMi, selectedFuelType, excludedBrands) {
                    center,
                    radius,
                    fuel,
                    excluded ->
                    Quadruple(center, radius, fuel, excluded)
                }
                .combine(_refreshTrigger) { params, attempt -> params to attempt }
                .distinctUntilChanged()
                // Flip to loading as soon as params change so the UI doesn't briefly show the
                // "none in radius" empty state during the debounce window before the fetch fires.
                .onEach { _isLoading.value = true }
                // Debounce so dragging the radius slider doesn't fire a request every frame.
                .debounce(250L)
                .collect { (params, _) ->
                    val (center, radius, fuel, excluded) = params
                    fetchForecourts(center, radius, fuel, excluded)
                }
        }

        // Watch device location whenever permission is granted AND the user isn't overriding with
        // a custom pin. flatMapLatest tears down the subscription (and thus the fused-location
        // callback) whenever either gate closes, and re-subscribes when both are true again.
        viewModelScope.launch {
            combine(_hasLocationPermission, customLocation) { hasPerm, custom ->
                    hasPerm && custom == null
                }
                .distinctUntilChanged()
                .flatMapLatest { shouldWatch ->
                    if (shouldWatch) {
                        locationUpdatesProvider.locationUpdates(MIN_LOCATION_UPDATE_DISTANCE_METERS)
                    } else {
                        // Clear to null so stale positions don't drive the search when we've
                        // stopped watching (permission revoked or custom pin picked).
                        _userLocation.value = null
                        emptyFlow()
                    }
                }
                .collect { _userLocation.value = it }
        }
    }

    fun setHasLocationPermission(granted: Boolean) {
        _hasLocationPermission.value = granted
    }

    fun setRadiusMi(radius: Float) {
        viewModelScope.launch { userPreferencesRepository.setSearchRadiusMi(radius) }
    }

    fun retry() {
        _error.value = null
        _isLoading.value = true
        _refreshTrigger.value += 1
    }

    fun setCustomLocation(location: SavedLocation?) {
        viewModelScope.launch { userPreferencesRepository.setCustomSearchLocation(location) }
    }

    fun selectStation(station: Forecourt) {
        detailFetchJob?.cancel()
        _selectedStation.value = SelectedListStation(basic = station)
        detailFetchJob = viewModelScope.launch {
            when (val result = forecourtRepository.getForecourtDetail(station.nodeId)) {
                is ApiResult.Success -> {
                    val current = _selectedStation.value
                    if (current?.basic?.nodeId == station.nodeId) {
                        _selectedStation.value = current.copy(detail = result.data)
                    }
                }
                is ApiResult.Failure -> {
                    logFailure("selectStation(nodeId=${station.nodeId})", result)
                    _error.value = result.message
                }
            }
        }
    }

    fun fetchPriceHistory(fuelType: String) {
        val nodeId = _selectedStation.value?.basic?.nodeId ?: return
        if (fuelType in _priceHistoryLoading.value || fuelType in _priceHistory.value) return
        _priceHistoryLoading.value = _priceHistoryLoading.value + fuelType
        viewModelScope.launch {
            val since = LocalDate.now().minusDays(90).format(DateTimeFormatter.ISO_LOCAL_DATE)
            when (val result = forecourtRepository.getPriceHistory(nodeId, fuelType, since)) {
                is ApiResult.Success -> {
                    _priceHistory.value = _priceHistory.value + (fuelType to result.data)
                }
                is ApiResult.Failure -> {
                    logFailure("fetchPriceHistory(fuelType=$fuelType)", result)
                    _priceHistory.value = _priceHistory.value + (fuelType to emptyList())
                }
            }
            _priceHistoryLoading.value = _priceHistoryLoading.value - fuelType
        }
    }

    fun clearSelection() {
        detailFetchJob?.cancel()
        detailFetchJob = null
        _selectedStation.value = null
        _priceHistory.value = emptyMap()
        _priceHistoryLoading.value = emptySet()
    }

    /**
     * Re-sort the already-sorted-by-distance result list to ascending price. Stations without a
     * price sink to the bottom so users can still see them but never near the "cheapest" position.
     */
    private fun sortByPriceAsc(list: List<ForecourtWithDistance>): List<ForecourtWithDistance> =
        list.sortedWith(
            compareBy(nullsLast()) { it.forecourt.price?.price } //
        )

    private suspend fun fetchForecourts(
        center: SearchCenter,
        radiusMi: Float,
        fuelType: String?,
        excludeBrands: Set<String>,
    ) {
        _isLoading.value = true
        _error.value = null
        when (
            val result =
                forecourtRepository.getForecourtsNear(
                    centerLat = center.latitude,
                    centerLng = center.longitude,
                    radiusMiles = radiusMi.toDouble(),
                    fuelType = fuelType,
                    excludeBrands = excludeBrands,
                )
        ) {
            is ApiResult.Success ->
                _results.value = withContext(Dispatchers.Default) { sortByPriceAsc(result.data) }
            is ApiResult.Failure -> {
                logFailure("fetchForecourts", result)
                _error.value = result.message
            }
        }
        _isLoading.value = false
    }

    private fun logFailure(operation: String, failure: ApiResult.Failure) {
        when (failure) {
            is ApiResult.NetworkError ->
                Timber.w(failure.cause, "%s network error: %s", operation, failure.message)
            is ApiResult.ApiError -> Timber.w("%s API error: %s", operation, failure.message)
        }
    }
}

/** Currently-tapped station shown in the detail sheet. Mirrors the map's SelectedStation. */
data class SelectedListStation(val basic: Forecourt, val detail: ForecourtDetail? = null)

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
