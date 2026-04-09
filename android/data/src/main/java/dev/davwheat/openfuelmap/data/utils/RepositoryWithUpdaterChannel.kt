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
