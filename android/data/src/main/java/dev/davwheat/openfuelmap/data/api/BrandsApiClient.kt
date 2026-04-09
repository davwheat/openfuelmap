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
