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
package dev.davwheat.openfuelmap.stats.impl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
import kotlin.coroutines.cancellation.CancellationException
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
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val statsRepository: StatsRepository,
    fuelTypeRepository: FuelTypeRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : AndroidViewModel(application) {

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

    /** Bumped by [retry] to re-run the fetch with unchanged parameters. */
    private val _refreshTrigger = MutableStateFlow(0)

    init {
        Timber.tag(TAG).d("StatsViewModel created (instance=%s)", System.identityHashCode(this))
        viewModelScope.launch {
            Timber.tag(TAG).d("collect coroutine started")
            combine(_timeRange, _priceStat, _refreshTrigger) { range, stat, _ -> range to stat }
                .collect { (range, stat) -> fetchPrices(range, stat) }
        }
    }

    override fun onCleared() {
        Timber.tag(TAG).d("StatsViewModel cleared (instance=%s)", System.identityHashCode(this))
        Timber.tag(TAG).d(Exception("Stack trace"), "onCleared call site")
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
    }

    fun setPriceStat(stat: PriceStat) {
        _priceStat.value = stat
    }

    fun retry() {
        _error.value = null
        _isLoading.value = true
        _refreshTrigger.value += 1
    }

    private suspend fun fetchPrices(timeRange: TimeRange, stat: PriceStat) {
        Timber.tag(TAG).d("fetchPrices START range=%s stat=%s", timeRange, stat)
        _isLoading.value = true
        _error.value = null
        try {
            when (val result = statsRepository.getDailyPrices(timeRange, stat)) {
                is ApiResult.Success -> {
                    Timber.tag(TAG).d("fetchPrices SUCCESS (%d items)", result.data.size)
                    _prices.value = result.data.groupBy { it.fuelType }
                }
                is ApiResult.Failure -> {
                    when (result) {
                        is ApiResult.NetworkError ->
                            Timber.tag(TAG)
                                .w(result.cause, "fetchPrices network error: %s", result.message)
                        is ApiResult.ApiError ->
                            Timber.tag(TAG).w("fetchPrices API error: %s", result.message)
                    }
                    _error.value = result.message
                }
            }
        } catch (e: CancellationException) {
            Timber.tag(TAG).w(e, "fetchPrices CANCELLED")
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchPrices UNEXPECTED EXCEPTION")
        }
        _isLoading.value = false
    }

    private companion object {
        const val TAG = "StatsViewModel"
    }
}
