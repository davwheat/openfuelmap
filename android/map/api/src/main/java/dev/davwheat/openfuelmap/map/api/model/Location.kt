package dev.davwheat.openfuelmap.map.api.model

data class Location(
    val addressLine1: String?,
    val addressLine2: String?,
    val city: String?,
    val country: String?,
    val county: String?,
    val postcode: String?,
    val latitude: Double,
    val longitude: Double,
)
