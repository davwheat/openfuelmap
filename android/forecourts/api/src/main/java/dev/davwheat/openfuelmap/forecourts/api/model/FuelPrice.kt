package dev.davwheat.openfuelmap.forecourts.api.model

data class FuelPrice(
    val fuelType: String,
    val price: Double,
    val priceLastUpdated: String,
    val priceChangeEffectiveTimestamp: String,
    val possiblyInaccurate: PriceInaccuracyReason? = null,
)
