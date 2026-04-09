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
package dev.davwheat.openfuelmap.common.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Default cadence at which the fused location provider will deliver updates. */
private const val DEFAULT_UPDATE_INTERVAL_MS: Long = 30_000L

/**
 * Wraps [com.google.android.gms.location.FusedLocationProviderClient] as a [Flow]. Starts
 * requesting location updates when the returned flow is collected and releases the client callback
 * when collection stops.
 */
@Singleton
class LocationUpdatesProvider
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    /**
     * Stream of device location updates. Each emission corresponds to the device having moved at
     * least [minimumDistanceMeters] from the last reported position.
     *
     * The caller **must** hold `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. The flow closes
     * if permission is missing. No replay; subscribers see only post-subscription fixes.
     *
     * @param minimumDistanceMeters displacement threshold below which updates are suppressed.
     * @param intervalMs desired delivery cadence when the device is moving (balanced with
     *   [Priority.PRIORITY_BALANCED_POWER_ACCURACY]).
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(
        minimumDistanceMeters: Float,
        intervalMs: Long = DEFAULT_UPDATE_INTERVAL_MS,
    ): Flow<UserLocation> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
                .setMinUpdateDistanceMeters(minimumDistanceMeters)
                .build()
        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        trySend(UserLocation(latitude = loc.latitude, longitude = loc.longitude))
                    }
                }
            }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
