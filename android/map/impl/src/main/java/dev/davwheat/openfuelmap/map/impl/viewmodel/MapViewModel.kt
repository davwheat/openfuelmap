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
package dev.davwheat.openfuelmap.map.impl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.data.repository.FuelTypeRepository
import dev.davwheat.openfuelmap.data.repository.SavedCameraPosition
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.data.utils.DispatcherProvider
import dev.davwheat.openfuelmap.forecourts.api.model.BoundingBox
import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtListResult
import dev.davwheat.openfuelmap.forecourts.api.model.PriceChange
import dev.davwheat.openfuelmap.forecourts.api.model.PriceHistoryEntry
import dev.davwheat.openfuelmap.forecourts.api.model.PricePercentiles
import dev.davwheat.openfuelmap.forecourts.api.repository.ForecourtRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface InitialPosition {
    data object Loading : InitialPosition

    data class Loaded(val position: SavedCameraPosition?) : InitialPosition
}

@HiltViewModel
class MapViewModel
@Inject
constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val forecourtRepository: ForecourtRepository,
    fuelTypeRepository: FuelTypeRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    dispatcherProvider: DispatcherProvider,
) : AndroidViewModel(application) {

    private val _currentBounds = MutableStateFlow<BoundingBox?>(null)

    private val _forecourtResult =
        MutableStateFlow(ForecourtListResult(emptyList(), pricePercentiles = null))

    val excludedBrands: StateFlow<Set<String>> =
        userPreferencesRepository.excludedBrands.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptySet(),
        )

    val colorblindMode: StateFlow<Boolean> =
        userPreferencesRepository.colorblindMode.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )

    /**
     * Display-ready markers for the currently loaded viewport. The per-station label, colour
     * position and z-index are computed off the main thread on [Dispatchers.Default].
     */
    val markers: StateFlow<List<StationMarker>> =
        _forecourtResult
            .map { buildMarkers(it.forecourts, it.pricePercentiles) }
            .distinctUntilChanged()
            .flowOn(dispatcherProvider.default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedStation = MutableStateFlow<SelectedStation?>(null)
    val selectedStation: StateFlow<SelectedStation?> = _selectedStation.asStateFlow()

    private var detailFetchJob: Job? = null

    private val _priceHistory = MutableStateFlow<Map<String, List<PriceHistoryEntry>>>(emptyMap())
    val priceHistory: StateFlow<Map<String, List<PriceHistoryEntry>>> = _priceHistory.asStateFlow()

    private val _priceHistoryLoading = MutableStateFlow<Set<String>>(emptySet())
    val priceHistoryLoading: StateFlow<Set<String>> = _priceHistoryLoading.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<ApiResult.Failure?>(null)
    val error: StateFlow<ApiResult.Failure?> = _error.asStateFlow()

    private val _initialPosition = MutableStateFlow<InitialPosition>(InitialPosition.Loading)
    val initialPosition: StateFlow<InitialPosition> = _initialPosition.asStateFlow()

    private val _hasLoadedOnce = MutableStateFlow(false)

    /**
     * True only after a fetch completes with no stations in the visible area. It stays false before
     * the first fetch, thus the map does not show the empty message while it still has no data.
     */
    val isAreaEmpty: StateFlow<Boolean> =
        combine(_hasLoadedOnce, _isLoading, _error, markers) { hasLoaded, loading, error, markers ->
                hasLoaded && !loading && error == null && markers.isEmpty()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    val fuelTypeNames: StateFlow<Map<String, String>> =
        fuelTypes
            .map { types -> types.associate { it.id to it.name } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            fuelTypes.collect { Timber.d("MapViewModel: fuelTypes changed, count=%d", it.size) }
        }
        viewModelScope.launch {
            selectedFuelType.collect {
                Timber.d("MapViewModel: selectedFuelType changed to %s", it)
            }
        }
        viewModelScope.launch {
            val saved = userPreferencesRepository.lastCameraPosition.first()
            _initialPosition.value = InitialPosition.Loaded(saved)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _stationsFetch: StateFlow<ForecourtListResult> =
        combine(_currentBounds.filterNotNull(), selectedFuelType.filterNotNull(), excludedBrands) {
                bounds,
                fuelType,
                excluded ->
                Timber.d(
                    "MapViewModel: combine bounds=%s fuelType=%s excludedCount=%d",
                    bounds,
                    fuelType,
                    excluded.size,
                )
                Triple(bounds, fuelType, excluded)
            }
            .onEach {
                _isLoading.value = true
                _error.value = null
            }
            .mapLatest { (bounds, fuelType, excluded) ->
                Timber.d(
                    "MapViewModel: fetching fuelType=%s excludeCount=%d",
                    fuelType,
                    excluded.size,
                )
                when (
                    val result =
                        forecourtRepository.getForecourts(
                            bounds = bounds,
                            fuelType = fuelType,
                            excludeBrands = excluded,
                        )
                ) {
                    is ApiResult.Success -> {
                        Timber.d(
                            "MapViewModel: fetch success count=%d",
                            result.data.forecourts.size,
                        )
                        _error.value = null
                        result.data
                    }
                    is ApiResult.Failure -> {
                        logFailure("fetchStations", result)
                        _error.value = result
                        _forecourtResult.value // keep previous data on failure
                    }
                }
            }
            .onEach {
                _forecourtResult.value = it
                _isLoading.value = false
                _hasLoadedOnce.value = true
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ForecourtListResult(emptyList(), pricePercentiles = null),
            )

    fun onCameraIdle(bounds: BoundingBox, position: SavedCameraPosition) {
        Timber.d("MapViewModel: onCameraIdle bounds=%s", bounds)
        _currentBounds.value = bounds
        viewModelScope.launch { userPreferencesRepository.setLastCameraPosition(position) }
    }

    fun selectStation(station: Forecourt) {
        detailFetchJob?.cancel()
        _selectedStation.value = SelectedStation(basic = station)
        _priceHistory.value = emptyMap()
        _priceHistoryLoading.value = emptySet()
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
                    _error.value = result
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

    private fun buildMarkers(
        stations: List<Forecourt>,
        percentiles: PricePercentiles?,
    ): List<StationMarker> {
        if (stations.isEmpty()) return emptyList()
        val rangeLow = percentiles?.low
        val rangeHigh = percentiles?.high
        val span = if (rangeLow != null && rangeHigh != null) rangeHigh - rangeLow else 0.0
        return stations.map { station ->
            val stationPrice = station.price?.price
            val arrow =
                when (station.price?.priceChange) {
                    PriceChange.INCREASE -> "↑"
                    PriceChange.DECREASE -> "↓"
                    else -> ""
                }
            val label = stationPrice?.let { "$arrow${it}p" } ?: "-"
            val colorPosition =
                if (stationPrice != null && rangeLow != null) {
                    if (span > 0.0) {
                        ((stationPrice - rangeLow) / span).toFloat().coerceIn(0f, 1f)
                    } else 0f
                } else null
            // Cheapest on top: negate the price so lower prices get a higher
            // zIndex. Stations with no price sit beneath all priced markers.
            val zIndex = stationPrice?.let { -it.toFloat() } ?: -1_000_000f
            StationMarker(
                isPriceInaccurate = station.price?.possiblyInaccurate != null,
                station = station,
                label = label,
                colorPosition = colorPosition,
                zIndex = zIndex,
            )
        }
    }

    private fun logFailure(operation: String, failure: ApiResult.Failure) {
        when (failure) {
            is ApiResult.NetworkError ->
                Timber.w(failure.cause, "%s network error: %s", operation, failure.message)
            is ApiResult.ParseError ->
                Timber.e(failure.cause, "%s parse error: %s", operation, failure.message)
            is ApiResult.ApiError -> Timber.w("%s API error: %s", operation, failure.message)
        }
    }
}
