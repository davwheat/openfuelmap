package dev.davwheat.openfuelmap.data.utils

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A helper class to add an updater channel to a repository, and automatically run jobs from this
 * channel on an IO thread.
 */
open class RepositoryWithUpdaterChannel(dispatchers: DispatcherProvider) {
    protected val updaterChannel = Channel<suspend () -> Unit>(capacity = RENDEZVOUS)

    init {
        CoroutineScope(dispatchers.io).launch {
            for (task in updaterChannel) {
                try {
                    task()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.isNetworkError()) {
                        Timber.w("Network error while processing background task:\n\n${e.message}")
                    } else {
                        Timber.e(e, "Error while processing background task")
                    }
                }
            }
        }
    }
}
