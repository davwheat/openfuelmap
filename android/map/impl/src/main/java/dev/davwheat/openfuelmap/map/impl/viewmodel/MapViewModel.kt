package dev.davwheat.openfuelmap.map.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.davwheat.openfuelmap.data.db.BrandEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.data.repository.BrandRepository
import dev.davwheat.openfuelmap.data.repository.FuelTypeRepository
import dev.davwheat.openfuelmap.data.repository.SavedCameraPosition
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.data.result.ApiResult
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class MapViewModel
@Inject
constructor(
    private val forecourtRepository: ForecourtRepository,
    fuelTypeRepository: FuelTypeRepository,
    brandRepository: BrandRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _forecourtResult =
        MutableStateFlow(ForecourtListResult(emptyList(), pricePercentiles = null))

    val excludedBrands: StateFlow<Set<String>> =
        userPreferencesRepository.excludedBrands.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptySet(),
        )

    /**
     * Display-ready markers for the currently loaded viewport. The per-station label, colour
     * position and z-index are computed off the main thread on [Dispatchers.Default].
     */
    val markers: StateFlow<List<StationMarker>> =
        _forecourtResult
            .map { buildMarkers(it.forecourts, it.pricePercentiles) }
            .flowOn(Dispatchers.Default)
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentBounds = MutableStateFlow<BoundingBox?>(null)

    val fuelTypes: StateFlow<List<FuelTypeEntity>> =
        fuelTypeRepository
            .getAllFuelTypes()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val brands: StateFlow<List<BrandEntity>> =
        brandRepository.getAllBrands().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    init {
        // Auto-refetch stations whenever bounds, selected fuel type, or excluded brands change.
        viewModelScope.launch {
            combine(_currentBounds.filterNotNull(), selectedFuelType, excludedBrands) {
                    bounds,
                    fuelType,
                    excluded ->
                    Triple(bounds, fuelType, excluded)
                }
                .collect { (bounds, fuelType, excluded) ->
                    fetchStations(bounds, fuelType, excluded)
                }
        }
    }

    fun loadStationsInBounds(bounds: BoundingBox) {
        _currentBounds.value = bounds
    }

    private suspend fun fetchStations(
        bounds: BoundingBox,
        fuelType: String?,
        excludeBrands: Set<String>,
    ) {
        _isLoading.value = true
        _error.value = null
        when (
            val result =
                forecourtRepository.getForecourts(
                    bounds = bounds,
                    fuelType = fuelType,
                    excludeBrands = excludeBrands,
                )
        ) {
            is ApiResult.Success -> _forecourtResult.value = result.data
            is ApiResult.Failure -> {
                logFailure("fetchStations", result)
                _error.value = result.message
            }
        }
        _isLoading.value = false
    }

    fun selectFuelType(fuelTypeId: String) {
        viewModelScope.launch { userPreferencesRepository.setSelectedFuelType(fuelTypeId) }
    }

    fun toggleBrandExcluded(brand: String) {
        viewModelScope.launch {
            val current = excludedBrands.value
            val next = if (brand in current) current - brand else current + brand
            userPreferencesRepository.setExcludedBrands(next)
        }
    }

    suspend fun loadInitialCameraPosition(): SavedCameraPosition? =
        userPreferencesRepository.lastCameraPosition.first()

    fun saveCameraPosition(position: SavedCameraPosition) {
        viewModelScope.launch { userPreferencesRepository.setLastCameraPosition(position) }
    }

    fun selectStation(station: Forecourt) {
        detailFetchJob?.cancel()
        _selectedStation.value = SelectedStation(basic = station)
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
            is ApiResult.ApiError -> Timber.w("%s API error: %s", operation, failure.message)
        }
    }
}
