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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import dev.davwheat.openfuelmap.common.maps.MapAttributionControl
import dev.davwheat.openfuelmap.common.maps.MapSurface
import dev.davwheat.openfuelmap.common.maps.attributionHtml
import dev.davwheat.openfuelmap.common.maps.rememberMapStyleUrl
import dev.davwheat.openfuelmap.data.repository.SavedLocation
import dev.davwheat.openfuelmap.list.impl.R
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/** The zoom level when the picker opens. At town level the user can put a pin with a small move. */
private const val PICKER_ZOOM: Double = 11.0

/**
 * Modal sheet hosting a map whose centre stays pinned under a fixed crosshair. The caller commits
 * the current camera target as the new custom location when the user hits "Use this location".
 *
 * Initial camera position: [currentCenter], which should be the location currently used for search
 * results (custom pin, device location, or a fallback).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLocationPickerSheet(
    currentCenter: LatLng,
    onConfirm: (SavedLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial = remember(currentCenter) { currentCenter }
    val styleUrl = rememberMapStyleUrl()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var attributionHtml by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.picker_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            // A fixed-height map keeps layout math simple inside ModalBottomSheet (which wraps
            // its content). 420dp is large enough for accurate placement without forcing the sheet
            // to eat the whole screen on shorter phones.
            Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                MapSurface(
                    styleUrl = styleUrl,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                ) { _, mapLibreMap, style ->
                    DisposableEffect(mapLibreMap) {
                        map = mapLibreMap
                        onDispose { map = null }
                    }
                    DisposableEffect(style) {
                        attributionHtml = style.attributionHtml()
                        onDispose { attributionHtml = null }
                    }
                    LaunchedEffect(mapLibreMap) {
                        // With pitch, the crosshair is not above the target of the camera.
                        mapLibreMap.setMinPitchPreference(0.0)
                        mapLibreMap.setMaxPitchPreference(0.0)
                        mapLibreMap.uiSettings.isRotateGesturesEnabled = false
                        // [MapAttributionControl] gives the credit in place of the native widgets.
                        mapLibreMap.uiSettings.isAttributionEnabled = false
                        mapLibreMap.uiSettings.isLogoEnabled = false
                        mapLibreMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(initial, PICKER_ZOOM)
                        )
                    }
                }
                // Pin is drawn as an overlay anchored to the centre of the viewport — the camera's
                // target is always under it, so the user drags the map beneath a stationary pin.
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = stringResource(R.string.picker_pin_description),
                    modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-48).dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                MapAttributionControl(
                    attributionHtml = attributionHtml,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    textSide = Alignment.End,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = dropUnlessResumed { onDismiss() }) {
                    Text(stringResource(R.string.picker_cancel))
                }
                Button(
                    enabled = map != null,
                    onClick =
                        dropUnlessResumed {
                            val target = map?.cameraPosition?.target ?: return@dropUnlessResumed
                            onConfirm(
                                SavedLocation(
                                    latitude = target.latitude,
                                    longitude = target.longitude,
                                )
                            )
                        },
                ) {
                    Text(stringResource(R.string.picker_confirm))
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
                currentCenter = LatLng(51.5014, -0.1419),
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
