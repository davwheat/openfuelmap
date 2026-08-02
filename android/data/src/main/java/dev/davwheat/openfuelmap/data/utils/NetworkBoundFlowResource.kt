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

import dev.davwheat.openfuelmap.data.result.isNetworkError
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * A [NetworkBoundResource] that subscribes to the database [Flow] continuously, so Room updates are
 * picked up automatically. Background network refreshes are dispatched through a shared
 * [updaterChannel] so they never block the downstream collector.
 *
 * @param updaterChannel A channel used to process background updates. Typically provided by
 *   [RepositoryWithUpdaterChannel].
 * @param dispatchers A set of dispatchers to use for this resource.
 * @param debugTag Tag used for debug-level Timber logs.
 */
internal abstract class NetworkBoundFlowResource<ResultType, RequestType>(
    private val updaterChannel: Channel<suspend () -> Unit>,
    private val dispatchers: DispatcherProvider,
    private val debugTag: String = "NetworkBoundFlowResource",
) : NetworkBoundResource<ResultType, RequestType>() {

    abstract override suspend fun fetchFromNetwork(): RequestType

    abstract override suspend fun saveCallResult(entries: RequestType)

    abstract override suspend fun shouldFetch(data: ResultType?): Boolean

    final override suspend fun loadFromDb(): ResultType = loadFlowFromDb().first()

    protected abstract fun loadFlowFromDb(): Flow<ResultType>

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun fetchAsFlow(filterLocal: (ResultType) -> Boolean): Flow<ResultType> =
        channelFlow {
                val mutex = Mutex()
                var hasCollectedFirst = false

                Timber.tag(debugTag).d("subscribing to loadFlowFromDb()")
                loadFlowFromDb().collect { data ->
                    mutex.withLock {
                        if (hasCollectedFirst) {
                            Timber.tag(debugTag).d("subsequent DB emission, sending downstream")
                            send(data)
                        } else {
                            hasCollectedFirst = true

                            val canEmitInitialValue = filterLocal(data)
                            val willFetch = shouldFetch(data)
                            Timber.tag(debugTag)
                                .d(
                                    "first DB emission, canEmit=%s, willFetch=%s",
                                    canEmitInitialValue,
                                    willFetch,
                                )
                            if (canEmitInitialValue) send(data)

                            if (willFetch) {
                                Timber.tag(debugTag).d("refreshing resource from remote")
                                updaterChannel.send {
                                    try {
                                        saveCallResult(fetchFromNetwork())
                                        if (!canEmitInitialValue) {
                                            // Room won't re-emit if the stored data didn't change.
                                            // Force a fresh DB read to guarantee we always emit
                                            // something when filterLocal suppressed the initial
                                            // value.
                                            try {
                                                ensureActive()
                                                send(loadFromDb())
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                Timber.tag(debugTag).e(e)
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        if (e.isNetworkError()) {
                                            Timber.tag(debugTag)
                                                .d("falling back to local due to networking error")
                                        } else {
                                            Timber.tag(debugTag)
                                                .w(e, "fetch failed, returning local data")
                                        }

                                        if (!canEmitInitialValue) {
                                            try {
                                                ensureActive()
                                                send(data)
                                            } catch (e2: CancellationException) {
                                                throw e2
                                            } catch (e2: Exception) {
                                                Timber.tag(debugTag).e(e2)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                awaitClose()
            }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)
}
