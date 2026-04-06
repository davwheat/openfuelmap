package dev.davwheat.openfuelmap.forecourts.data.api.dto

import dev.davwheat.openfuelmap.forecourts.api.model.FuelPrice
import dev.davwheat.openfuelmap.forecourts.api.model.PriceInaccuracyReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FuelPriceDto(
    @SerialName("fuel_type") val fuelType: String,
    val price: Double,
    @SerialName("price_last_updated") val priceLastUpdated: String,
    @SerialName("price_change_effective_timestamp") val priceChangeEffectiveTimestamp: String,
    @SerialName("possibly_inaccurate") val possiblyInaccurate: String? = null,
) {
    fun toDomain(): FuelPrice =
        FuelPrice(
            fuelType = fuelType,
            price = price,
            priceLastUpdated = priceLastUpdated,
            priceChangeEffectiveTimestamp = priceChangeEffectiveTimestamp,
            possiblyInaccurate = possiblyInaccurate.toInaccuracyReason(),
        )
}

internal fun String?.toInaccuracyReason(): PriceInaccuracyReason? =
    when (this) {
        null -> null
        "price_too_low" -> PriceInaccuracyReason.PRICE_TOO_LOW
        "stale_price" -> PriceInaccuracyReason.STALE_PRICE
        else -> PriceInaccuracyReason.UNKNOWN
    }
