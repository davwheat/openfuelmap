package dev.davwheat.openfuelmap.data.api

import dev.davwheat.openfuelmap.data.ApiConstants
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class FuelTypesApiClient
@Inject
constructor(private val client: OkHttpClient, private val json: Json) {
    suspend fun getFuelTypes(): List<FuelTypeDto>? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder().url("${ApiConstants.BASE_URL}/api/fuel-types").build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val parsed = json.decodeFromString<FuelTypesResponse>(body)
                if (parsed.success) parsed.result?.fuel_types else null
            } catch (e: IOException) {
                null
            }
        }
}
