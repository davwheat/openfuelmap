/**
 * Open Fuel Map
 * Copyright (C) 2026  David Wheatley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.davwheat.openfuelmap.forecourts.data.api.dto

import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtFuelPrice
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtListResult
import dev.davwheat.openfuelmap.forecourts.api.model.PriceChange
import dev.davwheat.openfuelmap.forecourts.api.model.PricePercentiles
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
    @SerialName("price_percentiles") val pricePercentiles: PricePercentilesDto? = null,
    val total: Int,
    val page: Int,
    val limit: Int,
) {
    fun toDomain(): ForecourtListResult =
        ForecourtListResult(
            forecourts = forecourts.map { it.toDomain() },
            pricePercentiles = pricePercentiles?.toDomain(),
        )
}

@Serializable
data class PricePercentilesDto(val low: Double, val high: Double) {
    fun toDomain(): PricePercentiles = PricePercentiles(low = low, high = high)
}

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
    @SerialName("price_change") val priceChange: String? = null,
    @SerialName("possibly_inaccurate") val possiblyInaccurate: String? = null,
) {
    fun toDomain(): ForecourtFuelPrice =
        ForecourtFuelPrice(
            price = price,
            priceLastUpdated = priceLastUpdated,
            priceChangeEffectiveTimestamp = priceChangeEffectiveTimestamp,
            priceChange =
                when (priceChange) {
                    "increase" -> PriceChange.INCREASE
                    "decrease" -> PriceChange.DECREASE
                    else -> null
                },
            possiblyInaccurate = possiblyInaccurate.toInaccuracyReason(),
        )
}
