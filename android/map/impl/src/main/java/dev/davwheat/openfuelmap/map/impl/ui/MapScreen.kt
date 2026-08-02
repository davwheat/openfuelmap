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
package dev.davwheat.openfuelmap.map.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davwheat.openfuelmap.app.api.ProvideTopBar
import dev.davwheat.openfuelmap.common.location.rememberCurrentLocation
import dev.davwheat.openfuelmap.common.location.rememberLocationPermissionState
import dev.davwheat.openfuelmap.common.maps.MapAttributionControl
import dev.davwheat.openfuelmap.common.maps.MapCompass
import dev.davwheat.openfuelmap.common.maps.MapSurface
import dev.davwheat.openfuelmap.common.maps.MapZoomControls
import dev.davwheat.openfuelmap.common.maps.attributionHtml
import dev.davwheat.openfuelmap.common.maps.createSymbolManagerOrNull
import dev.davwheat.openfuelmap.common.maps.destroyIfStyleLoaded
import dev.davwheat.openfuelmap.common.maps.rememberMapStyleUrl
import dev.davwheat.openfuelmap.common.maps.rememberTextureModeMapOptions
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import dev.davwheat.openfuelmap.common.ui.userMessage
import dev.davwheat.openfuelmap.data.repository.SavedCameraPosition
import dev.davwheat.openfuelmap.forecourts.api.model.BoundingBox
import dev.davwheat.openfuelmap.forecourts.impl.detail.ForecourtDetailSheet
import dev.davwheat.openfuelmap.map.impl.R
import dev.davwheat.openfuelmap.map.impl.viewmodel.InitialPosition
import dev.davwheat.openfuelmap.map.impl.viewmodel.MapViewModel
import kotlin.math.log2
import kotlin.math.min
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.plugins.annotation.OnSymbolClickListener
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MapScreenTopAppBar(modifier: Modifier = Modifier) {
    TopAppBar(
        modifier = modifier,
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        title = { Text(stringResource(R.string.map_title)) },
        subtitle = {},
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun MapScreenTopAppBarPreview() {
    MaterialExpressiveTheme { MapScreenTopAppBar() }
}

/** The zoom level for the first move of the camera to the position of the device. */
private const val DEFAULT_ZOOM: Double = 12.0

/** The time in milliseconds of the camera movement after the user touches the recentre button. */
private const val RECENTRE_ANIMATION_MS: Int = 700

/** The camera position to use until a saved position or a device position becomes available. */
private val LONDON = LatLng(51.5074, -0.1278)

/**
 * A full-screen MapLibre map that shows the price markers of the fuel stations. At low zoom levels
 * it puts the markers in clusters. It gets the location permission, keeps the camera position,
 * draws the markers with [PriceMarkerIconCache], and shows a [ForecourtDetailSheet] when the user
 * touches a station.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapScreen(viewModel: MapViewModel) {
    val markers by viewModel.markers.collectAsStateWithLifecycle()
    val selectedStation by viewModel.selectedStation.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isAreaEmpty by viewModel.isAreaEmpty.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFuelType by viewModel.selectedFuelType.collectAsStateWithLifecycle()
    val colorblindMode by viewModel.colorblindMode.collectAsStateWithLifecycle()
    val priceHistory by viewModel.priceHistory.collectAsStateWithLifecycle()
    val priceHistoryLoading by viewModel.priceHistoryLoading.collectAsStateWithLifecycle()
    val fuelTypeNames by viewModel.fuelTypeNames.collectAsStateWithLifecycle()
    val initialPosition by viewModel.initialPosition.collectAsStateWithLifecycle()

    ProvideTopBar { MapScreenTopAppBar() }

    val locationPermission = rememberLocationPermissionState()
    val hasLocationPermission = locationPermission.hasPermission

    LaunchedEffect(Unit) { if (!hasLocationPermission) locationPermission.request() }

    val userLocation by rememberCurrentLocation(hasLocationPermission)

    val priceMarkerIcons = rememberPriceMarkerIconCache(colorblindMode = colorblindMode)

    // MapLibre gives the zoom level on each camera movement, and this code makes it an integer.
    // Thus the clusters change only when the user goes across a zoom level, and not on each pixel
    // of a pinch movement.
    var clusteringZoom by remember { mutableStateOf(DEFAULT_ZOOM.toFloat()) }

    // Below CLUSTER_DISABLED_AT_ZOOM, put the near markers in clusters. Above that zoom level,
    // each station becomes its own Single.
    val clusters by remember { derivedStateOf { clusterMarkers(markers, clusteringZoom) } }

    // Draw the pill and the cluster-badge bitmaps for the current set on a background thread. The
    // cache keeps the bitmaps that it has. Thus a change of the zoom level, or a move back to an
    // area that is already drawn, does no work.
    LaunchedEffect(clusters, priceMarkerIcons) {
        val pillRequests = mutableListOf<PillRequest>()
        val badgeRequests = mutableListOf<Pair<String, Float?>>()
        for (cluster in clusters) {
            when (cluster) {
                is MapCluster.Single -> pillRequests.add(cluster.marker.toPillRequest())
                is MapCluster.Group ->
                    badgeRequests.add(clusterCountLabel(cluster.count) to cluster.minColorPosition)
            }
        }
        priceMarkerIcons.ensure(pillRequests)
        priceMarkerIcons.ensureClusterBadges(badgeRequests)
    }

    // Give each cluster the style image ID of its bitmap, and remove the clusters that have no
    // bitmap. A derivedStateOf does this. Thus the code below reads only the clusters that it can
    // draw, and it does not test for null.
    val readyClusters by
        remember(priceMarkerIcons) {
            derivedStateOf {
                clusters.mapNotNull { cluster ->
                    val imageId =
                        when (cluster) {
                            is MapCluster.Single ->
                                priceMarkerIcons.getOrNull(cluster.marker.toPillRequest())
                            is MapCluster.Group ->
                                priceMarkerIcons.getClusterBadgeOrNull(
                                    clusterCountLabel(cluster.count),
                                    cluster.minColorPosition,
                                )
                        }
                    imageId?.let { cluster to it }
                }
            }
        }

    val styleUrl = rememberMapStyleUrl()
    val userLocationColor = MaterialTheme.colorScheme.primary
    val userLocationBorderColor = MaterialTheme.colorScheme.onPrimary

    // The recentre button is outside the content block of MapSurface. It uses this reference to
    // move the camera.
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    // The credit comes from the style, thus a change of style also changes the credit. The control
    // that shows it is outside the content block, thus the value moves out through this state.
    var attributionHtml by remember { mutableStateOf<String?>(null) }

    // While the lock is on, the camera follows each new position of the device. A movement by the
    // user releases the lock. The value survives a change of configuration, thus a rotation of the
    // screen does not stop the camera from following the user.
    var isLockedOnUserLocation by rememberSaveable { mutableStateOf(false) }

    // The first movement after the lock starts also sets the zoom level, because the map can be at
    // country level. The movements after it keep the zoom level that the user selects.
    var lockNeedsZoom by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapSurface(
            styleUrl = styleUrl,
            modifier = Modifier.fillMaxSize(),
            mapOptions = rememberTextureModeMapOptions(),
        ) { mapView, map, style ->
            DisposableEffect(map) {
                mapRef = map
                onDispose { mapRef = null }
            }

            DisposableEffect(style) {
                attributionHtml = style.attributionHtml()
                onDispose { attributionHtml = null }
            }

            LaunchedEffect(map) {
                // Pitch does not help a price map, and it makes the flat marker pills more
                // difficult to read. Rotation stays available, and [MapCompass] moves the map back
                // to north.
                map.setMinPitchPreference(0.0)
                map.setMaxPitchPreference(0.0)
                // [MapAttributionControl] gives the credit in place of the native widgets, thus
                // the app controls where the credit is and how it looks.
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isLogoEnabled = false
                // [MapCompass] replaces the native compass, thus only one needle is on the map.
                map.uiSettings.isCompassEnabled = false
                // Show a known area while the saved position or the first device position loads.
                // Without this, the map starts at latitude 0, longitude 0, at world zoom.
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LONDON, DEFAULT_ZOOM))
            }

            // This flag records that the first position, from the store or from the device, is
            // applied. Thus the camera does not move again after the user starts to use the map.
            var initialPositionApplied by remember { mutableStateOf(false) }

            LaunchedEffect(map, initialPosition, userLocation) {
                if (initialPositionApplied) return@LaunchedEffect
                val loaded = (initialPosition as? InitialPosition.Loaded) ?: return@LaunchedEffect
                val saved = loaded.position
                val target =
                    when {
                        saved != null ->
                            LatLng(saved.latitude, saved.longitude) to saved.zoom.toDouble()
                        else -> {
                            val loc = userLocation ?: return@LaunchedEffect
                            LatLng(loc.latitude, loc.longitude) to DEFAULT_ZOOM
                        }
                    }
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(target.first, target.second))
                initialPositionApplied = true
            }

            val currentViewModel by rememberUpdatedState(viewModel)
            DisposableEffect(map) {
                clusteringZoom = map.cameraPosition.zoom.toInt().toFloat()
                val moveListener = MapLibreMap.OnCameraMoveListener {
                    clusteringZoom = map.cameraPosition.zoom.toInt().toFloat()
                }
                val idleListener = MapLibreMap.OnCameraIdleListener {
                    val position = map.cameraPosition
                    val target = position.target ?: return@OnCameraIdleListener
                    val bounds = map.projection.visibleRegion.latLngBounds
                    Timber.d("map-idle: bounds=%s zoom=%.2f", bounds, position.zoom)
                    currentViewModel.onCameraIdle(
                        bounds =
                            BoundingBox(
                                swLat = bounds.latitudeSouth,
                                swLng = bounds.longitudeWest,
                                neLat = bounds.latitudeNorth,
                                neLng = bounds.longitudeEast,
                            ),
                        position =
                            SavedCameraPosition(
                                latitude = target.latitude,
                                longitude = target.longitude,
                                zoom = position.zoom.toFloat(),
                            ),
                    )
                }
                // A movement by the user releases the lock, but the camera stays where the user put
                // it. Only a gesture does this: the movements that follow the location come from
                // [MapLibreMap.easeCamera], which reports REASON_API_ANIMATION.
                val moveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        isLockedOnUserLocation = false
                    }
                }
                map.addOnCameraMoveStartedListener(moveStartedListener)
                map.addOnCameraMoveListener(moveListener)
                map.addOnCameraIdleListener(idleListener)
                onDispose {
                    map.removeOnCameraMoveStartedListener(moveStartedListener)
                    map.removeOnCameraMoveListener(moveListener)
                    map.removeOnCameraIdleListener(idleListener)
                }
            }

            // Each symbol ID with its cluster. The click listener below reads this map.
            val symbolTargets = remember(style) { mutableMapOf<Long, MapCluster>() }
            val onSelectStation by rememberUpdatedState(viewModel::selectStation)

            // `iconAllowOverlap` keeps the pill of each station on the screen. The default
            // collision control of MapLibre removes markers in areas with many stations. Users read
            // this as missing data.
            var symbolManager by remember(style) { mutableStateOf<SymbolManager?>(null) }
            DisposableEffect(mapView, map, style) {
                val manager =
                    createSymbolManagerOrNull(mapView, map, style) {
                        iconAllowOverlap = true
                        iconIgnorePlacement = true
                    }
                symbolManager = manager
                val listener = OnSymbolClickListener { symbol ->
                    when (val cluster = symbolTargets[symbol.id]) {
                        is MapCluster.Single -> onSelectStation(cluster.marker.station)
                        is MapCluster.Group -> map.animateCameraToCluster(cluster)
                        null -> Unit
                    }
                    true
                }
                manager?.addClickListener(listener)
                onDispose { manager?.destroyIfStyleLoaded(style) }
            }

            // A symbol can use an image only after the style has that image. Each style has its
            // own image list. Thus a change between the light and the dark style adds all the
            // images again.
            val registeredImages = remember(style) { mutableSetOf<String>() }

            LaunchedEffect(symbolManager, style, readyClusters) {
                val manager = symbolManager ?: return@LaunchedEffect
                priceMarkerIcons.images.forEach { (imageId, bitmap) ->
                    if (registeredImages.add(imageId)) style.addImage(imageId, bitmap)
                }
                symbolTargets.clear()
                manager.deleteAll()
                val options = readyClusters.map { (cluster, imageId) ->
                    val position =
                        when (cluster) {
                            is MapCluster.Single ->
                                LatLng(
                                    cluster.marker.station.latitude,
                                    cluster.marker.station.longitude,
                                )
                            is MapCluster.Group -> LatLng(cluster.centroidLat, cluster.centroidLng)
                        }
                    // zIndex is the negative price, thus a less expensive station gets a higher
                    // sort key. It is drawn above the more expensive stations where pills overlap.
                    val sortKey =
                        when (cluster) {
                            is MapCluster.Single -> cluster.marker.zIndex
                            is MapCluster.Group -> 0f
                        }
                    SymbolOptions()
                        .withLatLng(position)
                        .withIconImage(imageId)
                        .withSymbolSortKey(sortKey)
                }
                manager.create(options).forEachIndexed { index, symbol ->
                    symbolTargets[symbol.id] = readyClusters[index].first
                }
            }

            LaunchedEffect(map, userLocation, isLockedOnUserLocation) {
                if (!isLockedOnUserLocation) return@LaunchedEffect
                val location = userLocation ?: return@LaunchedEffect
                val target = LatLng(location.latitude, location.longitude)
                val update =
                    if (lockNeedsZoom) CameraUpdateFactory.newLatLngZoom(target, DEFAULT_ZOOM)
                    else CameraUpdateFactory.newLatLng(target)
                lockNeedsZoom = false
                map.easeCamera(update, RECENTRE_ANIMATION_MS)
            }

            UserLocationDot(
                mapView = mapView,
                map = map,
                style = style,
                location = userLocation,
                fillColor = userLocationColor,
                borderColor = userLocationBorderColor,
            )
        }

        if (isLoading) {
            ContainedLoadingIndicator(modifier = Modifier.padding(16.dp).align(Alignment.TopCenter))
        }

        if (isAreaEmpty) {
            Surface(
                modifier = Modifier.padding(16.dp).align(Alignment.TopCenter),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                Text(
                    text = stringResource(R.string.map_no_forecourts_in_area),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        Box(Modifier.fillMaxSize().padding(16.dp)) {
            val recentreDescription = stringResource(R.string.map_recenter_on_me)
            Box(Modifier.align(Alignment.TopEnd)) {
                SimpleTooltip(text = recentreDescription) {
                    FilledTonalIconToggleButton(
                        checked = isLockedOnUserLocation,
                        onCheckedChange = { checked ->
                            isLockedOnUserLocation = checked
                            if (checked) {
                                lockNeedsZoom = true
                                // The lock can start before the first position arrives, for example
                                // while the user reads the permission dialog. The effect above then
                                // moves the camera as soon as a position becomes available.
                                if (!hasLocationPermission) locationPermission.request()
                            }
                        },
                        modifier = Modifier.size(IconButtonDefaults.smallContainerSize()),
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        // Concentric arcs while no position is available, and a filled centre after
                        // the first position arrives.
                        Icon(
                            if (userLocation != null) Icons.Rounded.MyLocation
                            else Icons.Rounded.LocationSearching,
                            contentDescription = recentreDescription,
                        )
                    }
                }
            }

            MapCompass(map = mapRef, modifier = Modifier.align(Alignment.TopStart))

            MapZoomControls(map = mapRef, modifier = Modifier.align(Alignment.BottomEnd))

            // `Alignment.End` puts the credit to the end of the button, thus the popup opens away
            // from the edge of the screen.
            MapAttributionControl(
                attributionHtml = attributionHtml,
                modifier = Modifier.align(Alignment.BottomStart),
                textSide = Alignment.End,
            )

            error?.let { failure ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text(failure.userMessage())
                }
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
}

/**
 * The part of the screen that the bounds of the cluster fill after the zoom. A value of 0.78 leaves
 * approximately 11% of free space at each side. The marker pills fill this free space.
 */
private const val CLUSTER_FIT_RATIO: Double = 0.78

/**
 * The maximum zoom level after the user touches a cluster. Without this limit, a cluster with all
 * its stations in one small area goes to street level (18 to 20). Users almost never want this.
 */
private const val CLUSTER_TAP_MAX_ZOOM: Double = 15.0

/** The time in milliseconds of the camera movement into a cluster. */
private const val CLUSTER_ZOOM_ANIMATION_MS: Int = 400

private fun MapLibreMap.animateCameraToCluster(cluster: MapCluster.Group) {
    // Move to the MIDPOINT of the bounds of the cluster, and not to the centroid. In a cluster
    // with 4 near stations and 1 distant station, the centroid is too near the 4 stations. Thus the
    // distant station stays outside the screen.
    val target =
        LatLng((cluster.swLat + cluster.neLat) / 2.0, (cluster.swLng + cluster.neLng) / 2.0)
    val fitted = zoomToFitCluster(cluster)
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
        cameraPosition.zoom,
    )
    animateCamera(CameraUpdateFactory.newLatLngZoom(target, targetZoom), CLUSTER_ZOOM_ANIMATION_MS)
}

/**
 * Calculates the zoom level that makes the bounds of the cluster fill [CLUSTER_FIT_RATIO] of the
 * screen. It compares the degree span of the cluster with the degree span of the visible area at
 * the current zoom level. It then changes that ratio into a zoom difference. Both spans are in
 * degrees at approximately the same latitude. Thus the Mercator distortion has no effect, and a
 * calculation in pixels is not necessary.
 *
 * Gives `null` if the visible area has no size, or if the cluster is only one point. The caller
 * then uses [CLUSTER_TAP_MAX_ZOOM].
 */
private fun MapLibreMap.zoomToFitCluster(cluster: MapCluster.Group): Double? {
    if (cluster.swLat == cluster.neLat && cluster.swLng == cluster.neLng) {
        Timber.d("zoomToFitCluster: degenerate cluster (single point) — returning null")
        return null
    }

    val visible = projection.visibleRegion.latLngBounds
    val currentLngSpan = visible.longitudeEast - visible.longitudeWest
    val currentLatSpan = visible.latitudeNorth - visible.latitudeSouth
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

    // `delta = log2(currentSpan * fitRatio / clusterSpan)`. A positive delta is the number of
    // levels to zoom in. Use the smaller of the two axes, thus the closer axis controls the view.
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
    val result = cameraPosition.zoom + zoomDelta
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
        cameraPosition.zoom,
        result,
    )
    return result
}
