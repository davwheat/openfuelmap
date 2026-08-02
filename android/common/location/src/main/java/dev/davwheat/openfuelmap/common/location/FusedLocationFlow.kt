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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The positions from the fused location provider. The first value is the last known position, which
 * is available immediately, thus a screen does not wait for the location hardware before it shows
 * something. The values after it come from the hardware.
 *
 * The caller must have `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. The flow closes if the
 * permission is missing.
 *
 * @param updateIntervalMillis the wanted time between two positions while the device moves.
 * @param minUpdateDistanceMeters the movement below which the provider gives no new position.
 */
@SuppressLint("MissingPermission")
internal fun fusedLocationUpdates(
    context: Context,
    updateIntervalMillis: Long,
    minUpdateDistanceMeters: Float,
): Flow<UserLocation> = callbackFlow {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(UserLocation(location.latitude, location.longitude))
            }
        }
    try {
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) trySend(UserLocation(location.latitude, location.longitude))
        }
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, updateIntervalMillis)
                .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
                .build(),
            callback,
            Looper.getMainLooper(),
        )
    } catch (e: SecurityException) {
        close(e)
        return@callbackFlow
    }
    awaitClose { client.removeLocationUpdates(callback) }
}
