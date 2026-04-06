package dev.davwheat.openfuelmap.forecourts.data.api

import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.forecourts.api.model.BoundingBox
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtListResult
import dev.davwheat.openfuelmap.forecourts.api.model.PriceHistoryEntry
import dev.davwheat.openfuelmap.forecourts.data.api.dto.ForecourtDetailResponse
import dev.davwheat.openfuelmap.forecourts.data.api.dto.ForecourtListResponse
import dev.davwheat.openfuelmap.forecourts.data.api.dto.PriceHistoryResponse
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class FuelPricesApiClient
@Inject
constructor(
    private val client: OkHttpClient,
    private val json: Json,
    @param:Named("baseUrl") private val baseUrl: String,
) {
    suspend fun getForecourts(
        bounds: BoundingBox,
        fuelType: String? = null,
        excludeBrands: Set<String> = emptySet(),
        limit: Int = 200,
    ): ApiResult<ForecourtListResult> =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "$baseUrl/api/forecourts"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("sw_lat", bounds.swLat.toString())
                        .addQueryParameter("sw_lng", bounds.swLng.toString())
                        .addQueryParameter("ne_lat", bounds.neLat.toString())
                        .addQueryParameter("ne_lng", bounds.neLng.toString())
                        .addQueryParameter("limit", limit.toString())
                        .apply {
                            if (fuelType != null) addQueryParameter("fuel_type", fuelType)
                            if (excludeBrands.isNotEmpty()) {
                                addQueryParameter("exclude_brand", excludeBrands.joinToString(","))
                            }
                        }
                        .build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()

                val parsed = json.decodeFromString<ForecourtListResponse>(body)
                if (parsed.success && parsed.result != null) {
                    ApiResult.Success(parsed.result.toDomain())
                } else {
                    ApiResult.ApiError(parsed.error ?: "Unknown API error")
                }
            } catch (e: IOException) {
                ApiResult.NetworkError("Network error: ${e.message}", e)
            } catch (e: SerializationException) {
                ApiResult.NetworkError("Failed to parse response: ${e.message}", e)
            }
        }

    suspend fun getForecourtDetail(nodeId: String): ApiResult<ForecourtDetail> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/api/forecourts/$nodeId".toHttpUrl()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()

                if (response.code == 404) {
                    val parsed = json.decodeFromString<ForecourtDetailResponse>(body)
                    return@withContext ApiResult.ApiError(parsed.error ?: "Forecourt not found")
                }

                val parsed = json.decodeFromString<ForecourtDetailResponse>(body)
                if (parsed.success && parsed.result != null) {
                    ApiResult.Success(parsed.result.toDomain())
                } else {
                    ApiResult.ApiError(parsed.error ?: "Unknown API error")
                }
            } catch (e: IOException) {
                ApiResult.NetworkError("Network error: ${e.message}", e)
            } catch (e: SerializationException) {
                ApiResult.NetworkError("Failed to parse response: ${e.message}", e)
            }
        }

    suspend fun getPriceHistory(
        nodeId: String,
        fuelType: String,
        since: String? = null,
        limit: Int = 500,
    ): ApiResult<List<PriceHistoryEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "$baseUrl/api/forecourts/$nodeId/prices/history"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("fuel_type", fuelType)
                        .addQueryParameter("limit", limit.toString())
                        .apply { if (since != null) addQueryParameter("since", since) }
                        .build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()

                if (response.code == 404) {
                    val parsed = json.decodeFromString<PriceHistoryResponse>(body)
                    return@withContext ApiResult.ApiError(parsed.error ?: "Forecourt not found")
                }

                val parsed = json.decodeFromString<PriceHistoryResponse>(body)
                if (parsed.success && parsed.result != null) {
                    ApiResult.Success(parsed.result.prices.map { it.toDomain() })
                } else {
                    ApiResult.ApiError(parsed.error ?: "Unknown API error")
                }
            } catch (e: IOException) {
                ApiResult.NetworkError("Network error: ${e.message}", e)
            } catch (e: SerializationException) {
                ApiResult.NetworkError("Failed to parse response: ${e.message}", e)
            }
        }
}
