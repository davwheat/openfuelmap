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

import dev.davwheat.openfuelmap.forecourts.api.model.PriceHistoryEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PriceHistoryResponse(
    val success: Boolean,
    val result: PriceHistoryResultDto? = null,
    val error: String? = null,
)

@Serializable
data class PriceHistoryResultDto(
    @SerialName("node_id") val nodeId: String,
    val prices: List<PriceHistoryEntryDto>,
)

@Serializable
data class PriceHistoryEntryDto(
    val price: Double,
    @SerialName("fuel_type") val fuelType: String,
    @SerialName("price_change_effective_timestamp") val priceChangeEffectiveTimestamp: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("possibly_inaccurate") val possiblyInaccurate: String? = null,
) {
    fun toDomain(): PriceHistoryEntry =
        PriceHistoryEntry(
            price = price,
            fuelType = fuelType,
            priceChangeEffectiveTimestamp = priceChangeEffectiveTimestamp,
            createdAt = createdAt,
            possiblyInaccurate = possiblyInaccurate.toInaccuracyReason(),
        )
}
