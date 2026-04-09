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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices

/**
 * One-shot fetch of the device's last-known location from fused location provider. Emits `null`
 * until the fix resolves (or indefinitely if it never does). Keyed on [hasPermission] so the
 * request only fires once permission has been granted.
 *
 * Callers must hold `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` — the suppress below
 * reflects that the caller has checked via [rememberLocationPermissionState].
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberLastKnownLocation(hasPermission: Boolean): State<UserLocation?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<UserLocation?>(null) }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                state.value = UserLocation(latitude = loc.latitude, longitude = loc.longitude)
            }
        }
    }
    return state
}
