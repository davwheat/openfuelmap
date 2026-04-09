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
package dev.davwheat.openfuelmap.map.impl.viewmodel

import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt

/**
 * Pre-computed display data for a single station marker on the map.
 *
 * All fields are derived on a background dispatcher in [MapViewModel] so the UI can render markers
 * without doing any per-station processing on the main thread.
 */
data class StationMarker(
    val station: Forecourt,
    val label: String,
    /**
     * Normalised position of this station's price within the 10th–90th percentile range returned by
     * the API: `0f` for cheapest, `1f` for most expensive, `null` when the station has no price.
     */
    val colorPosition: Float?,
    val zIndex: Float,
)
