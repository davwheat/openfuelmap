package dev.davwheat.openfuelmap.data.repository

import dev.davwheat.openfuelmap.data.api.BrandDto
import dev.davwheat.openfuelmap.data.api.BrandsApiClient
import dev.davwheat.openfuelmap.data.db.BrandDao
import dev.davwheat.openfuelmap.data.db.BrandEntity
import dev.davwheat.openfuelmap.data.utils.NetworkBoundResource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BrandRepository
@Inject
constructor(private val dao: BrandDao, private val apiClient: BrandsApiClient) {

    fun getAllBrands(): Flow<List<BrandEntity>> =
        object : NetworkBoundResource<List<BrandEntity>, List<BrandDto>>() {
                override fun loadFromDb(): Flow<List<BrandEntity>> = dao.getAll()

                override suspend fun shouldFetch(data: List<BrandEntity>?): Boolean =
                    data.isNullOrEmpty()

                override suspend fun fetchFromNetwork(): List<BrandDto> =
                    apiClient.getBrands() ?: emptyList()

                override suspend fun saveCallResult(data: List<BrandDto>) {
                    val entities = data.map {
                        BrandEntity(name = it.name, forecourtCount = it.forecourt_count)
                    }
                    dao.deleteAll()
                    dao.insertAll(entities)
                }
            }
            .asFlow()

    /**
     * Refreshes the brand cache from the network. Intended to be called on every app launch so
     * newly-added brands are picked up over time. If the network call fails (null) or returns an
     * empty list, the existing cache is left untouched, so offline launches fall back to whatever
     * was last seen. IOExceptions are swallowed by [BrandsApiClient]; other failures (e.g.
     * deserialization) propagate and should be handled by the caller.
     */
    suspend fun prefetch() {
        val fresh = apiClient.getBrands() ?: return
        if (fresh.isEmpty()) return
        val entities = fresh.map {
            BrandEntity(name = it.name, forecourtCount = it.forecourt_count)
        }
        dao.deleteAll()
        dao.insertAll(entities)
    }
}
