package dev.davwheat.openfuelmap.forecourts.api.model

data class PriceHistoryEntry(
    val price: Double,
    val fuelType: String,
    val priceChangeEffectiveTimestamp: String,
    val createdAt: String,
    val possiblyInaccurate: PriceInaccuracyReason? = null,
)
