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
