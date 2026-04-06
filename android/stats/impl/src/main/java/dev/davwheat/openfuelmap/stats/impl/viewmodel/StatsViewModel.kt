package dev.davwheat.openfuelmap.stats.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.data.repository.FuelTypeRepository
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.stats.api.model.DailyMedianPrice
import dev.davwheat.openfuelmap.stats.api.model.PriceStat
import dev.davwheat.openfuelmap.stats.api.model.TimeRange
import dev.davwheat.openfuelmap.stats.api.repository.StatsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class StatsViewModel
@Inject
constructor(
    private val statsRepository: StatsRepository,
    fuelTypeRepository: FuelTypeRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

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

    private val _timeRange = MutableStateFlow(TimeRange.DAYS_28)
    val timeRange: StateFlow<TimeRange> = _timeRange.asStateFlow()

    private val _priceStat = MutableStateFlow(PriceStat.TRIMMED_MEAN)
    val priceStat: StateFlow<PriceStat> = _priceStat.asStateFlow()

    private val _prices = MutableStateFlow<Map<String, List<DailyMedianPrice>>>(emptyMap())
    val prices: StateFlow<Map<String, List<DailyMedianPrice>>> = _prices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_timeRange, _priceStat) { range, stat -> range to stat }
                .collect { (range, stat) -> fetchPrices(range, stat) }
        }
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
    }

    fun setPriceStat(stat: PriceStat) {
        _priceStat.value = stat
    }

    fun retry() {
        viewModelScope.launch { fetchPrices(_timeRange.value, _priceStat.value) }
    }

    private suspend fun fetchPrices(timeRange: TimeRange, stat: PriceStat) {
        _isLoading.value = true
        _error.value = null
        when (val result = statsRepository.getDailyPrices(timeRange, stat)) {
            is ApiResult.Success -> {
                _prices.value = result.data.groupBy { it.fuelType }
            }
            is ApiResult.Failure -> {
                when (result) {
                    is ApiResult.NetworkError ->
                        Timber.w(result.cause, "fetchPrices network error: %s", result.message)
                    is ApiResult.ApiError -> Timber.w("fetchPrices API error: %s", result.message)
                }
                _error.value = result.message
            }
        }
        _isLoading.value = false
    }
}
