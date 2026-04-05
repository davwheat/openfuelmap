package dev.davwheat.openfuelmap.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "user_preferences")

data class SavedCameraPosition(val latitude: Double, val longitude: Double, val zoom: Float)

@Singleton
class UserPreferencesRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    private object Keys {
        val SELECTED_FUEL_TYPE = stringPreferencesKey("selected_fuel_type")
        val CAMERA_LAT = doublePreferencesKey("camera_latitude")
        val CAMERA_LNG = doublePreferencesKey("camera_longitude")
        val CAMERA_ZOOM = floatPreferencesKey("camera_zoom")
    }

    val selectedFuelType: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.SELECTED_FUEL_TYPE] }

    suspend fun setSelectedFuelType(fuelTypeId: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SELECTED_FUEL_TYPE] = fuelTypeId }
    }

    val lastCameraPosition: Flow<SavedCameraPosition?> =
        context.dataStore.data.map { prefs ->
            val lat = prefs[Keys.CAMERA_LAT]
            val lng = prefs[Keys.CAMERA_LNG]
            val zoom = prefs[Keys.CAMERA_ZOOM]
            if (lat != null && lng != null && zoom != null) {
                SavedCameraPosition(lat, lng, zoom)
            } else {
                null
            }
        }

    suspend fun setLastCameraPosition(position: SavedCameraPosition) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAMERA_LAT] = position.latitude
            prefs[Keys.CAMERA_LNG] = position.longitude
            prefs[Keys.CAMERA_ZOOM] = position.zoom
        }
    }
}
