package dev.davwheat.openfuelmap.forecourts.data.api.dto

import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtDetail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecourtDetailResponse(
    val success: Boolean,
    val result: ForecourtDetailResultDto? = null,
    val error: String? = null,
)

@Serializable
data class ForecourtDetailResultDto(
    val forecourt: ForecourtDetailItemDto,
    @SerialName("current_prices") val currentPrices: List<FuelPriceDto>,
) {
    fun toDomain(): ForecourtDetail =
        ForecourtDetail(
            nodeId = forecourt.nodeId,
            tradingName = forecourt.tradingName,
            brandName = forecourt.brandName,
            isSameTradingAndBrandName = forecourt.isSameTradingAndBrandName,
            publicPhoneNumber = forecourt.publicPhoneNumber,
            temporaryClosure = forecourt.temporaryClosure,
            permanentClosure = forecourt.permanentClosure,
            permanentClosureDate = forecourt.permanentClosureDate,
            isMotorwayServiceStation = forecourt.isMotorwayServiceStation,
            isSupermarketServiceStation = forecourt.isSupermarketServiceStation,
            location = forecourt.location.toDomain(),
            amenities = forecourt.amenities,
            openingTimes = forecourt.openingTimes?.toDomain(),
            fuelTypes = forecourt.fuelTypes,
            updatedAt = forecourt.updatedAt,
            currentPrices = currentPrices.map { it.toDomain() },
        )
}

@Serializable
data class ForecourtDetailItemDto(
    @SerialName("node_id") val nodeId: String,
    @SerialName("trading_name") val tradingName: String,
    @SerialName("brand_name") val brandName: String,
    @SerialName("is_same_trading_and_brand_name") val isSameTradingAndBrandName: Boolean,
    @SerialName("public_phone_number") val publicPhoneNumber: String?,
    @SerialName("temporary_closure") val temporaryClosure: Boolean,
    @SerialName("permanent_closure") val permanentClosure: Boolean?,
    @SerialName("permanent_closure_date") val permanentClosureDate: String?,
    @SerialName("is_motorway_service_station") val isMotorwayServiceStation: Boolean,
    @SerialName("is_supermarket_service_station") val isSupermarketServiceStation: Boolean,
    val location: LocationDto,
    val amenities: List<String>,
    @SerialName("opening_times") val openingTimes: OpeningTimesDto? = null,
    @SerialName("fuel_types") val fuelTypes: List<String>,
    @SerialName("updated_at") val updatedAt: String,
)
