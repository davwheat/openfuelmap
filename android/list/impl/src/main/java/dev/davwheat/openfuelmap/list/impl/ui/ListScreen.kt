package dev.davwheat.openfuelmap.list.impl.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import dev.davwheat.openfuelmap.app.api.LocalBottomNavBarProvider
import dev.davwheat.openfuelmap.common.location.rememberLocationPermissionState
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import dev.davwheat.openfuelmap.forecourts.impl.detail.ForecourtDetailSheet
import dev.davwheat.openfuelmap.forecourts.impl.filter.StationFilterSheet
import dev.davwheat.openfuelmap.list.impl.viewmodel.ListViewModel
import dev.davwheat.openfuelmap.list.impl.viewmodel.SearchCenter

/** UK centroid — used as fallback centre when we have neither a device fix nor a custom pin. */
private val UK_CENTROID = LatLng(54.5, -2.5)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ListScreenTopAppBar(
    usingCustomLocation: Boolean,
    onOpenFilter: () -> Unit,
    onToggleCustomLocation: () -> Unit,
) {
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
            SimpleTooltip("Filter") {
                IconButton(onClick = onOpenFilter, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.FilterAlt, contentDescription = "Filter")
                }
            }
        },
    )
}

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
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val excludedBrands by viewModel.excludedBrands.collectAsStateWithLifecycle()
    val selectedStation by viewModel.selectedStation.collectAsStateWithLifecycle()

    val fuelTypeNames = remember(fuelTypes) { fuelTypes.associate { it.id to it.name } }

    val bottomNavBar = LocalBottomNavBarProvider.current

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

    var showFilterSheet by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ListScreenTopAppBar(
                usingCustomLocation = customLocation != null,
                onOpenFilter = { showFilterSheet = true },
                onToggleCustomLocation = {
                    if (customLocation != null) {
                        // Revert to current location.
                        viewModel.setCustomLocation(null)
                    } else {
                        showPicker = true
                    }
                },
            )
        },
        bottomBar = bottomNavBar,
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
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
                        results.isEmpty() && !isLoading && searchCenter != null ->
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
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(
                                items = results,
                                key = { _, item -> item.forecourt.nodeId },
                            ) { index, item ->
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
                            modifier = Modifier.padding(16.dp).align(Alignment.TopCenter)
                        )
                    }
                }
            }

            if (error != null && results.isNotEmpty()) {
                // Soft error with stale results present — surface as snackbar rather than
                // replacing the list with the full empty state.
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text(error!!)
                }
            }
        }
    }

    selectedStation?.let { selection ->
        ForecourtDetailSheet(
            forecourt = selection.basic,
            detail = selection.detail,
            fuelTypeNames = fuelTypeNames,
            onDismiss = { viewModel.clearSelection() },
        )
    }

    if (showFilterSheet) {
        StationFilterSheet(
            fuelTypes = fuelTypes,
            selectedFuelType = selectedFuelType,
            onFuelTypeSelected = { viewModel.selectFuelType(it) },
            brands = brands,
            excludedBrands = excludedBrands,
            onBrandToggled = { viewModel.toggleBrandExcluded(it) },
            onDismiss = { showFilterSheet = false },
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
            AnimatedContent(
                targetState = searchCenter,
                // Animate only when the MODE changes; dragging the custom pin mutates the lat/lng
                // but shouldn't retrigger the transition.
                contentKey = {
                    when (it) {
                        null -> 0
                        is SearchCenter.CurrentLocation -> 1
                        is SearchCenter.Custom -> 2
                    }
                },
                transitionSpec = {
                    (slideInVertically { h -> h / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { h -> -h / 2 } + fadeOut())
                },
                label = "searchCenterMode",
            ) { center ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector =
                            when (center) {
                                is SearchCenter.Custom -> Icons.Outlined.EditLocationAlt
                                else -> Icons.Outlined.MyLocation
                            },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                when (center) {
                                    is SearchCenter.Custom -> "Custom location"
                                    is SearchCenter.CurrentLocation -> "Your location"
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
                    if (center is SearchCenter.Custom) {
                        TextButton(onClick = onChangePickedLocation) { Text("Change") }
                    }
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
        ListScreenTopAppBar(
            usingCustomLocation = false,
            onOpenFilter = {},
            onToggleCustomLocation = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ListScreenTopAppBarCustomLocationPreview() {
    MaterialExpressiveTheme {
        ListScreenTopAppBar(
            usingCustomLocation = true,
            onOpenFilter = {},
            onToggleCustomLocation = {},
        )
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
