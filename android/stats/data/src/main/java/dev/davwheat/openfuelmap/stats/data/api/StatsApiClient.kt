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
package dev.davwheat.openfuelmap.stats.data.api

import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.stats.api.model.DailyMedianPrice
import dev.davwheat.openfuelmap.stats.api.model.PriceStat
import dev.davwheat.openfuelmap.stats.api.model.TimeRange
import dev.davwheat.openfuelmap.stats.data.api.dto.DailyMedianPricesResponse
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

class StatsApiClient
@Inject
constructor(
    private val client: OkHttpClient,
    private val json: Json,
    @param:Named("baseUrl") private val baseUrl: String,
) {
    suspend fun getDailyPrices(
        timeRange: TimeRange,
        stat: PriceStat,
    ): ApiResult<List<DailyMedianPrice>> =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "$baseUrl/api/stats/daily-median-prices"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("range", timeRange.queryParam)
                        .addQueryParameter("stat", stat.queryParam)
                        .build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val parsed = json.decodeFromString<DailyMedianPricesResponse>(body)
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
