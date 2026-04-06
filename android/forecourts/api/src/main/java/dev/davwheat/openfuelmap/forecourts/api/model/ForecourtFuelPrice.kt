package dev.davwheat.openfuelmap.forecourts.api.model

enum class PriceChange {
    INCREASE,
    DECREASE,
}

data class ForecourtFuelPrice(
    val price: Double,
    val priceLastUpdated: String,
    val priceChangeEffectiveTimestamp: String,
    val priceChange: PriceChange? = null,
)
