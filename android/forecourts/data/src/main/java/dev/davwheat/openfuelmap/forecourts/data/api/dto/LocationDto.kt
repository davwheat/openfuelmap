package dev.davwheat.openfuelmap.forecourts.data.api.dto

import dev.davwheat.openfuelmap.forecourts.api.model.Location
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationDto(
    @SerialName("address_line_1") val addressLine1: String?,
    @SerialName("address_line_2") val addressLine2: String?,
    val city: String?,
    val country: String?,
    val county: String?,
    val postcode: String?,
    val latitude: Double,
    val longitude: Double,
) {
    fun toDomain(): Location =
        Location(
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            country = country,
            county = county,
            postcode = postcode,
            latitude = latitude,
            longitude = longitude,
        )
}
