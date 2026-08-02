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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The time in milliseconds between two position calculations. */
private const val DEFAULT_UPDATE_INTERVAL_MS = 5_000L

/**
 * The minimum movement in metres between two updates. Below this distance, the position of the user
 * on the map does not change sufficiently to show a new dot.
 */
private const val MIN_UPDATE_DISTANCE_M = 10f

/**
 * The position of the device, which changes as the user moves. The value is `null` until the first
 * position becomes available.
 *
 * The flow starts when the screen becomes visible, and it stops when the screen goes to the
 * background. Thus the app does not use the location hardware while the user cannot see the map.
 *
 * The caller must have `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. Use
 * [rememberLocationPermissionState] to get the permission, then give the result as [hasPermission].
 */
@Composable
fun rememberCurrentLocation(
    hasPermission: Boolean,
    updateIntervalMillis: Long = DEFAULT_UPDATE_INTERVAL_MS,
): State<UserLocation?> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf<UserLocation?>(null) }
    LaunchedEffect(hasPermission, context, lifecycleOwner, updateIntervalMillis) {
        if (!hasPermission) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            locationUpdates(context, updateIntervalMillis).collect { state.value = it }
        }
    }
    return state
}

/**
 * The positions from the fused location provider. The first value is the last known position, which
 * is available immediately. The values after it come from the location hardware.
 *
 * The permission check occurs in [rememberCurrentLocation], thus the suppression below is correct.
 */
@SuppressLint("MissingPermission")
private fun locationUpdates(context: Context, updateIntervalMillis: Long): Flow<UserLocation> =
    callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    trySend(UserLocation(location.latitude, location.longitude))
                }
            }
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) trySend(UserLocation(location.latitude, location.longitude))
        }
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, updateIntervalMillis)
                .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
                .build(),
            callback,
            Looper.getMainLooper(),
        )
        awaitClose { client.removeLocationUpdates(callback) }
    }
