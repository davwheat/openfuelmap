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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.maplibre.android.maps.MapLibreMapOptions

/**
 * Options that make the map draw into a `TextureView` in place of the default `SurfaceView`.
 *
 * A `SurfaceView` has its own hardware layer, which the system composites separately from the other
 * views. It thus does not move, change size, or fade with an animation of the view above it. A
 * movement between screens, or a sheet that opens above the map, shows this as a black area or as a
 * map that stays in position while the screen around it moves.
 *
 * A `TextureView` draws in the same layer as the other views, thus each animation is correct. The
 * cost is one more copy of each frame on the GPU.
 *
 * [MapSurface] makes the `MapView` with this value as its remember key, thus the result must stay
 * the same for each composition.
 */
@Composable
fun rememberTextureModeMapOptions(): MapLibreMapOptions {
    val context = LocalContext.current
    return remember(context) {
        MapLibreMapOptions.createFromAttributes(context).apply { textureMode(true) }
    }
}
