package dev.davwheat.openfuelmap.data.api

import dev.davwheat.openfuelmap.data.ApiConstants
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class BrandsApiClient
@Inject
constructor(private val client: OkHttpClient, private val json: Json) {
    suspend fun getBrands(): List<BrandDto>? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("${ApiConstants.BASE_URL}/api/brands").build()
                val response = client.newCall(request).execute()
                val body = response.body.string()
                val parsed = json.decodeFromString<BrandsResponse>(body)
                if (parsed.success) parsed.result?.brands else null
            } catch (e: IOException) {
                null
            }
        }
}
