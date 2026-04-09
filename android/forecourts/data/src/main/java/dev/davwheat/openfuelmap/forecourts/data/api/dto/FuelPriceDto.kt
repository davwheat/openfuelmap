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

import dev.davwheat.openfuelmap.forecourts.api.model.FuelPrice
import dev.davwheat.openfuelmap.forecourts.api.model.PriceInaccuracyReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FuelPriceDto(
    @SerialName("fuel_type") val fuelType: String,
    val price: Double,
    @SerialName("price_last_updated") val priceLastUpdated: String,
    @SerialName("price_change_effective_timestamp") val priceChangeEffectiveTimestamp: String,
    @SerialName("possibly_inaccurate") val possiblyInaccurate: String? = null,
) {
    fun toDomain(): FuelPrice =
        FuelPrice(
            fuelType = fuelType,
            price = price,
            priceLastUpdated = priceLastUpdated,
            priceChangeEffectiveTimestamp = priceChangeEffectiveTimestamp,
            possiblyInaccurate = possiblyInaccurate.toInaccuracyReason(),
        )
}

internal fun String?.toInaccuracyReason(): PriceInaccuracyReason? =
    when (this) {
        null -> null
        "price_too_low" -> PriceInaccuracyReason.PRICE_TOO_LOW
        "stale_price" -> PriceInaccuracyReason.STALE_PRICE
        else -> PriceInaccuracyReason.UNKNOWN
    }
