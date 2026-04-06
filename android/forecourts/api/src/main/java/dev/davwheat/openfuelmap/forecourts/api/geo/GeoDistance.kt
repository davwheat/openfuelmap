package dev.davwheat.openfuelmap.forecourts.api.geo

import dev.davwheat.openfuelmap.forecourts.api.model.BoundingBox
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean radius of Earth, in miles. */
private const val EARTH_RADIUS_MILES: Double = 3958.7613

/** Miles per degree of latitude (constant everywhere on Earth). */
private const val MILES_PER_DEGREE_LAT: Double = 69.054

/**
 * Great-circle distance in miles between two lat/lng points via the haversine formula. Assumes a
 * spherical Earth — accurate to ~0.5 % for distances up to a few hundred miles, which is well
 * within the needs of a consumer fuel map.
 */
fun haversineMiles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a =
        sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
    val c = 2 * asin(min(1.0, sqrt(a)))
    return EARTH_RADIUS_MILES * c
}

/**
 * Return the smallest axis-aligned bounding box that fully contains the circle of [radiusMiles]
 * around ([centerLat], [centerLng]). The east/west span widens towards the poles because a degree
 * of longitude shrinks with latitude. Latitudes are clamped to valid ranges.
 */
fun boundingBoxForRadius(centerLat: Double, centerLng: Double, radiusMiles: Double): BoundingBox {
    val latDelta = radiusMiles / MILES_PER_DEGREE_LAT
    // Longitude degrees shrink by cos(lat) away from the equator. Guard against cos→0 near the
    // poles (UK never gets close, but we want a defensive floor).
    val cosLat = max(0.01, cos(centerLat * PI / 180.0))
    val lngDelta = radiusMiles / (MILES_PER_DEGREE_LAT * cosLat)
    return BoundingBox(
        swLat = (centerLat - latDelta).coerceIn(-90.0, 90.0),
        swLng = centerLng - lngDelta,
        neLat = (centerLat + latDelta).coerceIn(-90.0, 90.0),
        neLng = centerLng + lngDelta,
    )
}
