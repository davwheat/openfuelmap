package dev.davwheat.openfuelmap.forecourts.data.repository

import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.forecourts.api.geo.boundingBoxForRadius
import dev.davwheat.openfuelmap.forecourts.api.geo.haversineMiles
import dev.davwheat.openfuelmap.forecourts.api.model.BoundingBox
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtListResult
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtWithDistance
import dev.davwheat.openfuelmap.forecourts.api.model.PriceHistoryEntry
import dev.davwheat.openfuelmap.forecourts.api.repository.ForecourtRepository
import dev.davwheat.openfuelmap.forecourts.data.api.FuelPricesApiClient
import javax.inject.Inject

class ForecourtRepositoryImpl @Inject constructor(private val apiClient: FuelPricesApiClient) :
    ForecourtRepository {

    override suspend fun getForecourts(
        bounds: BoundingBox,
        fuelType: String?,
        excludeBrands: Set<String>,
        limit: Int,
    ): ApiResult<ForecourtListResult> =
        apiClient.getForecourts(bounds, fuelType, excludeBrands, limit)

    override suspend fun getForecourtsNear(
        centerLat: Double,
        centerLng: Double,
        radiusMiles: Double,
        fuelType: String?,
        excludeBrands: Set<String>,
        limit: Int,
    ): ApiResult<List<ForecourtWithDistance>> {
        val bbox = boundingBoxForRadius(centerLat, centerLng, radiusMiles)
        return when (val result = apiClient.getForecourts(bbox, fuelType, excludeBrands, limit)) {
            is ApiResult.Success -> {
                val enriched =
                    result.data.forecourts
                        .asSequence()
                        .map { forecourt ->
                            ForecourtWithDistance(
                                forecourt = forecourt,
                                distanceMiles =
                                    haversineMiles(
                                        centerLat,
                                        centerLng,
                                        forecourt.latitude,
                                        forecourt.longitude,
                                    ),
                            )
                        }
                        .filter { it.distanceMiles <= radiusMiles }
                        .sortedBy { it.distanceMiles }
                        .toList()
                ApiResult.Success(enriched)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun getForecourtDetail(nodeId: String): ApiResult<ForecourtDetail> =
        apiClient.getForecourtDetail(nodeId)

    override suspend fun getPriceHistory(
        nodeId: String,
        fuelType: String,
        since: String?,
        limit: Int,
    ): ApiResult<List<PriceHistoryEntry>> =
        apiClient.getPriceHistory(nodeId, fuelType, since, limit)
}
