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
package dev.davwheat.openfuelmap.data.repository

import dev.davwheat.openfuelmap.data.api.BrandDto
import dev.davwheat.openfuelmap.data.api.BrandsApiClient
import dev.davwheat.openfuelmap.data.db.BrandDao
import dev.davwheat.openfuelmap.data.db.BrandEntity
import dev.davwheat.openfuelmap.data.utils.DispatcherProvider
import dev.davwheat.openfuelmap.data.utils.NetworkBoundFlowResource
import dev.davwheat.openfuelmap.data.utils.RepositoryWithUpdaterChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BrandRepository
@Inject
constructor(
    private val dao: BrandDao,
    private val apiClient: BrandsApiClient,
    private val dispatchers: DispatcherProvider,
    private val cacheMetadata: CacheMetadataRepository,
) : RepositoryWithUpdaterChannel(dispatchers) {

    fun getAllBrands(): Flow<List<BrandEntity>> =
        object :
                NetworkBoundFlowResource<List<BrandEntity>, List<BrandDto>>(
                    updaterChannel = updaterChannel,
                    dispatchers = dispatchers,
                    debugTag = "BrandRepository",
                ) {
                override fun loadFlowFromDb(): Flow<List<BrandEntity>> = dao.getAll()

                override suspend fun shouldFetch(data: List<BrandEntity>?): Boolean =
                    cacheMetadata.isBrandsStale()

                override suspend fun fetchFromNetwork(): List<BrandDto> =
                    apiClient.getBrands() ?: emptyList()

                override suspend fun saveCallResult(entries: List<BrandDto>) {
                    val entities = entries.map {
                        BrandEntity(name = it.name, forecourtCount = it.forecourt_count)
                    }
                    dao.deleteAll()
                    dao.insertAll(entities)
                    cacheMetadata.markBrandsFetched()
                }
            }
            .fetchAsFlow(filterLocal = { it.isNotEmpty() })

    suspend fun prefetch() {
        val fresh = apiClient.getBrands() ?: return
        if (fresh.isEmpty()) return
        val entities = fresh.map {
            BrandEntity(name = it.name, forecourtCount = it.forecourt_count)
        }
        dao.deleteAll()
        dao.insertAll(entities)
        cacheMetadata.markBrandsFetched()
    }
}
