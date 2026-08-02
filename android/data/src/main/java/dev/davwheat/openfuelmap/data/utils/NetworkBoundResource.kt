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
package dev.davwheat.openfuelmap.data.utils

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * A generic class that can provide a resource backed by both the database and the network.
 *
 * @param ResultType Type for the Resource data (from DB)
 * @param RequestType Type for the API response
 */
abstract class NetworkBoundResource<ResultType, RequestType> {

    protected abstract suspend fun loadFromDb(): ResultType

    protected abstract suspend fun fetchFromNetwork(): RequestType

    protected abstract suspend fun saveCallResult(entries: RequestType)

    protected abstract suspend fun shouldFetch(data: ResultType?): Boolean

    /**
     * Returns a [Flow] that emits local data first, then optionally refreshes from the network.
     * Subclasses may override this to provide a more sophisticated flow (e.g. continuous DB
     * subscription).
     *
     * @param filterLocal predicate deciding whether the initial cached value should be emitted.
     *   When it returns `false` the first emission is suppressed and only the network-refreshed
     *   result is sent downstream.
     */
    open fun fetchAsFlow(filterLocal: (ResultType) -> Boolean = { true }): Flow<ResultType> = flow {
        val dbData = loadFromDb()
        if (filterLocal(dbData)) emit(dbData)

        if (shouldFetch(dbData)) {
            try {
                saveCallResult(fetchFromNetwork())
                emit(loadFromDb())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Timber.w("Network error, using cached data: %s", e.message)
            } catch (e: Exception) {
                Timber.e(e, "Fetch failed, using cached data")
            }
        }
    }
}
