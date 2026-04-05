package dev.davwheat.openfuelmap.data.utils

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * A generic class that can provide a resource backed by both the database and the network.
 *
 * Emits local data first, then fetches from network if [shouldFetch] returns true, saves to DB, and
 * the Room Flow automatically re-emits the updated data.
 *
 * @param ResultType Type for the Resource data (from DB)
 * @param RequestType Type for the API response
 */
abstract class NetworkBoundResource<ResultType, RequestType> {

    fun asFlow(): Flow<ResultType> = flow {
        val dbData = loadFromDb().first()
        emit(dbData)

        if (shouldFetch(dbData)) {
            try {
                val networkResult = fetchFromNetwork()
                saveCallResult(networkResult)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Timber.w("Network error, using cached data: %s", e.message)
            } catch (e: Exception) {
                Timber.e(e, "Fetch failed, using cached data")
            }
        }

        emitAll(loadFromDb())
    }

    protected abstract fun loadFromDb(): Flow<ResultType>

    protected abstract suspend fun shouldFetch(data: ResultType?): Boolean

    protected abstract suspend fun fetchFromNetwork(): RequestType

    protected abstract suspend fun saveCallResult(data: RequestType)
}
