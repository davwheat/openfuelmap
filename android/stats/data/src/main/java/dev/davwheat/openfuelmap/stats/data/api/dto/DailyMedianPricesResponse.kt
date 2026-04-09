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
