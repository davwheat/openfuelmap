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

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.AnnotationManager
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.SymbolManager

/**
 * An annotation manager fails if its [Style] is not fully loaded. The [Style] from [MapSurface] can
 * change before Compose runs the content block again. A change of the style URL, for example a
 * change between the light and the dark theme, makes [MapSurface] call [MapLibreMap.setStyle]. That
 * function immediately clears the previous style, before the content block leaves the composition.
 *
 * Thus this function gives `null` in place of a failure. The effect around it runs again with the
 * new style when [MapSurface] gives it.
 */
fun createSymbolManagerOrNull(
    mapView: MapView,
    map: MapLibreMap,
    style: Style,
    configure: SymbolManager.() -> Unit = {},
): SymbolManager? =
    if (style.isFullyLoaded) SymbolManager(mapView, map, style).apply(configure) else null

/** The same as [createSymbolManagerOrNull], but for circle annotations. */
fun createCircleManagerOrNull(mapView: MapView, map: MapLibreMap, style: Style): CircleManager? =
    if (style.isFullyLoaded) CircleManager(mapView, map, style) else null

/**
 * Releases the layers and the sources of the manager, but only while its [Style] is alive. This
 * operation fails against a style that is cleared. That style also releases these resources when it
 * closes.
 */
fun AnnotationManager<*, *, *, *, *, *>.destroyIfStyleLoaded(style: Style) {
    if (style.isFullyLoaded) onDestroy()
}
