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
package dev.davwheat.openfuelmap.common.maps

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

const val LIGHT_MAP_STYLE_URL = "https://osm-assets.coveragetiles.com/osm-bright-style.json"
const val DARK_MAP_STYLE_URL = "https://osm-assets.coveragetiles.com/dark-matter-style.json"

/** The style URL for the light or the dark setting of the system. */
@Composable
@ReadOnlyComposable
fun rememberMapStyleUrl(): String =
    if (isSystemInDarkTheme()) DARK_MAP_STYLE_URL else LIGHT_MAP_STYLE_URL
