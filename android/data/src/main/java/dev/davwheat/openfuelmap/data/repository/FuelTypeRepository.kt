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
) : RepositoryWithUpdaterChannel(dispatchers) {

    fun getAllFuelTypes(): Flow<List<FuelTypeEntity>> =
        object :
                NetworkBoundFlowResource<List<FuelTypeEntity>, List<FuelTypeDto>>(
                    updaterChannel = updaterChannel,
                    dispatchers = dispatchers,
                    debugTag = "FuelTypeRepository",
                ) {
                override fun loadFlowFromDb(): Flow<List<FuelTypeEntity>> = dao.getAll()

                override suspend fun shouldFetch(data: List<FuelTypeEntity>?): Boolean = true

                override suspend fun fetchFromNetwork(): List<FuelTypeDto> =
                    apiClient.getFuelTypes() ?: emptyList()

                override suspend fun saveCallResult(entries: List<FuelTypeDto>) {
                    val entities = entries.map { FuelTypeEntity(id = it.id, name = it.name) }
                    dao.deleteAll()
                    dao.insertAll(entities)
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
    }
}
