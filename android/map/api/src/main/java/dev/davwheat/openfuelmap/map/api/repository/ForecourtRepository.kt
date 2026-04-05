package dev.davwheat.openfuelmap.map.api.repository

import dev.davwheat.openfuelmap.map.api.model.BoundingBox
import dev.davwheat.openfuelmap.map.api.model.Forecourt
import dev.davwheat.openfuelmap.map.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.map.api.result.ApiResult

interface ForecourtRepository {
    suspend fun getForecourts(
        bounds: BoundingBox,
        fuelType: String? = null,
        brand: String? = null,
        limit: Int = 1_000,
    ): ApiResult<List<Forecourt>>

    suspend fun getForecourtDetail(nodeId: String): ApiResult<ForecourtDetail>
}
