package dev.davwheat.openfuelmap.forecourts.api.model

data class Forecourt(
    val nodeId: String,
    val tradingName: String,
    val brandName: String,
    val postcode: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val isMotorwayServiceStation: Boolean,
    val isSupermarketServiceStation: Boolean,
    val temporaryClosure: Boolean,
    val permanentClosure: Boolean?,
    val fuelTypes: List<String>,
    val price: ForecourtFuelPrice? = null,
)
