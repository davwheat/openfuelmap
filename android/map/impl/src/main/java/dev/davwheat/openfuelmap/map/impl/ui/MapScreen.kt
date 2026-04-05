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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

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
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val selectedBrand by viewModel.selectedBrand.collectAsStateWithLifecycle()

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
    val coroutineScope = rememberCoroutineScope()

    // Quantise zoom to the integer bucket so clusters only re-bucket when the user crosses a
    // zoom level — not on every pixel of a pinch gesture.
    val clusteringZoom by remember {
        derivedStateOf { cameraPositionState.position.zoom.toInt().toFloat() }
    }

    // Group nearby markers into clusters below CLUSTER_DISABLED_AT_ZOOM; at higher zooms every
    // station is emitted as its own Single.
    val clusters by remember { derivedStateOf { clusterMarkers(markers, clusteringZoom) } }

    // Render both pill and cluster-badge bitmaps for the current set on a background dispatcher.
    // Already-cached entries are skipped, so crossing a zoom boundary or panning back over an
    // area we've drawn does no work.
    LaunchedEffect(clusters, priceMarkerIcons) {
        val pillRequests = mutableListOf<Pair<String, Float?>>()
        val badgeRequests = mutableListOf<Pair<String, Float?>>()
        for (cluster in clusters) {
            when (cluster) {
                is MapCluster.Single ->
                    pillRequests.add(cluster.marker.label to cluster.marker.colorPosition)
                is MapCluster.Group ->
                    badgeRequests.add(clusterCountLabel(cluster.count) to cluster.minColorPosition)
            }
        }
        priceMarkerIcons.ensure(pillRequests)
        priceMarkerIcons.ensureClusterBadges(badgeRequests)
    }

    // Pair each cluster with its rendered icon, filtering out any whose bitmap isn't ready yet.
    // Driven by derivedStateOf so the render loop doesn't need to branch on null — it iterates
    // ready-to-draw entries only.
    val readyClusters by
        remember(priceMarkerIcons) {
            derivedStateOf {
                clusters.mapNotNull { cluster ->
                    val icon =
                        when (cluster) {
                            is MapCluster.Single ->
                                priceMarkerIcons.getOrNull(
                                    cluster.marker.label,
                                    cluster.marker.colorPosition,
                                )
                            is MapCluster.Group ->
                                priceMarkerIcons.getClusterBadgeOrNull(
                                    clusterCountLabel(cluster.count),
                                    cluster.minColorPosition,
                                )
                        }
                    icon?.let { cluster to it }
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
                readyClusters.forEach { (cluster, icon) ->
                    // Stable keys across cluster-list reorderings so Compose keeps each marker's
                    // node (and its native Google Maps marker) alive when the same station or
                    // grid cell is still present, even if the HashMap-derived list order changed.
                    val identity =
                        when (cluster) {
                            is MapCluster.Single -> cluster.marker.station.nodeId
                            is MapCluster.Group -> cluster.cellKey
                        }
                    key(identity) {
                        when (cluster) {
                            is MapCluster.Single -> {
                                val marker = cluster.marker
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
                            is MapCluster.Group -> {
                                val markerState =
                                    rememberUpdatedMarkerState(
                                        position = LatLng(cluster.centroidLat, cluster.centroidLng)
                                    )
                                Marker(
                                    state = markerState,
                                    anchor = Offset(0.5f, 0.5f),
                                    icon = icon,
                                    onClick = {
                                        coroutineScope.launch {
                                            animateCameraToCluster(cameraPositionState, cluster)
                                        }
                                        true
                                    },
                                )
                            }
                        }
                    }
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
            brands = brands,
            selectedBrand = selectedBrand,
            onBrandSelected = { viewModel.selectBrand(it) },
            onDismiss = { showFilterSheet = false },
        )
    }
}

/** Padding around the cluster bounds when fitting them into the viewport (in pixels). */
private const val CLUSTER_ZOOM_PADDING_PX: Int = 120

/**
 * Maximum zoom level that tapping a cluster can land on. A tight cluster (members in one small
 * area) would otherwise fit into street-level zoom (18–20), which is almost never what the user
 * wants.
 */
private const val CLUSTER_TAP_MAX_ZOOM: Float = 15f

/** Camera animation duration when zooming into a cluster, in milliseconds. */
private const val CLUSTER_ZOOM_ANIMATION_MS: Int = 400

/** Google Maps' tile size at zoom 0. The world is 256×256 px at that zoom. */
private const val MERCATOR_WORLD_PX: Double = 256.0

private suspend fun animateCameraToCluster(
    cameraState: CameraPositionState,
    cluster: MapCluster.Group,
) {
    val centroid = LatLng(cluster.centroidLat, cluster.centroidLng)
    // Work out the zoom that would fit the cluster's bounds, clamp to the cap, then do a single
    // clean animation. If we can't compute the fit (no projection yet, or a single-point
    // cluster), just go to the cap.
    val fitted = zoomToFitCluster(cameraState, cluster)
    val targetZoom = (fitted ?: CLUSTER_TAP_MAX_ZOOM).coerceAtMost(CLUSTER_TAP_MAX_ZOOM)
    cameraState.animate(
        CameraUpdateFactory.newLatLngZoom(centroid, targetZoom),
        durationMs = CLUSTER_ZOOM_ANIMATION_MS,
    )
}

/**
 * Mercator calculation of the zoom level at which this cluster's bounds would fit the current
 * viewport, minus [CLUSTER_ZOOM_PADDING_PX] on each edge. Returns `null` when the projection isn't
 * ready or the bounds degenerate to a single point — caller falls back to [CLUSTER_TAP_MAX_ZOOM].
 */
private fun zoomToFitCluster(cameraState: CameraPositionState, cluster: MapCluster.Group): Float? {
    if (cluster.swLat == cluster.neLat && cluster.swLng == cluster.neLng) return null
    val projection = cameraState.projection ?: return null

    // Projection of the current viewport's screen corners gives us its pixel size, which is what
    // the zoom-to-fit formula needs as its denominator.
    val region = projection.visibleRegion
    val nearLeft = projection.toScreenLocation(region.nearLeft)
    val nearRight = projection.toScreenLocation(region.nearRight)
    val farLeft = projection.toScreenLocation(region.farLeft)
    val viewportWidthPx = abs(nearRight.x - nearLeft.x)
    val viewportHeightPx = abs(nearLeft.y - farLeft.y)
    if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return null

    val latFraction = (mercatorLatRad(cluster.neLat) - mercatorLatRad(cluster.swLat)) / PI
    val lngDiff = cluster.neLng - cluster.swLng
    val lngFraction = (if (lngDiff < 0) lngDiff + 360 else lngDiff) / 360.0
    val availableW = (viewportWidthPx - 2 * CLUSTER_ZOOM_PADDING_PX).coerceAtLeast(1)
    val availableH = (viewportHeightPx - 2 * CLUSTER_ZOOM_PADDING_PX).coerceAtLeast(1)

    val lngZoom =
        if (lngFraction > 0.0) log2(availableW / MERCATOR_WORLD_PX / lngFraction)
        else Double.POSITIVE_INFINITY
    val latZoom =
        if (latFraction > 0.0) log2(availableH / MERCATOR_WORLD_PX / latFraction)
        else Double.POSITIVE_INFINITY
    if (lngZoom.isInfinite() && latZoom.isInfinite()) return null
    return min(lngZoom, latZoom).toFloat()
}

/** Converts a latitude (degrees) to its Mercator Y coordinate, in radians, range [-π/2, π/2]. */
private fun mercatorLatRad(lat: Double): Double {
    val s = sin(lat * PI / 180.0)
    val radX2 = ln((1 + s) / (1 - s)) / 2
    return max(min(radX2, PI), -PI) / 2
}
