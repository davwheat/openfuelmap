package dev.davwheat.openfuelmap.forecourts.data.api.dto

import dev.davwheat.openfuelmap.forecourts.api.model.FuelPrice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FuelPriceDto(
    @SerialName("fuel_type") val fuelType: String,
    val price: Double,
    @SerialName("price_last_updated") val priceLastUpdated: String,
    @SerialName("price_change_effective_timestamp") val priceChangeEffectiveTimestamp: String,
) {
    fun toDomain(): FuelPrice =
        FuelPrice(
            fuelType = fuelType,
            price = price,
            priceLastUpdated = priceLastUpdated,
            priceChangeEffectiveTimestamp = priceChangeEffectiveTimestamp,
        )
}
