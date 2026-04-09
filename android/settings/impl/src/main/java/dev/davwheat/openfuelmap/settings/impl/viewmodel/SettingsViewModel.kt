package dev.davwheat.openfuelmap.settings.impl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.data.repository.BrandRepository
import dev.davwheat.openfuelmap.data.repository.FuelTypeRepository
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.data.utils.DispatcherProvider
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
                fuelTypes,
                selectedFuelType,
                brands,
                userPreferencesRepository.excludedBrands,
                userPreferencesRepository.colorblindMode,
            ) { fuelTypes, selectedFuel, brands, excluded, colorblind ->
                buildSettingsList(fuelTypes, selectedFuel, brands, excluded, colorblind)
            }
            .flowOn(dispatcherProvider.default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
