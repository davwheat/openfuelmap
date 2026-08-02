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

import dev.davwheat.openfuelmap.map.impl.viewmodel.StationMarker
import kotlin.math.floor

/**
 * Zoom level at and above which clustering is disabled. Below this zoom individual stations are
 * grouped into cluster badges; at or above it every station renders as its own price pill.
 *
 * Tweakable — raising it keeps clusters visible further into street-level zoom, lowering it lets
 * pills appear earlier (at the risk of overlapping in dense cities).
 */
internal const val CLUSTER_DISABLED_AT_ZOOM: Float = 12f

/**
 * Controls cluster cell granularity. Larger values = more cells = fewer stations per cluster. The
 * cell width in degrees at zoom `z` is `360 / 2^(z + CLUSTER_CELL_ZOOM_OFFSET)`.
 */
private const val CLUSTER_CELL_ZOOM_OFFSET: Int = 3

/** A marker to render on the map — either one station or a cluster of them. */
internal sealed interface MapCluster {
    data class Single(val marker: StationMarker) : MapCluster

    data class Group(
        /**
         * Stable identity derived from this cluster's grid cell coordinates at the current zoom.
         * Used as a Compose `key` so the marker retains its composable node (and its underlying
         * native GoogleMap marker) across recomputes when the same cell keeps clustering — panning
         * doesn't tear down and rebuild unchanged clusters just because the output list's iteration
         * order shuffled.
         */
        val cellKey: Long,
        val centroidLat: Double,
        val centroidLng: Double,
        val count: Int,
        /**
         * The cheapest (lowest) `colorPosition` among members, used to colour the badge. Members
         * with a possibly incorrect price are not included, because one bad price makes the whole
         * cluster look cheaper than it is. `null` when no member has a price you can trust.
         */
        val minColorPosition: Float?,
        /** Bounding box of the member stations, for zoom-to-cluster on tap. */
        val swLat: Double,
        val swLng: Double,
        val neLat: Double,
        val neLng: Double,
    ) : MapCluster
}

/**
 * Group nearby markers into clusters using a lat/lng grid whose cell size shrinks with zoom. Fast
 * (O(n) with a single pass) and stable — two markers in the same cell always cluster together at
 * the same zoom level.
 *
 * Returns one [MapCluster.Single] per un-grouped station and one [MapCluster.Group] per multi-
 * station cell. Clustering is skipped entirely when `zoom >= [CLUSTER_DISABLED_AT_ZOOM]`.
 */
internal fun clusterMarkers(markers: List<StationMarker>, zoom: Float): List<MapCluster> {
    if (zoom >= CLUSTER_DISABLED_AT_ZOOM || markers.size < 2) {
        return markers.map(MapCluster::Single)
    }
    val cellExponent = zoom.toInt().coerceAtLeast(0) + CLUSTER_CELL_ZOOM_OFFSET
    val cellSize = 360.0 / (1L shl cellExponent)
    val grid = HashMap<Long, MutableList<StationMarker>>()
    for (m in markers) {
        val cx = floor(m.station.longitude / cellSize).toInt()
        val cy = floor(m.station.latitude / cellSize).toInt()
        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
        grid.getOrPut(key) { mutableListOf() }.add(m)
    }
    val out = ArrayList<MapCluster>(grid.size)
    for ((cellKey, group) in grid) {
        if (group.size == 1) {
            out.add(MapCluster.Single(group[0]))
            continue
        }
        var sumLat = 0.0
        var sumLng = 0.0
        var minLat = Double.POSITIVE_INFINITY
        var minLng = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var maxLng = Double.NEGATIVE_INFINITY
        var minColor: Float? = null
        for (m in group) {
            val lat = m.station.latitude
            val lng = m.station.longitude
            sumLat += lat
            sumLng += lng
            if (lat < minLat) minLat = lat
            if (lng < minLng) minLng = lng
            if (lat > maxLat) maxLat = lat
            if (lng > maxLng) maxLng = lng
            val cp = if (m.isPriceInaccurate) null else m.colorPosition
            if (cp != null && (minColor == null || cp < minColor)) {
                minColor = cp
            }
        }
        out.add(
            MapCluster.Group(
                cellKey = cellKey,
                centroidLat = sumLat / group.size,
                centroidLng = sumLng / group.size,
                count = group.size,
                minColorPosition = minColor,
                swLat = minLat,
                swLng = minLng,
                neLat = maxLat,
                neLng = maxLng,
            )
        )
    }
    return out
}

/** Display label for a cluster's station count, capped at "99+". */
internal fun clusterCountLabel(count: Int): String = if (count > 99) "99+" else count.toString()
