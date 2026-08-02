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
package dev.davwheat.openfuelmap.map.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.davwheat.openfuelmap.common.location.UserLocation
import dev.davwheat.openfuelmap.common.maps.createCircleManagerOrNull
import dev.davwheat.openfuelmap.common.maps.destroyIfStyleLoaded
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions

private const val UserLocationDotRadius = 7f
private const val UserLocationDotBorderWidth = 2f

/**
 * Draws the position of the device as a dot with a border. It draws nothing while [location] is
 * `null`, that is, before the user gives the permission, or before the first position becomes
 * available.
 *
 * MapLibre has a `LocationComponent` that uses its own location engine. But the app already has a
 * location flow and the permission control for it. Thus a circle annotation keeps one source for
 * the position of the user.
 */
@Composable
internal fun UserLocationDot(
    mapView: MapView,
    map: MapLibreMap,
    style: Style,
    location: UserLocation?,
    fillColor: Color,
    borderColor: Color,
) {
    var circleManager by remember(style) { mutableStateOf<CircleManager?>(null) }
    DisposableEffect(mapView, map, style) {
        val manager = createCircleManagerOrNull(mapView, map, style)
        circleManager = manager
        onDispose { manager?.destroyIfStyleLoaded(style) }
    }

    LaunchedEffect(circleManager, location, fillColor, borderColor) {
        val manager = circleManager ?: return@LaunchedEffect
        manager.deleteAll()
        if (location == null) return@LaunchedEffect
        manager.create(
            CircleOptions()
                .withLatLng(LatLng(location.latitude, location.longitude))
                .withCircleRadius(UserLocationDotRadius)
                .withCircleColor(fillColor.toHexRgb())
                .withCircleStrokeWidth(UserLocationDotBorderWidth)
                .withCircleStrokeColor(borderColor.toHexRgb())
        )
    }
}

/** MapLibre annotation properties take a colour as a hexadecimal string, and not as an integer. */
private fun Color.toHexRgb(): String = "#%06X".format(0xFFFFFF and toArgb())
