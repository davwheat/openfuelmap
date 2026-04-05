package dev.davwheat.openfuelmap.map.api.model

data class FuelPrice(
    val fuelType: String,
    val price: Double,
    val priceLastUpdated: String,
    val priceChangeEffectiveTimestamp: String,
)
