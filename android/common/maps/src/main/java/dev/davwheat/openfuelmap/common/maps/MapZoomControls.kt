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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/** The time in milliseconds of the camera movement for one step of zoom. */
private const val ZOOM_ANIMATION_MS = 250

/** The corner radius at the outer edges of the group, which gives each button its full shape. */
private val OuterCornerRadius = 16.dp

/** The corner radius where the two buttons touch. A small radius makes them read as one control. */
private val InnerCornerRadius = 4.dp

/**
 * The shape of a button while the user touches it. Material 3 Expressive makes each button of a
 * connected group become fully round on press, thus the touched button separates from its
 * neighbour. `Material` moves between this shape and the shape of the button.
 */
private val PressedShape = RoundedCornerShape(percent = 50)

/** The space between the two buttons. */
private val ButtonGap = 2.dp

/**
 * A margin in zoom levels. The zoom of the camera can stop a very small distance from a limit, thus
 * a test for equality would keep a button available that can do no more work.
 */
private const val ZOOM_LIMIT_MARGIN = 0.01f

/**
 * Two connected buttons that change the zoom of the map by one level. A button becomes unavailable
 * at the zoom limit of the map, because a touch there has no result.
 *
 * @param map The map to zoom. Use `null` while the map loads, and both buttons stay unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapZoomControls(map: MapLibreMap?, modifier: Modifier = Modifier) {
    var zoom by remember(map) { mutableFloatStateOf(0f) }

    DisposableEffect(map) {
        if (map == null) return@DisposableEffect onDispose {}
        zoom = map.cameraPosition.zoom.toFloat()
        val listener = MapLibreMap.OnCameraMoveListener { zoom = map.cameraPosition.zoom.toFloat() }
        // The move listener stops before the last frame of an animation, thus read the zoom again
        // when the camera stops. Without this, a button can stay available at the limit.
        val idleListener = MapLibreMap.OnCameraIdleListener {
            zoom = map.cameraPosition.zoom.toFloat()
        }
        map.addOnCameraMoveListener(listener)
        map.addOnCameraIdleListener(idleListener)
        onDispose {
            map.removeOnCameraMoveListener(listener)
            map.removeOnCameraIdleListener(idleListener)
        }
    }

    val canZoomIn = map != null && zoom < map.maxZoomLevel - ZOOM_LIMIT_MARGIN
    val canZoomOut = map != null && zoom > map.minZoomLevel + ZOOM_LIMIT_MARGIN

    val buttonSize = IconButtonDefaults.smallContainerSize()
    val zoomInText = stringResource(R.string.map_zoom_in)
    val zoomOutText = stringResource(R.string.map_zoom_out)

    // Both shapes are corner-based, thus Material animates between them while the user touches the
    // button. Remembered because the animation keys on the identity of the pair.
    val zoomInShapes = remember {
        IconButtonShapes(
            shape =
                RoundedCornerShape(
                    topStart = OuterCornerRadius,
                    topEnd = OuterCornerRadius,
                    bottomStart = InnerCornerRadius,
                    bottomEnd = InnerCornerRadius,
                ),
            pressedShape = PressedShape,
        )
    }
    val zoomOutShapes = remember {
        IconButtonShapes(
            shape =
                RoundedCornerShape(
                    topStart = InnerCornerRadius,
                    topEnd = InnerCornerRadius,
                    bottomStart = OuterCornerRadius,
                    bottomEnd = OuterCornerRadius,
                ),
            pressedShape = PressedShape,
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        SimpleTooltip(text = zoomInText) {
            FilledTonalIconButton(
                onClick = { map?.easeCamera(CameraUpdateFactory.zoomIn(), ZOOM_ANIMATION_MS) },
                shapes = zoomInShapes,
                enabled = canZoomIn,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = zoomInText)
            }
        }
        SimpleTooltip(text = zoomOutText) {
            FilledTonalIconButton(
                onClick = { map?.easeCamera(CameraUpdateFactory.zoomOut(), ZOOM_ANIMATION_MS) },
                shapes = zoomOutShapes,
                enabled = canZoomOut,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = zoomOutText)
            }
        }
    }
}
