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
package dev.davwheat.openfuelmap.settings.impl.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * A single item rendered on the settings screen. Each subtype carries its own state and callbacks,
 * so the screen simply iterates a `List<SettingsItem>` and dispatches rendering via exhaustive
 * `when`. Adding a new setting type means adding a sealed subtype here, a renderer in
 * `SettingsItemRow`, and a new entry in the ViewModel's list builder.
 */
@Stable
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
        val options: ImmutableList<T>,
        val selectedOption: T?,
        val optionLabel: (T) -> String,
        val onOptionSelected: (T) -> Unit,
    ) : SettingsItem

    /** Multi-select chip group with select/deselect all controls. */
    data class MultiSelectChips<T>(
        override val key: String,
        override val title: String,
        override val description: String?,
        val options: ImmutableList<T>,
        val excludedOptions: ImmutableSet<T>,
        val optionLabel: (T) -> String,
        val onOptionToggled: (T) -> Unit,
        val onSelectAll: () -> Unit,
        val onDeselectAll: () -> Unit,
    ) : SettingsItem
}
