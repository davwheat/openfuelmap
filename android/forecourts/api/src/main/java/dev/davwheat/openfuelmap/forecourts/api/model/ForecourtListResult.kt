package dev.davwheat.openfuelmap.forecourts.api.model

data class ForecourtListResult(
    val forecourts: List<Forecourt>,
    val pricePercentiles: PricePercentiles?,
)

data class PricePercentiles(val low: Double, val high: Double)
