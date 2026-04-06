package dev.davwheat.openfuelmap.map.impl.ui

import android.annotation.SuppressLint
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
import androidx.compose.material3.MaterialExpressiveTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.davwheat.openfuelmap.common.location.rememberLastKnownLocation
import dev.davwheat.openfuelmap.common.location.rememberLocationPermissionState
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import dev.davwheat.openfuelmap.data.repository.SavedCameraPosition
import dev.davwheat.openfuelmap.forecourts.api.model.BoundingBox
import dev.davwheat.openfuelmap.forecourts.impl.detail.ForecourtDetailSheet
import dev.davwheat.openfuelmap.forecourts.impl.filter.StationFilterSheet
import dev.davwheat.openfuelmap.map.impl.viewmodel.MapViewModel
import kotlin.math.log2
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun MapScreenTopAppBarPreview() {
    MaterialExpressiveTheme { MapScreenTopAppBar(openFilterSheet = {}) }
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
    val excludedBrands by viewModel.excludedBrands.collectAsStateWithLifecycle()

    val fuelTypeNames = remember(fuelTypes) { fuelTypes.associate { it.id to it.name } }

    val bottomNavBar = LocalBottomNavBarProvider.current

    val locationPermission = rememberLocationPermissionState()
    val hasLocationPermission = locationPermission.hasPermission

    LaunchedEffect(Unit) { if (!hasLocationPermission) locationPermission.request() }

    val lastKnownLocation by rememberLastKnownLocation(hasLocationPermission)

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

    LaunchedEffect(lastKnownLocation, savedPositionChecked) {
        val loc = lastKnownLocation
        if (!savedPositionChecked || initialPositionApplied || loc == null) {
            return@LaunchedEffect
        }
        cameraPositionState.position =
            CameraPosition.fromLatLngZoom(LatLng(loc.latitude, loc.longitude), 10f)
        initialPositionApplied = true
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
            fuelTypeBlurb =
                "Only show stations offering this fuel type. Prices shown on the map will also " +
                    "be for this fuel.",
            brandBlurb = "Tap a brand to hide its stations from the map.",
        )
    }
}

/**
 * Fraction of the viewport the cluster's bounds should occupy after zooming. 0.78 leaves ~11%
 * padding on each side, which sits comfortably within the marker pills' own footprint.
 */
private const val CLUSTER_FIT_RATIO: Double = 0.78

/**
 * Maximum zoom level that tapping a cluster can land on. A tight cluster (members in one small
 * area) would otherwise fit into street-level zoom (18–20), which is almost never what the user
 * wants.
 */
private const val CLUSTER_TAP_MAX_ZOOM: Float = 15f

/** Camera animation duration when zooming into a cluster, in milliseconds. */
private const val CLUSTER_ZOOM_ANIMATION_MS: Int = 400

private suspend fun animateCameraToCluster(
    cameraState: CameraPositionState,
    cluster: MapCluster.Group,
) {
    // Centre on the MIDPOINT of the cluster's bounds, not the centroid. If we centre on the
    // centroid of an asymmetric cluster (4 tight stations + 1 outlier), the centroid is pulled
    // toward the dense side and the outlier falls outside the viewport.
    val target =
        LatLng((cluster.swLat + cluster.neLat) / 2.0, (cluster.swLng + cluster.neLng) / 2.0)
    val fitted = zoomToFitCluster(cameraState, cluster)
    val targetZoom = (fitted ?: CLUSTER_TAP_MAX_ZOOM).coerceAtMost(CLUSTER_TAP_MAX_ZOOM)
    Timber.d(
        "cluster-tap count=%d bounds=sw(%.6f,%.6f)→ne(%.6f,%.6f) mid=(%.6f,%.6f) " +
            "fitted=%s cap=%.2f -> targetZoom=%.2f (from zoom=%.2f)",
        cluster.count,
        cluster.swLat,
        cluster.swLng,
        cluster.neLat,
        cluster.neLng,
        target.latitude,
        target.longitude,
        fitted?.let { "%.2f".format(it) } ?: "null",
        CLUSTER_TAP_MAX_ZOOM,
        targetZoom,
        cameraState.position.zoom,
    )
    cameraState.animate(
        CameraUpdateFactory.newLatLngZoom(target, targetZoom),
        durationMs = CLUSTER_ZOOM_ANIMATION_MS,
    )
    Timber.d(
        "cluster-tap settled at zoom=%.2f target=(%.6f,%.6f)",
        cameraState.position.zoom,
        cameraState.position.target.latitude,
        cameraState.position.target.longitude,
    )
}

/**
 * Compute the target zoom that makes the cluster's bounds occupy [CLUSTER_FIT_RATIO] of the current
 * viewport's span. Works by comparing the cluster's lat/lng span to the visible region's lat/lng
 * span at the *current* zoom, then translating the ratio into a zoom delta. Since both spans live
 * in the same degree-space at (approximately) the same latitude, Mercator distortion cancels out
 * and no pixel-size guesswork is needed.
 *
 * Returns `null` when the projection isn't ready, the current viewport is zero-size, or the cluster
 * degenerates to a single point — caller falls back to [CLUSTER_TAP_MAX_ZOOM].
 */
private fun zoomToFitCluster(cameraState: CameraPositionState, cluster: MapCluster.Group): Float? {
    if (cluster.swLat == cluster.neLat && cluster.swLng == cluster.neLng) {
        Timber.d("zoomToFitCluster: degenerate cluster (single point) — returning null")
        return null
    }
    val projection = cameraState.projection
    if (projection == null) {
        Timber.d("zoomToFitCluster: projection is null — returning null")
        return null
    }

    val visible = projection.visibleRegion.latLngBounds
    val currentLngSpan = visible.northeast.longitude - visible.southwest.longitude
    val currentLatSpan = visible.northeast.latitude - visible.southwest.latitude
    if (currentLngSpan <= 0 || currentLatSpan <= 0) {
        Timber.d(
            "zoomToFitCluster: zero viewport lng=%.6f lat=%.6f — returning null",
            currentLngSpan,
            currentLatSpan,
        )
        return null
    }

    val clusterLngSpan = cluster.neLng - cluster.swLng
    val clusterLatSpan = cluster.neLat - cluster.swLat

    // `delta = log2(currentSpan * fitRatio / clusterSpan)` — positive delta means zoom IN that
    // many levels. Take the axis-wise minimum so the tighter axis governs the framing.
    val lngDelta =
        if (clusterLngSpan > 0.0) log2(currentLngSpan * CLUSTER_FIT_RATIO / clusterLngSpan)
        else Double.POSITIVE_INFINITY
    val latDelta =
        if (clusterLatSpan > 0.0) log2(currentLatSpan * CLUSTER_FIT_RATIO / clusterLatSpan)
        else Double.POSITIVE_INFINITY
    if (lngDelta.isInfinite() && latDelta.isInfinite()) {
        Timber.d("zoomToFitCluster: both axis deltas infinite — returning null")
        return null
    }

    val zoomDelta = min(lngDelta, latDelta)
    val currentZoom = cameraState.position.zoom
    val result = (currentZoom + zoomDelta).toFloat()
    Timber.d(
        "zoomToFitCluster: viewport=(lng=%.6f,lat=%.6f) cluster=(lng=%.6f,lat=%.6f) " +
            "lngDelta=%.3f latDelta=%.3f chosenDelta=%.3f currentZoom=%.2f -> %.2f",
        currentLngSpan,
        currentLatSpan,
        clusterLngSpan,
        clusterLatSpan,
        lngDelta,
        latDelta,
        zoomDelta,
        currentZoom,
        result,
    )
    return result
}
