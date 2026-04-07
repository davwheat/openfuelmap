package dev.davwheat.openfuelmap.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cacheMetadataStore: DataStore<Preferences> by
    preferencesDataStore(name = "cache_metadata")

@Singleton
class CacheMetadataRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    private object Keys {
        val FUEL_TYPES_LAST_FETCHED = longPreferencesKey("fuel_types_last_fetched")
        val BRANDS_LAST_FETCHED = longPreferencesKey("brands_last_fetched")
    }

    private val defaultMaxAge: Duration = 8.hours

    suspend fun isFuelTypesStale(maxAge: Duration = defaultMaxAge): Boolean =
        isStale(Keys.FUEL_TYPES_LAST_FETCHED, maxAge)

    suspend fun markFuelTypesFetched() {
        mark(Keys.FUEL_TYPES_LAST_FETCHED)
    }

    suspend fun isBrandsStale(maxAge: Duration = defaultMaxAge): Boolean =
        isStale(Keys.BRANDS_LAST_FETCHED, maxAge)

    suspend fun markBrandsFetched() {
        mark(Keys.BRANDS_LAST_FETCHED)
    }

    private suspend fun isStale(key: Preferences.Key<Long>, maxAge: Duration): Boolean {
        val lastFetched = context.cacheMetadataStore.data.map { prefs -> prefs[key] ?: 0L }.first()
        return System.currentTimeMillis() - lastFetched > maxAge.inWholeMilliseconds
    }

    private suspend fun mark(key: Preferences.Key<Long>) {
        context.cacheMetadataStore.edit { prefs -> prefs[key] = System.currentTimeMillis() }
    }
}
