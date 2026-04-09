package dev.davwheat.openfuelmap.common.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

/** Coarse and fine location together — the set Play services needs to return a fix. */
private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Handle to the user's location-permission state. [hasPermission] re-composes when the user grants
 * or denies via the system dialog; [request] shows that dialog. The composable owner is in charge
 * of deciding when/whether to call [request] (typically once on first composition).
 */
@Stable
class LocationPermissionState
internal constructor(val hasPermission: Boolean, val request: () -> Unit)

/**
 * Remembers a [LocationPermissionState] that tracks coarse/fine location permission and can launch
 * the system permission dialog via [LocationPermissionState.request].
 */
@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PermissionChecker.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PermissionChecker.PERMISSION_GRANTED
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            hasPermission =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    return remember(hasPermission) {
        LocationPermissionState(
            hasPermission = hasPermission,
            request = { launcher.launch(LOCATION_PERMISSIONS) },
        )
    }
}
