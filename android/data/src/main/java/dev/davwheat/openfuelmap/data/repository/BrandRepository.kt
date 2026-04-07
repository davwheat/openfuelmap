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
