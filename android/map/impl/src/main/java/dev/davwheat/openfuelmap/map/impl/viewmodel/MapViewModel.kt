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
import dev.davwheat.openfuelmap.map.api.model.BoundingBox
import dev.davwheat.openfuelmap.map.api.model.Forecourt
import dev.davwheat.openfuelmap.map.api.repository.ForecourtRepository
import dev.davwheat.openfuelmap.map.api.result.ApiResult
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

    private val _stations = MutableStateFlow<List<Forecourt>>(emptyList())

    val selectedBrand: StateFlow<String?> =
        userPreferencesRepository.selectedBrand.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    /**
     * Display-ready markers for the currently loaded viewport. The per-station label, colour
     * position and z-index are computed off the main thread on [Dispatchers.Default].
     */
    val markers: StateFlow<List<StationMarker>> =
        _stations
            .map { buildMarkers(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedStation = MutableStateFlow<SelectedStation?>(null)
    val selectedStation: StateFlow<SelectedStation?> = _selectedStation.asStateFlow()

    private var detailFetchJob: Job? = null

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
        // Auto-refetch stations whenever bounds, selected fuel type, or selected brand changes.
        viewModelScope.launch {
            combine(_currentBounds.filterNotNull(), selectedFuelType, selectedBrand) {
                    bounds,
                    fuelType,
                    brand ->
                    Triple(bounds, fuelType, brand)
                }
                .collect { (bounds, fuelType, brand) -> fetchStations(bounds, fuelType, brand) }
        }
    }

    fun loadStationsInBounds(bounds: BoundingBox) {
        _currentBounds.value = bounds
    }

    private suspend fun fetchStations(bounds: BoundingBox, fuelType: String?, brand: String?) {
        _isLoading.value = true
        _error.value = null
        when (
            val result =
                forecourtRepository.getForecourts(
                    bounds = bounds,
                    fuelType = fuelType,
                    brand = brand,
                )
        ) {
            is ApiResult.Success -> _stations.value = result.data
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

    fun selectBrand(brand: String?) {
        viewModelScope.launch { userPreferencesRepository.setSelectedBrand(brand) }
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

    fun clearSelection() {
        detailFetchJob?.cancel()
        detailFetchJob = null
        _selectedStation.value = null
    }

    private fun buildMarkers(stations: List<Forecourt>): List<StationMarker> {
        if (stations.isEmpty()) return emptyList()
        // Normalise colour position against the 10th-90th percentile range so a single
        // unusually-cheap or unusually-expensive station can't compress the rest of the
        // markers into a narrow slice of the gradient. Outliers clamp to the ends.
        val sortedPrices = stations.mapNotNull { it.price?.price }.sorted()
        val rangeLow: Double?
        val rangeHigh: Double?
        when {
            sortedPrices.isEmpty() -> {
                rangeLow = null
                rangeHigh = null
            }
            sortedPrices.size < 5 -> {
                // Too few samples for meaningful percentiles — fall back to min/max.
                rangeLow = sortedPrices.first()
                rangeHigh = sortedPrices.last()
            }
            else -> {
                rangeLow = percentile(sortedPrices, 0.1)
                rangeHigh = percentile(sortedPrices, 0.9)
            }
        }
        val span = if (rangeLow != null && rangeHigh != null) rangeHigh - rangeLow else 0.0
        return stations.map { station ->
            val stationPrice = station.price?.price
            val label = stationPrice?.let { "${it}p" } ?: "-"
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

    /** Linear-interpolated percentile over an already-sorted, non-empty list. */
    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.size == 1) return sorted[0]
        val idx = p * (sorted.size - 1)
        val lo = idx.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.size - 1)
        val frac = idx - lo
        return sorted[lo] * (1.0 - frac) + sorted[hi] * frac
    }

    private fun logFailure(operation: String, failure: ApiResult.Failure) {
        when (failure) {
            is ApiResult.NetworkError ->
                Timber.w(failure.cause, "%s network error: %s", operation, failure.message)
            is ApiResult.ApiError -> Timber.w("%s API error: %s", operation, failure.message)
        }
    }
}
