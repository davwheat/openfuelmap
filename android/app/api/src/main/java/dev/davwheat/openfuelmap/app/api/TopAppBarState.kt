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
package dev.davwheat.openfuelmap.app.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Holds the current top app bar content, allowing screens to provide their own TopAppBar to the
 * scene decorator's Scaffold.
 */
class TopAppBarState {
    var content: @Composable () -> Unit by mutableStateOf({})
}

val LocalTopAppBarState = compositionLocalOf<TopAppBarState> { error("No TopAppBarState provided") }

/**
 * Provides a TopAppBar composable to the scene decorator's Scaffold. Call this at the top of each
 * screen composable.
 */
@Composable
fun ProvideTopBar(content: @Composable () -> Unit) {
    val topAppBarState = LocalTopAppBarState.current
    val currentContent by rememberUpdatedState(content)
    topAppBarState.content = { currentContent() }
}
