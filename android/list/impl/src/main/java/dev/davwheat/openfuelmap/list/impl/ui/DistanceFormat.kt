package dev.davwheat.openfuelmap.list.impl.ui

/**
 * Format a distance in miles for display on a list row. Below 10 mi uses one decimal place so
 * nearby-station differences are readable ("0.3 mi" vs "0.7 mi"); above 10 mi rounds to whole
 * numbers.
 */
internal fun formatDistanceMi(distanceMi: Double): String =
    if (distanceMi < 10.0) "%.1f mi".format(distanceMi) else "${distanceMi.toInt()} mi"
