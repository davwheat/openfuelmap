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
package dev.davwheat.openfuelmap.map.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.map.api.MapNav
import dev.davwheat.openfuelmap.map.impl.ui.MapScreen
import dev.davwheat.openfuelmap.map.impl.viewmodel.MapViewModel

fun EntryProviderScope<NavKey>.mapEntryBuilder() {
    entry<MapNav.ForecourtMap> {
        val viewModel = hiltViewModel<MapViewModel>()
        MapScreen(viewModel = viewModel)
    }
}
