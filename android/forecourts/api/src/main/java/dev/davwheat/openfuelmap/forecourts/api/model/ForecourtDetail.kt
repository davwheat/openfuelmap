package dev.davwheat.openfuelmap.forecourts.api.model

data class ForecourtDetail(
    val nodeId: String,
    val tradingName: String,
    val brandName: String,
    val isSameTradingAndBrandName: Boolean,
    val publicPhoneNumber: String?,
    val temporaryClosure: Boolean,
    val permanentClosure: Boolean?,
    val permanentClosureDate: String?,
    val isMotorwayServiceStation: Boolean,
    val isSupermarketServiceStation: Boolean,
    val location: Location,
    val amenities: List<String>,
    val openingTimes: OpeningTimes?,
    val fuelTypes: List<String>,
    val updatedAt: String,
    val currentPrices: List<FuelPrice>,
)
