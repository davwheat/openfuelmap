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

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * State for [MapSurface] that the caller can hoist. It holds the active [MapLibreMap] and [Style]
 * as snapshot state, thus other code can read them. An example is a button handler that moves the
 * camera with `state.map?.easeCamera(...)`.
 *
 * Both values are `null` while the style loads: at the start, and for a short time after the style
 * URL changes. When they are not `null`, they always come from the same style.
 */
class MapSurfaceState internal constructor() {
    var map: MapLibreMap? by mutableStateOf(null)
        internal set

    var style: Style? by mutableStateOf(null)
        internal set
}

@Composable fun rememberMapSurfaceState(): MapSurfaceState = remember { MapSurfaceState() }

/**
 * Puts a MapLibre [MapView] in a Compose layout. It connects the [MapView] lifecycle functions
 * ([MapView.onCreate], [MapView.onStart], and the others) to the host [Lifecycle], and it loads the
 * style.
 *
 * The [content] block runs when the map and the style are ready. It runs again after a new
 * [styleUrl] loads. Put the effects that need a [MapLibreMap] or a [Style] in this block, for
 * example to add an annotation manager or to move the camera. The block also gets the [MapView],
 * because the annotation managers (`SymbolManager`, `CircleManager`) need it.
 *
 * In `LocalInspectionMode` (Compose previews and screenshot tests) this function shows nothing and
 * does not start MapLibre. Thus previews of parent composables continue to work.
 *
 * @param state State that the caller can hoist. Hoist it if code outside [content] must read
 *   `state.map` or `state.style`. If not, use the default.
 * @param mapOptions MapLibre options for the new [MapView]. The [MapView] uses this value as its
 *   remember key, thus a change makes a new [MapView].
 */
@Composable
fun MapSurface(
    styleUrl: String,
    modifier: Modifier = Modifier,
    state: MapSurfaceState = rememberMapSurfaceState(),
    mapOptions: MapLibreMapOptions? = null,
    content: @Composable (mapView: MapView, map: MapLibreMap, style: Style) -> Unit = { _, _, _ -> },
) {
    if (LocalInspectionMode.current) return

    val mapView = rememberMapViewWithLifecycle(mapOptions)

    LaunchedEffect(mapView, styleUrl) {
        state.map = null
        state.style = null
        val map = mapView.awaitMap()
        val style = map.awaitStyle(styleUrl)
        state.map = map
        state.style = style
    }

    AndroidView(modifier = modifier, factory = { mapView })

    val currentMap = state.map
    val currentStyle = state.style
    if (currentMap != null && currentStyle != null) {
        content(mapView, currentMap, currentStyle)
    }
}

/**
 * Makes a MapLibre [MapView] and connects its lifecycle functions ([MapView.onCreate],
 * [MapView.onStart], and the others) to the Compose [Lifecycle]. Thus the renderer gets and
 * releases its GPU resources at the correct time.
 */
@Composable
fun rememberMapViewWithLifecycle(options: MapLibreMapOptions? = null): MapView {
    val context = LocalContext.current
    val mapView =
        remember(options) {
            MapLibre.getInstance(context)
            if (options != null) MapView(context, options) else MapView(context)
        }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return mapView
}

/** Waits until [MapView.getMapAsync] gives its [MapLibreMap]. */
suspend fun MapView.awaitMap(): MapLibreMap = suspendCancellableCoroutine { cont ->
    getMapAsync { cont.resume(it) }
}

/** Waits until [MapLibreMap.setStyle] loads the style from [styleUrl]. */
suspend fun MapLibreMap.awaitStyle(styleUrl: String): Style = suspendCancellableCoroutine { cont ->
    setStyle(styleUrl) { style -> cont.resume(style) }
}
