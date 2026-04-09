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
package dev.davwheat.openfuelmap.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.davwheat.openfuelmap.data.DistanceUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "user_preferences")

data class SavedCameraPosition(val latitude: Double, val longitude: Double, val zoom: Float)

/** A user-chosen pin on the map, used by the List screen when "custom location" is selected. */
data class SavedLocation(val latitude: Double, val longitude: Double)

/** Default radius (miles) applied when the user opens the List screen for the first time. */
const val DEFAULT_SEARCH_RADIUS_MI: Float = 10f

@Singleton
class UserPreferencesRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    private object Keys {
        val SELECTED_FUEL_TYPE = stringPreferencesKey("selected_fuel_type")
        val EXCLUDED_BRANDS = stringSetPreferencesKey("excluded_brands")
        val CAMERA_LAT = doublePreferencesKey("camera_latitude")
        val CAMERA_LNG = doublePreferencesKey("camera_longitude")
        val CAMERA_ZOOM = floatPreferencesKey("camera_zoom")
        val SEARCH_RADIUS_MI = floatPreferencesKey("search_radius_mi")
        val CUSTOM_SEARCH_LAT = doublePreferencesKey("custom_search_lat")
        val CUSTOM_SEARCH_LNG = doublePreferencesKey("custom_search_lng")
        val LAST_TOP_LEVEL_ROUTE = stringPreferencesKey("last_top_level_route")
        val COLORBLIND_MODE = booleanPreferencesKey("colorblind_mode")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
    }

    val selectedFuelType: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.SELECTED_FUEL_TYPE] }

    suspend fun setSelectedFuelType(fuelTypeId: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SELECTED_FUEL_TYPE] = fuelTypeId }
    }

    val excludedBrands: Flow<Set<String>> =
        context.dataStore.data.map { prefs -> prefs[Keys.EXCLUDED_BRANDS] ?: emptySet() }

    suspend fun setExcludedBrands(brands: Set<String>) {
        context.dataStore.edit { prefs ->
            if (brands.isEmpty()) prefs.remove(Keys.EXCLUDED_BRANDS)
            else prefs[Keys.EXCLUDED_BRANDS] = brands
        }
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

    /** List-screen search radius in miles. Defaults to [DEFAULT_SEARCH_RADIUS_MI]. */
    val searchRadiusMi: Flow<Float> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.SEARCH_RADIUS_MI] ?: DEFAULT_SEARCH_RADIUS_MI
        }

    suspend fun setSearchRadiusMi(radius: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.SEARCH_RADIUS_MI] = radius }
    }

    /**
     * User-picked centre location for the List screen. `null` means "use current device location".
     */
    val customSearchLocation: Flow<SavedLocation?> =
        context.dataStore.data.map { prefs ->
            val lat = prefs[Keys.CUSTOM_SEARCH_LAT]
            val lng = prefs[Keys.CUSTOM_SEARCH_LNG]
            if (lat != null && lng != null) SavedLocation(lat, lng) else null
        }

    suspend fun setCustomSearchLocation(location: SavedLocation?) {
        context.dataStore.edit { prefs ->
            if (location == null) {
                prefs.remove(Keys.CUSTOM_SEARCH_LAT)
                prefs.remove(Keys.CUSTOM_SEARCH_LNG)
            } else {
                prefs[Keys.CUSTOM_SEARCH_LAT] = location.latitude
                prefs[Keys.CUSTOM_SEARCH_LNG] = location.longitude
            }
        }
    }

    /**
     * Stable identifier of the top-level route the user was on last time the app was open. Used to
     * restore to the same tab on cold launch. The `:data` layer doesn't know the route-id
     * vocabulary — callers in `:app` define the mapping.
     */
    val lastTopLevelRoute: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[Keys.LAST_TOP_LEVEL_ROUTE] }

    suspend fun setLastTopLevelRoute(id: String) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_TOP_LEVEL_ROUTE] = id }
    }

    val colorblindMode: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.COLORBLIND_MODE] ?: false }

    suspend fun setColorblindMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.COLORBLIND_MODE] = enabled }
    }

    val distanceUnit: Flow<DistanceUnit> =
        context.dataStore.data.map { prefs ->
            DistanceUnit.fromStoredValue(prefs[Keys.DISTANCE_UNIT])
        }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { prefs -> prefs[Keys.DISTANCE_UNIT] = unit.name }
    }
}
