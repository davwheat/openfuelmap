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
