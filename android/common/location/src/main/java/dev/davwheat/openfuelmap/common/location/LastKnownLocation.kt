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
