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

import dev.davwheat.openfuelmap.data.api.FuelTypeDto
import dev.davwheat.openfuelmap.data.api.FuelTypesApiClient
import dev.davwheat.openfuelmap.data.db.FuelTypeDao
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity
import dev.davwheat.openfuelmap.data.utils.DispatcherProvider
import dev.davwheat.openfuelmap.data.utils.NetworkBoundFlowResource
import dev.davwheat.openfuelmap.data.utils.RepositoryWithUpdaterChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FuelTypeRepository
@Inject
constructor(
    private val dao: FuelTypeDao,
    private val apiClient: FuelTypesApiClient,
    private val dispatchers: DispatcherProvider,
    private val cacheMetadata: CacheMetadataRepository,
) : RepositoryWithUpdaterChannel(dispatchers) {

    fun getAllFuelTypes(): Flow<List<FuelTypeEntity>> =
        object :
                NetworkBoundFlowResource<List<FuelTypeEntity>, List<FuelTypeDto>>(
                    updaterChannel = updaterChannel,
                    dispatchers = dispatchers,
                    debugTag = "FuelTypeRepository",
                ) {
                override fun loadFlowFromDb(): Flow<List<FuelTypeEntity>> = dao.getAll()

                override suspend fun shouldFetch(data: List<FuelTypeEntity>?): Boolean =
                    cacheMetadata.isFuelTypesStale()

                override suspend fun fetchFromNetwork(): List<FuelTypeDto> =
                    apiClient.getFuelTypes() ?: emptyList()

                override suspend fun saveCallResult(entries: List<FuelTypeDto>) {
                    val entities = entries.map { FuelTypeEntity(id = it.id, name = it.name) }
                    dao.deleteAll()
                    dao.insertAll(entities)
                    cacheMetadata.markFuelTypesFetched()
                }
            }
            .fetchAsFlow(filterLocal = { it.isNotEmpty() })

    fun getDisplayName(fuelTypeId: String): Flow<String> =
        dao.getById(fuelTypeId).map { it?.name ?: fuelTypeId }

    suspend fun prefetch() {
        val fresh = apiClient.getFuelTypes() ?: return
        if (fresh.isEmpty()) return
        val entities = fresh.map { FuelTypeEntity(id = it.id, name = it.name) }
        dao.deleteAll()
        dao.insertAll(entities)
        cacheMetadata.markFuelTypesFetched()
    }
}
