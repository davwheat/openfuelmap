package dev.davwheat.openfuelmap.forecourts.api.repository

import dev.davwheat.openfuelmap.forecourts.api.model.BoundingBox
import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtWithDistance
import dev.davwheat.openfuelmap.forecourts.api.result.ApiResult

interface ForecourtRepository {
    suspend fun getForecourts(
        bounds: BoundingBox,
        fuelType: String? = null,
        excludeBrands: Set<String> = emptySet(),
        limit: Int = 1_000,
    ): ApiResult<List<Forecourt>>

    /**
     * Fetch forecourts within [radiusMiles] of [centerLat], [centerLng], sorted nearest-first.
     *
     * Implementations convert the circle to an enclosing bounding box, call the existing bbox
     * endpoint, and filter results by straight-line (haversine) distance so the returned set is a
     * true circle rather than its enclosing square.
     */
    suspend fun getForecourtsNear(
        centerLat: Double,
        centerLng: Double,
        radiusMiles: Double,
        fuelType: String? = null,
        excludeBrands: Set<String> = emptySet(),
        limit: Int = 1_000,
    ): ApiResult<List<ForecourtWithDistance>>

    suspend fun getForecourtDetail(nodeId: String): ApiResult<ForecourtDetail>
}
