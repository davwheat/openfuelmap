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
package dev.davwheat.openfuelmap

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.davwheat.openfuelmap.data.repository.BrandRepository
import dev.davwheat.openfuelmap.data.repository.FuelTypeRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class OpenFuelMapApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var brandRepository: BrandRepository
    @Inject lateinit var fuelTypeRepository: FuelTypeRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // App-scoped background scope for fire-and-forget startup work. SupervisorJob so one
    // prefetch failing doesn't cancel the other.
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Warm the brand and fuel-type caches at launch so feature UIs (e.g. the map filter
        // sheet) have them ready on first open, rather than waiting for a network round-trip
        // when the user taps in. Each prefetch is a no-op if the cache is already populated.
        startupScope.launch {
            runCatching { brandRepository.prefetch() }
                .onFailure { Timber.w(it, "Brand prefetch failed") }
        }
        startupScope.launch {
            runCatching { fuelTypeRepository.prefetch() }
                .onFailure { Timber.w(it, "Fuel-type prefetch failed") }
        }
    }
}
