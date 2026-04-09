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
package dev.davwheat.openfuelmap.settings.impl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.davwheat.openfuelmap.data.DistanceUnit
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.data.repository.BrandRepository
import dev.davwheat.openfuelmap.data.repository.FuelTypeRepository
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.data.utils.DispatcherProvider
import dev.davwheat.openfuelmap.settings.impl.R
import dev.davwheat.openfuelmap.settings.impl.model.SettingsItem
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val userPreferencesRepository: UserPreferencesRepository,
    fuelTypeRepository: FuelTypeRepository,
    brandRepository: BrandRepository,
    dispatcherProvider: DispatcherProvider,
) : AndroidViewModel(application) {

    init {
        Timber.tag(TAG).d("SettingsViewModel created (instance=%s)", System.identityHashCode(this))
    }

    override fun onCleared() {
        Timber.tag(TAG).d("SettingsViewModel cleared (instance=%s)", System.identityHashCode(this))
    }

    private val fuelTypes =
        fuelTypeRepository
            .getAllFuelTypes()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val brands = brandRepository.getAllBrands()

    private val selectedFuelType =
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

    val settingsItems: StateFlow<List<SettingsItem>?> =
        combine(
                combine(
                    fuelTypes,
                    selectedFuelType,
                    brands,
                    userPreferencesRepository.excludedBrands,
                ) { fuelTypes, selectedFuel, brands, excluded ->
                    SettingsInputs(fuelTypes, selectedFuel, brands, excluded)
                },
                userPreferencesRepository.colorblindMode,
                userPreferencesRepository.distanceUnit,
            ) { inputs, colorblind, distanceUnit ->
                buildSettingsList(
                    inputs.fuelTypes,
                    inputs.selectedFuel,
                    inputs.brands,
                    inputs.excluded,
                    colorblind,
                    distanceUnit,
                )
            }
            .flowOn(dispatcherProvider.default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun buildSettingsList(
        fuelTypes: List<dev.davwheat.openfuelmap.data.db.FuelTypeEntity>,
        selectedFuel: String?,
        brands: List<dev.davwheat.openfuelmap.data.db.BrandEntity>,
        excluded: Set<String>,
        colorblind: Boolean,
        distanceUnit: DistanceUnit,
    ): List<SettingsItem> = buildList {
        add(
            SettingsItem.Toggle(
                key = "colorblind_mode",
                title = application.getString(R.string.setting_colorblind_title),
                description = application.getString(R.string.setting_colorblind_description),
                checked = colorblind,
                onCheckedChange = ::setColorblindMode,
            )
        )
        add(
            SettingsItem.SingleSelectChips(
                key = "distance_unit",
                title = application.getString(R.string.setting_distance_unit_title),
                description = application.getString(R.string.setting_distance_unit_description),
                options = DistanceUnit.entries.toList(),
                selectedOption = distanceUnit,
                optionLabel = { unit ->
                    when (unit) {
                        DistanceUnit.MILES ->
                            application.getString(R.string.setting_distance_unit_miles)
                        DistanceUnit.KILOMETERS ->
                            application.getString(R.string.setting_distance_unit_km)
                    }
                },
                onOptionSelected = ::setDistanceUnit,
            )
        )
        if (fuelTypes.isNotEmpty()) {
            val sortedFuelTypes = fuelTypes.sortedBy { type ->
                val idx = FuelTypeIds.PRIORITY_ORDER.indexOf(type.id)
                if (idx >= 0) idx else FuelTypeIds.PRIORITY_ORDER.size
            }
            add(
                SettingsItem.SingleSelectChips(
                    key = "fuel_type",
                    title = application.getString(R.string.setting_fuel_type_title),
                    description = application.getString(R.string.setting_fuel_type_description),
                    options = sortedFuelTypes,
                    selectedOption = sortedFuelTypes.find { it.id == selectedFuel },
                    optionLabel = { it.name },
                    onOptionSelected = { selectFuelType(it.id) },
                )
            )
        }
        if (brands.isNotEmpty()) {
            val sortedBrands = brands.sortedBy { it.name }
            add(
                SettingsItem.MultiSelectChips(
                    key = "brands",
                    title = application.getString(R.string.setting_brands_title),
                    description = application.getString(R.string.setting_brands_description),
                    options = sortedBrands,
                    excludedOptions = sortedBrands.filter { it.name in excluded }.toSet(),
                    optionLabel = { it.name },
                    onOptionToggled = { toggleBrandExcluded(it.name) },
                    onSelectAll = ::selectAllBrands,
                    onDeselectAll = ::deselectAllBrands,
                )
            )
        }
    }

    private fun selectFuelType(id: String) {
        viewModelScope.launch { userPreferencesRepository.setSelectedFuelType(id) }
    }

    private fun toggleBrandExcluded(brand: String) {
        viewModelScope.launch {
            val current = userPreferencesRepository.excludedBrands.first()
            val next = if (brand in current) current - brand else current + brand
            userPreferencesRepository.setExcludedBrands(next)
        }
    }

    private fun selectAllBrands() {
        viewModelScope.launch { userPreferencesRepository.setExcludedBrands(emptySet()) }
    }

    private fun deselectAllBrands() {
        viewModelScope.launch {
            userPreferencesRepository.setExcludedBrands(brands.first().map { it.name }.toSet())
        }
    }

    private fun setColorblindMode(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setColorblindMode(enabled) }
    }

    private fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { userPreferencesRepository.setDistanceUnit(unit) }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

private data class SettingsInputs(
    val fuelTypes: List<dev.davwheat.openfuelmap.data.db.FuelTypeEntity>,
    val selectedFuel: String?,
    val brands: List<dev.davwheat.openfuelmap.data.db.BrandEntity>,
    val excluded: Set<String>,
)
