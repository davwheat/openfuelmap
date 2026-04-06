package dev.davwheat.openfuelmap.list.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import dev.davwheat.openfuelmap.data.repository.SavedLocation

/**
 * Modal sheet hosting a map whose centre stays pinned under a fixed crosshair. The caller commits
 * the current camera target as the new custom location when the user hits "Use this location".
 *
 * Initial camera position: the existing custom pin if set, otherwise [fallbackCenter] (typically
 * the user's current location or the UK centroid).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLocationPickerSheet(
    currentCustomLocation: SavedLocation?,
    fallbackCenter: LatLng,
    onConfirm: (SavedLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial =
        remember(currentCustomLocation, fallbackCenter) {
            currentCustomLocation?.let { LatLng(it.latitude, it.longitude) } ?: fallbackCenter
        }
    val cameraPositionState: CameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initial, 11f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Pick a location",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = "Drag the map until the pin is where you want to search from.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            // A fixed-height map keeps layout math simple inside ModalBottomSheet (which wraps
            // its content). 420dp is large enough for accurate placement without forcing the sheet
            // to eat the whole screen on shorter phones.
            Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                GoogleMap(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    cameraPositionState = cameraPositionState,
                    uiSettings =
                        MapUiSettings(
                            zoomControlsEnabled = false,
                            mapToolbarEnabled = false,
                            myLocationButtonEnabled = false,
                        ),
                    mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
                )
                // Pin is drawn as an overlay anchored to the centre of the viewport — the camera's
                // target is always under it, so the user drags the map beneath a stationary pin.
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = "Selected location",
                    modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = 48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        val target = cameraPositionState.position.target
                        onConfirm(
                            SavedLocation(latitude = target.latitude, longitude = target.longitude)
                        )
                    }
                ) {
                    Text("Use this location")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun CustomLocationPickerSheetPreview() {
    MaterialExpressiveTheme {
        Surface {
            CustomLocationPickerSheet(
                currentCustomLocation = SavedLocation(latitude = 51.5014, longitude = -0.1419),
                fallbackCenter = LatLng(54.5, -2.5),
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
