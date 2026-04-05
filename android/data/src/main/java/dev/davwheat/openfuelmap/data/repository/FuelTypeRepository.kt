package dev.davwheat.openfuelmap.data.repository

import dev.davwheat.openfuelmap.data.api.FuelTypeDto
import dev.davwheat.openfuelmap.data.api.FuelTypesApiClient
import dev.davwheat.openfuelmap.data.db.FuelTypeDao
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity
import dev.davwheat.openfuelmap.data.utils.NetworkBoundResource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FuelTypeRepository
@Inject
constructor(private val dao: FuelTypeDao, private val apiClient: FuelTypesApiClient) {

    fun getAllFuelTypes(): Flow<List<FuelTypeEntity>> =
        object : NetworkBoundResource<List<FuelTypeEntity>, List<FuelTypeDto>>() {
                override fun loadFromDb(): Flow<List<FuelTypeEntity>> = dao.getAll()

                override suspend fun shouldFetch(data: List<FuelTypeEntity>?): Boolean =
                    data.isNullOrEmpty()

                override suspend fun fetchFromNetwork(): List<FuelTypeDto> =
                    apiClient.getFuelTypes() ?: emptyList()

                override suspend fun saveCallResult(data: List<FuelTypeDto>) {
                    val entities = data.map { FuelTypeEntity(id = it.id, name = it.name) }
                    dao.deleteAll()
                    dao.insertAll(entities)
                }
            }
            .asFlow()

    fun getDisplayName(fuelTypeId: String): Flow<String> =
        dao.getById(fuelTypeId).map { it?.name ?: fuelTypeId }
}
