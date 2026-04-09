package dev.davwheat.openfuelmap.list.impl.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import dev.davwheat.openfuelmap.app.api.ProvideTopBar
import dev.davwheat.openfuelmap.common.location.rememberLocationPermissionState
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import dev.davwheat.openfuelmap.forecourts.impl.detail.ForecourtDetailSheet
import dev.davwheat.openfuelmap.list.impl.viewmodel.ListViewModel
import dev.davwheat.openfuelmap.list.impl.viewmodel.SearchCenter

/** UK centroid — used as fallback centre when we have neither a device fix nor a custom pin. */
private val UK_CENTROID = LatLng(54.5, -2.5)

/**
 * Top app bar for the list screen. Shows a location toggle action that switches between the
 * device's current location and a user-picked custom pin.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ListScreenTopAppBar(usingCustomLocation: Boolean, onToggleCustomLocation: () -> Unit) {
    TopAppBar(
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        title = { Text("Nearby") },
        subtitle = {},
        actions = {
            SimpleTooltip(if (usingCustomLocation) "Use my location" else "Pick a location") {
                IconButton(onClick = onToggleCustomLocation, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector =
                            if (usingCustomLocation) Icons.Outlined.MyLocation
                            else Icons.Outlined.EditLocationAlt,
                        contentDescription =
                            if (usingCustomLocation) "Use my location" else "Pick a location",
                    )
                }
            }
        },
    )
}

/**
 * Nearby-stations list screen. Displays forecourts sorted by distance within an adjustable radius,
 * supports switching between device location and a custom map pin, and opens a
 * [ForecourtDetailSheet] on tap.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListScreen(viewModel: ListViewModel) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val radiusMi by viewModel.radiusMi.collectAsStateWithLifecycle()
    val customLocation by viewModel.customLocation.collectAsStateWithLifecycle()
    val searchCenter by viewModel.searchCenter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val fuelTypes by viewModel.fuelTypes.collectAsStateWithLifecycle()
    val selectedFuelType by viewModel.selectedFuelType.collectAsStateWithLifecycle()
    val selectedStation by viewModel.selectedStation.collectAsStateWithLifecycle()
    val priceHistory by viewModel.priceHistory.collectAsStateWithLifecycle()
    val priceHistoryLoading by viewModel.priceHistoryLoading.collectAsStateWithLifecycle()

    val fuelTypeNames = remember(fuelTypes) { fuelTypes.associate { it.id to it.name } }

    val locationPermission = rememberLocationPermissionState()
    val hasLocationPermission = locationPermission.hasPermission

    // Only request permission automatically when the user hasn't set a custom pin — someone who
    // deliberately picks a location shouldn't get a permission prompt later.
    LaunchedEffect(customLocation) {
        if (customLocation == null && !hasLocationPermission) {
            locationPermission.request()
        }
    }

    // The VM watches location updates itself; we just tell it when permission changes.
    LaunchedEffect(hasLocationPermission) {
        viewModel.setHasLocationPermission(hasLocationPermission)
    }

    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // Scroll back to the top whenever query parameters change.
    LaunchedEffect(radiusMi, searchCenter, selectedFuelType) { listState.scrollToItem(0) }

    var showPicker by remember { mutableStateOf(false) }

    ProvideTopBar {
        ListScreenTopAppBar(
            usingCustomLocation = customLocation != null,
            onToggleCustomLocation = {
                if (customLocation != null) {
                    // Revert to current location.
                    viewModel.setCustomLocation(null)
                } else {
                    showPicker = true
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchContextHeader(
                radiusMi = radiusMi,
                searchCenter = searchCenter,
                onRadiusChanged = { viewModel.setRadiusMi(it) },
                onChangePickedLocation = { showPicker = true },
            )

            HorizontalDivider()

            val reason =
                when {
                    searchCenter == null && !isLoading -> ListEmptyReason.NoLocation
                    error != null && results.isEmpty() ->
                        ListEmptyReason.Error(message = error ?: "Unknown error")
                    results.isEmpty() && !isLoading ->
                        ListEmptyReason.NoneInRadius(radiusMi = radiusMi)
                    else -> null
                }

            // Box scopes the loading indicator to the list area only — it overlaps the list
            // without covering the sticky header above.
            Box(modifier = Modifier.fillMaxSize()) {
                if (reason != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ListEmptyState(
                            reason = reason,
                            onRequestLocation = { locationPermission.request() },
                            onPickCustomLocation = { showPicker = true },
                            onIncreaseRadius = {
                                val nextStop =
                                    RADIUS_STOPS.firstOrNull { it > radiusMi }
                                        ?: RADIUS_STOPS.last()
                                viewModel.setRadiusMi(nextStop)
                            },
                            onRetry = { viewModel.setRadiusMi(radiusMi) },
                        )
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(items = results, key = { _, item -> item.forecourt.nodeId }) {
                            index,
                            item ->
                            if (index > 0) HorizontalDivider()
                            ForecourtListItem(
                                item = item,
                                onClick = { viewModel.selectStation(item.forecourt) },
                            )
                        }
                    }
                }

                if (isLoading) {
                    ContainedLoadingIndicator(
                        modifier =
                            Modifier.padding(16.dp).align(Alignment.TopCenter).semantics {
                                contentDescription = "Loading"
                                liveRegion = LiveRegionMode.Polite
                            }
                    )
                }
            }
        }

        if (error != null && results.isNotEmpty()) {
            // Soft error with stale results present — surface as snackbar rather than
            // replacing the list with the full empty state.
            Snackbar(
                modifier =
                    Modifier.align(Alignment.BottomCenter).padding(16.dp).semantics {
                        liveRegion = LiveRegionMode.Assertive
                    }
            ) {
                Text(error!!)
            }
        }
    }

    selectedStation?.let { selection ->
        ForecourtDetailSheet(
            forecourt = selection.basic,
            detail = selection.detail,
            fuelTypeNames = fuelTypeNames,
            selectedFuelType = selectedFuelType,
            onDismiss = { viewModel.clearSelection() },
            priceHistory = priceHistory,
            priceHistoryLoading = priceHistoryLoading,
            onRequestPriceHistory = viewModel::fetchPriceHistory,
        )
    }

    if (showPicker) {
        val fallback =
            remember(userLocation) {
                userLocation?.let { LatLng(it.latitude, it.longitude) } ?: UK_CENTROID
            }
        CustomLocationPickerSheet(
            currentCustomLocation = customLocation,
            fallbackCenter = fallback,
            onConfirm = {
                viewModel.setCustomLocation(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * Sticky header above the list showing the [RadiusSlider] and the current search centre (device
 * location, custom pin, or "no location yet") with animated transitions between states.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchContextHeader(
    radiusMi: Float,
    searchCenter: SearchCenter?,
    onRadiusChanged: (Float) -> Unit,
    onChangePickedLocation: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            RadiusSlider(radiusMi = radiusMi, onRadiusChanged = onRadiusChanged)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crossfade(
                    targetState =
                        when (searchCenter) {
                            is SearchCenter.Custom -> Icons.Outlined.EditLocationAlt
                            else -> Icons.Outlined.MyLocation
                        },
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "searchCenterIcon",
                ) { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription =
                            if (icon == Icons.Outlined.EditLocationAlt) "Using custom location"
                            else "Using current location",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                val motionScheme = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = searchCenter,
                    contentKey = {
                        when (it) {
                            null -> 0
                            is SearchCenter.CurrentLocation -> 1
                            is SearchCenter.Custom -> 2
                        }
                    },
                    transitionSpec = {
                        (slideInVertically(motionScheme.defaultSpatialSpec()) { h -> h / 2 } +
                            fadeIn(motionScheme.defaultEffectsSpec())) togetherWith
                            (slideOutVertically(motionScheme.defaultSpatialSpec()) { h -> -h / 2 } +
                                fadeOut(motionScheme.defaultEffectsSpec())) using
                            SizeTransform(clip = true)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = "searchCenterText",
                    contentAlignment = Alignment.CenterStart,
                ) { center ->
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text =
                                when (center) {
                                    is SearchCenter.Custom -> "Custom location"
                                    is SearchCenter.CurrentLocation -> "Current location"
                                    null -> "No location yet"
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        if (center is SearchCenter.Custom) {
                            Text(
                                text = "%.4f, %.4f".format(center.latitude, center.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onChangePickedLocation,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.zIndex(1f),
                ) {
                    Text("Change")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ListScreenTopAppBarMyLocationPreview() {
    MaterialExpressiveTheme {
        ListScreenTopAppBar(usingCustomLocation = false, onToggleCustomLocation = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ListScreenTopAppBarCustomLocationPreview() {
    MaterialExpressiveTheme {
        ListScreenTopAppBar(usingCustomLocation = true, onToggleCustomLocation = {})
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun SearchContextHeaderCurrentLocationPreview() {
    MaterialExpressiveTheme {
        Surface {
            SearchContextHeader(
                radiusMi = 5f,
                searchCenter = SearchCenter.CurrentLocation(51.5014, -0.1419),
                onRadiusChanged = {},
                onChangePickedLocation = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun SearchContextHeaderCustomLocationPreview() {
    MaterialExpressiveTheme {
        Surface {
            SearchContextHeader(
                radiusMi = 10f,
                searchCenter = SearchCenter.Custom(51.5014, -0.1419),
                onRadiusChanged = {},
                onChangePickedLocation = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun SearchContextHeaderNoLocationPreview() {
    MaterialExpressiveTheme {
        Surface {
            SearchContextHeader(
                radiusMi = 3f,
                searchCenter = null,
                onRadiusChanged = {},
                onChangePickedLocation = {},
            )
        }
    }
}
