package dev.davwheat.openfuelmap.settings.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.data.repository.BrandRepository
import dev.davwheat.openfuelmap.data.repository.FuelTypeRepository
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.settings.impl.model.SettingsItem
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    fuelTypeRepository: FuelTypeRepository,
    brandRepository: BrandRepository,
) : ViewModel() {

    private val fuelTypes =
        fuelTypeRepository
            .getAllFuelTypes()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val brands =
        brandRepository.getAllBrands().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val excludedBrands =
        userPreferencesRepository.excludedBrands.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptySet(),
        )

    private val colorblindMode =
        userPreferencesRepository.colorblindMode.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )

    val settingsItems: StateFlow<List<SettingsItem>> =
        combine(fuelTypes, selectedFuelType, brands, excludedBrands, colorblindMode) {
                fuelTypes,
                selectedFuel,
                brands,
                excluded,
                colorblind ->
                buildSettingsList(fuelTypes, selectedFuel, brands, excluded, colorblind)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                buildSettingsList(
                    fuelTypes.value,
                    selectedFuelType.value,
                    brands.value,
                    excludedBrands.value,
                    colorblindMode.value,
                ),
            )

    private fun buildSettingsList(
        fuelTypes: List<dev.davwheat.openfuelmap.data.db.FuelTypeEntity>,
        selectedFuel: String?,
        brands: List<dev.davwheat.openfuelmap.data.db.BrandEntity>,
        excluded: Set<String>,
        colorblind: Boolean,
    ): List<SettingsItem> = buildList {
        add(
            SettingsItem.Toggle(
                key = "colorblind_mode",
                title = "Colorblind-friendly colours",
                description =
                    "Use blue-to-orange instead of green-to-red for price markers on the map.",
                checked = colorblind,
                onCheckedChange = ::setColorblindMode,
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
                    title = "Fuel type",
                    description =
                        "Only show stations offering this fuel type. Prices shown will also be for this fuel.",
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
                    title = "Brands",
                    description = "Tap a brand to hide its stations from results.",
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
            val current = excludedBrands.value
            val next = if (brand in current) current - brand else current + brand
            userPreferencesRepository.setExcludedBrands(next)
        }
    }

    private fun selectAllBrands() {
        viewModelScope.launch { userPreferencesRepository.setExcludedBrands(emptySet()) }
    }

    private fun deselectAllBrands() {
        viewModelScope.launch {
            userPreferencesRepository.setExcludedBrands(brands.value.map { it.name }.toSet())
        }
    }

    private fun setColorblindMode(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setColorblindMode(enabled) }
    }
}
