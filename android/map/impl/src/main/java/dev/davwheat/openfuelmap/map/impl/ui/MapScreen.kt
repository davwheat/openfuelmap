package dev.davwheat.openfuelmap.map.impl.ui

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import dev.davwheat.openfuelmap.app.api.LocalBottomNavBarProvider
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import dev.davwheat.openfuelmap.data.repository.SavedCameraPosition
import dev.davwheat.openfuelmap.map.api.model.BoundingBox
import dev.davwheat.openfuelmap.map.impl.viewmodel.MapViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MapScreenTopAppBar(modifier: Modifier = Modifier, openFilterSheet: () -> Unit) {
    TopAppBar(
        modifier = modifier,
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        title = { Text("Open Fuel Map") },
        subtitle = {},
        actions = {
            SimpleTooltip("Filter") {
                IconButton(onClick = openFilterSheet, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.FilterAlt, contentDescription = "Filter")
                }
            }
        },
    )
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapScreen(viewModel: MapViewModel) {
    val markers by viewModel.markers.collectAsStateWithLifecycle()
    val selectedStation by viewModel.selectedStation.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val fuelTypes by viewModel.fuelTypes.collectAsStateWithLifecycle()
    val selectedFuelType by viewModel.selectedFuelType.collectAsStateWithLifecycle()

    val fuelTypeNames = remember(fuelTypes) { fuelTypes.associate { it.id to it.name } }

    val bottomNavBar = LocalBottomNavBarProvider.current

    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PermissionChecker.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            hasLocationPermission =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        // Default to London; overridden by saved position or last known location once available.
        position = CameraPosition.fromLatLngZoom(LatLng(51.5074, -0.1278), 10f)
    }

    // Tracks whether we have applied an initial position (saved or geolocation) so we don't
    // keep snapping the camera after the user starts interacting with the map.
    var initialPositionApplied by remember { mutableStateOf(false) }
    // Gate the geolocation override until we've checked DataStore for a saved position.
    var savedPositionChecked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val saved = viewModel.loadInitialCameraPosition()
        if (saved != null && !initialPositionApplied) {
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(LatLng(saved.latitude, saved.longitude), saved.zoom)
            initialPositionApplied = true
        }
        savedPositionChecked = true
    }

    LaunchedEffect(hasLocationPermission, savedPositionChecked) {
        if (!savedPositionChecked || initialPositionApplied || !hasLocationPermission) {
            return@LaunchedEffect
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null && !initialPositionApplied) {
                cameraPositionState.position =
                    CameraPosition.fromLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        10f,
                    )
                initialPositionApplied = true
            }
        }
    }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { !cameraPositionState.isMoving && cameraPositionState.projection != null }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                delay(100)
                val projection = cameraPositionState.projection ?: return@collect
                val bounds = projection.visibleRegion.latLngBounds
                viewModel.loadStationsInBounds(
                    BoundingBox(
                        swLat = bounds.southwest.latitude,
                        swLng = bounds.southwest.longitude,
                        neLat = bounds.northeast.latitude,
                        neLng = bounds.northeast.longitude,
                    )
                )
                val position = cameraPositionState.position
                viewModel.saveCameraPosition(
                    SavedCameraPosition(
                        latitude = position.target.latitude,
                        longitude = position.target.longitude,
                        zoom = position.zoom,
                    )
                )
            }
    }

    var showFilterSheet by remember { mutableStateOf(false) }

    val priceMarkerIcons = rememberPriceMarkerIconCache()

    // Render any missing marker bitmaps on a background dispatcher so the main thread is only
    // responsible for cheap descriptor lookups. Already-cached entries are skipped, so panning
    // through areas we've already drawn does no work.
    LaunchedEffect(markers, priceMarkerIcons) {
        priceMarkerIcons.ensure(markers.map { it.label to it.colorPosition })
    }

    // Pair each marker with its rendered icon, filtering out any whose bitmap hasn't been
    // produced yet. Driven by derivedStateOf so the inner marker loop doesn't need to branch
    // on null icons — it just iterates ready-to-render entries.
    val readyMarkers by
        remember(priceMarkerIcons) {
            derivedStateOf {
                markers.mapNotNull { marker ->
                    priceMarkerIcons.getOrNull(marker.label, marker.colorPosition)?.let {
                        marker to it
                    }
                }
            }
        }

    Scaffold(
        bottomBar = bottomNavBar,
        topBar = { MapScreenTopAppBar(openFilterSheet = { showFilterSheet = true }) },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
                mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            ) {
                readyMarkers.forEach { (marker, icon) ->
                    val station = marker.station
                    val markerState =
                        rememberUpdatedMarkerState(
                            position = LatLng(station.latitude, station.longitude)
                        )
                    Marker(
                        state = markerState,
                        title = station.tradingName,
                        snippet = station.brandName,
                        anchor = Offset(0.5f, 0.5f),
                        zIndex = marker.zIndex,
                        icon = icon,
                        onClick = {
                            viewModel.selectStation(station)
                            true
                        },
                    )
                }
            }

            if (isLoading) {
                ContainedLoadingIndicator(
                    modifier = Modifier.padding(16.dp).align(Alignment.TopCenter)
                )
            }

            error?.let { errorMessage ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text(errorMessage)
                }
            }
        }
    }

    selectedStation?.let { selection ->
        StationDetailSheet(
            forecourt = selection.basic,
            detail = selection.detail,
            fuelTypeNames = fuelTypeNames,
            onDismiss = { viewModel.clearSelection() },
        )
    }

    if (showFilterSheet) {
        MapFilterSheet(
            fuelTypes = fuelTypes,
            selectedFuelType = selectedFuelType,
            onFuelTypeSelected = { viewModel.selectFuelType(it) },
            onDismiss = { showFilterSheet = false },
        )
    }
}
