package dev.davwheat.openfuelmap.map.data.api

import dev.davwheat.openfuelmap.map.api.model.BoundingBox
import dev.davwheat.openfuelmap.map.api.model.Forecourt
import dev.davwheat.openfuelmap.map.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.map.api.result.ApiResult
import dev.davwheat.openfuelmap.map.data.api.dto.ForecourtDetailResponse
import dev.davwheat.openfuelmap.map.data.api.dto.ForecourtListResponse
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
        limit: Int = 200,
    ): ApiResult<List<Forecourt>> =
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
                        .apply { if (fuelType != null) addQueryParameter("fuel_type", fuelType) }
                        .build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()

                val parsed = json.decodeFromString<ForecourtListResponse>(body)
                if (parsed.success && parsed.result != null) {
                    ApiResult.Success(parsed.result.forecourts.map { it.toDomain() })
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
}
