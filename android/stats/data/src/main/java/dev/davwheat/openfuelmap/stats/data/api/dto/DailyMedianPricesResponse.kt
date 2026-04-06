package dev.davwheat.openfuelmap.stats.data.api.dto

import dev.davwheat.openfuelmap.stats.api.model.DailyMedianPrice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyMedianPricesResponse(
    val success: Boolean,
    val result: DailyMedianPricesResultDto? = null,
    val error: String? = null,
)

@Serializable
data class DailyMedianPricesResultDto(val stat: String, val prices: List<DailyMedianPriceDto>)

@Serializable
data class DailyMedianPriceDto(
    val date: String,
    @SerialName("fuel_type") val fuelType: String,
    val price: Double,
) {
    fun toDomain(): DailyMedianPrice =
        DailyMedianPrice(date = date, fuelType = fuelType, price = price)
}
