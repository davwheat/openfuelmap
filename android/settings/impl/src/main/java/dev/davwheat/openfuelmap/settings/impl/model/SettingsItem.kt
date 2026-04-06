package dev.davwheat.openfuelmap.settings.impl.model

/**
 * A single item rendered on the settings screen. Each subtype carries its own state and callbacks,
 * so the screen simply iterates a `List<SettingsItem>` and dispatches rendering via exhaustive
 * `when`. Adding a new setting type means adding a sealed subtype here, a renderer in
 * `SettingsItemRow`, and a new entry in the ViewModel's list builder.
 */
sealed interface SettingsItem {
    /** Stable key used for `LazyColumn` item identity. */
    val key: String
    val title: String
    val description: String?

    /** On/off switch. */
    data class Toggle(
        override val key: String,
        override val title: String,
        override val description: String?,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : SettingsItem

    /** Mutually-exclusive chip group (radio-style). */
    data class SingleSelectChips<T>(
        override val key: String,
        override val title: String,
        override val description: String?,
        val options: List<T>,
        val selectedOption: T?,
        val optionLabel: (T) -> String,
        val onOptionSelected: (T) -> Unit,
    ) : SettingsItem

    /** Multi-select chip group with select/deselect all controls. */
    data class MultiSelectChips<T>(
        override val key: String,
        override val title: String,
        override val description: String?,
        val options: List<T>,
        val excludedOptions: Set<T>,
        val optionLabel: (T) -> String,
        val onOptionToggled: (T) -> Unit,
        val onSelectAll: () -> Unit,
        val onDeselectAll: () -> Unit,
    ) : SettingsItem
}
