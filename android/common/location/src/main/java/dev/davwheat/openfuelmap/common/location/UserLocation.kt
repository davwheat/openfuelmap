package dev.davwheat.openfuelmap.common.location

/** Minimal lat/lng pair, decoupled from the Google Maps SDK's `LatLng`. */
data class UserLocation(val latitude: Double, val longitude: Double)
