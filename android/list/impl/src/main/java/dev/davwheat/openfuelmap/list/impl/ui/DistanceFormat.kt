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
package dev.davwheat.openfuelmap.list.impl.ui

import android.content.Context
import dev.davwheat.openfuelmap.data.DistanceUnit
import dev.davwheat.openfuelmap.list.impl.R

/**
 * Format a distance (always stored as miles internally) for display on a list row. Below 10 uses
 * one decimal place so nearby-station differences are readable ("0.3 mi" vs "0.7 mi"); above 10
 * rounds to whole numbers.
 */
internal fun formatDistance(context: Context, distanceMi: Double, unit: DistanceUnit): String {
    val value =
        when (unit) {
            DistanceUnit.MILES -> distanceMi
            DistanceUnit.KILOMETERS -> distanceMi * DistanceUnit.KM_PER_MILE
        }
    return when (unit) {
        DistanceUnit.MILES ->
            if (value < 10.0) context.getString(R.string.distance_decimal_mi).format(value)
            else context.getString(R.string.distance_whole_mi, value.toInt())
        DistanceUnit.KILOMETERS ->
            if (value < 10.0) context.getString(R.string.distance_decimal_km).format(value)
            else context.getString(R.string.distance_whole_km, value.toInt())
    }
}
