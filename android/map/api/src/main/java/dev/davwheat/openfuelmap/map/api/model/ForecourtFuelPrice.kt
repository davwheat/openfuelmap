package dev.davwheat.openfuelmap.map.api.model

data class ForecourtFuelPrice(
    val price: Double,
    val priceLastUpdated: String,
    val priceChangeEffectiveTimestamp: String,
)
