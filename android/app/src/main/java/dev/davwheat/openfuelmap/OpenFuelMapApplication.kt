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
