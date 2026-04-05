package dev.davwheat.openfuelmap.map.data.api.dto

import dev.davwheat.openfuelmap.map.api.model.Forecourt
import dev.davwheat.openfuelmap.map.api.model.ForecourtFuelPrice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecourtListResponse(
    val success: Boolean,
    val result: ForecourtListResultDto? = null,
    val error: String? = null,
)

@Serializable
data class ForecourtListResultDto(
    val forecourts: List<ForecourtDto>,
    val total: Int,
    val page: Int,
    val limit: Int,
)

@Serializable
data class ForecourtDto(
    @SerialName("node_id") val nodeId: String,
    @SerialName("trading_name") val tradingName: String,
    @SerialName("brand_name") val brandName: String,
    val postcode: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("is_motorway_service_station") val isMotorwayServiceStation: Boolean,
    @SerialName("is_supermarket_service_station") val isSupermarketServiceStation: Boolean,
    @SerialName("temporary_closure") val temporaryClosure: Boolean,
    @SerialName("permanent_closure") val permanentClosure: Boolean?,
    @SerialName("fuel_types") val fuelTypes: List<String>,
    val price: ForecourtFuelPriceDto? = null,
) {
    fun toDomain(): Forecourt =
        Forecourt(
            nodeId = nodeId,
            tradingName = tradingName,
            brandName = brandName,
            postcode = postcode,
            city = city,
            latitude = latitude,
            longitude = longitude,
            isMotorwayServiceStation = isMotorwayServiceStation,
            isSupermarketServiceStation = isSupermarketServiceStation,
            temporaryClosure = temporaryClosure,
            permanentClosure = permanentClosure,
            fuelTypes = fuelTypes,
            price = price?.toDomain(),
        )
}

@Serializable
data class ForecourtFuelPriceDto(
    val price: Double,
    @SerialName("price_last_updated") val priceLastUpdated: String,
    @SerialName("price_change_effective_timestamp") val priceChangeEffectiveTimestamp: String,
) {
    fun toDomain(): ForecourtFuelPrice =
        ForecourtFuelPrice(
            price = price,
            priceLastUpdated = priceLastUpdated,
            priceChangeEffectiveTimestamp = priceChangeEffectiveTimestamp,
        )
}
