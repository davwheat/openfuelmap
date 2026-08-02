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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/**
 * The time in milliseconds of the camera movement back to north. A rotation is a small change, thus
 * a short movement keeps the control quick to use.
 */
private const val RESET_BEARING_ANIMATION_MS = 350

/**
 * A dead band around north in degrees. Small changes in the calculation of the bearing keep the
 * compass on the screen after a reset, thus ignore a bearing in this range.
 */
private const val NORTH_DEAD_BAND_DEGREES = 0.5f

/**
 * A needle that shows the bearing of the map. It stays hidden while the map points north, and it
 * appears when the user turns the map. A touch moves the camera back to north.
 *
 * The map must permit rotation gestures. If it does not, the bearing is always 0 and this control
 * never appears.
 *
 * @param map The map to read the bearing from. Use `null` while the map loads.
 * @param alwaysShow Keeps the needle on the screen at all bearings, north included.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapCompass(map: MapLibreMap?, modifier: Modifier = Modifier, alwaysShow: Boolean = false) {
    var bearingDegrees by remember(map) { mutableFloatStateOf(0f) }

    DisposableEffect(map) {
        if (map == null) return@DisposableEffect onDispose {}
        bearingDegrees = map.cameraPosition.bearing.toFloat()
        val listener = MapLibreMap.OnCameraMoveListener {
            bearingDegrees = map.cameraPosition.bearing.toFloat()
        }
        map.addOnCameraMoveListener(listener)
        onDispose { map.removeOnCameraMoveListener(listener) }
    }

    val isNorthUp =
        !alwaysShow && bearingDegrees in -NORTH_DEAD_BAND_DEGREES..NORTH_DEAD_BAND_DEGREES
    AnimatedVisibility(
        visible = map != null && !isNorthUp,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val description = stringResource(R.string.map_compass)
        SimpleTooltip(text = description) {
            FilledTonalIconButton(
                onClick = {
                    map?.easeCamera(CameraUpdateFactory.bearingTo(0.0), RESET_BEARING_ANIMATION_MS)
                },
                modifier = Modifier.size(IconButtonDefaults.smallContainerSize()),
                shapes = IconButtonDefaults.shapes(),
            ) {
                // MapLibre gives the bearing clockwise from north. A rotation by the negative
                // bearing thus keeps the needle on true north.
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = description,
                    modifier = Modifier.rotate(-bearingDegrees),
                )
            }
        }
    }
}
