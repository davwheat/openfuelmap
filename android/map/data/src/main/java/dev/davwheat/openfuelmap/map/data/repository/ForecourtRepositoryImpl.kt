package dev.davwheat.openfuelmap.map.data.repository

import dev.davwheat.openfuelmap.map.api.model.BoundingBox
import dev.davwheat.openfuelmap.map.api.model.Forecourt
import dev.davwheat.openfuelmap.map.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.map.api.repository.ForecourtRepository
import dev.davwheat.openfuelmap.map.api.result.ApiResult
import dev.davwheat.openfuelmap.map.data.api.FuelPricesApiClient
import javax.inject.Inject

class ForecourtRepositoryImpl @Inject constructor(private val apiClient: FuelPricesApiClient) :
    ForecourtRepository {

    override suspend fun getForecourts(
        bounds: BoundingBox,
        fuelType: String?,
        limit: Int,
    ): ApiResult<List<Forecourt>> = apiClient.getForecourts(bounds, fuelType, limit)

    override suspend fun getForecourtDetail(nodeId: String): ApiResult<ForecourtDetail> =
        apiClient.getForecourtDetail(nodeId)
}
