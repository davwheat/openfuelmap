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

import android.util.DisplayMetrics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private val DefaultCheapColor = Color(0xFF2E7D32) // green 800
private val DefaultExpensiveColor = Color(0xFFC62828) // red 800
private val ColorblindCheapColor = Color(0xFF1565C0) // blue 800
private val ColorblindExpensiveColor = Color(0xFFEF6C00) // orange 800
private val PriceMissingColor = Color(0xFF757575) // grey 600

/**
 * Number of discrete buckets to quantise `colorPosition` into. Quantising lets the icon cache
 * collapse arbitrarily-close floating-point positions onto a finite set of keys so that the bitmap
 * for two stations one pence apart is generated only once.
 */
private const val COLOR_BUCKETS = 24

/**
 * Builds and caches [BitmapDescriptor]s for the price-pill and cluster-badge marker icons.
 *
 * Previously the map used `MarkerComposable` which — per marker, on the main thread — spins up a
 * `ComposeView`, runs measure/layout/draw, and captures the result to a bitmap. That was the source
 * of significant jank when the viewport's station set changed (every marker has a distinct `nodeId`
 * key so nothing was ever cached).
 *
 * This cache renders icons directly via [CanvasDrawScope] (no view hierarchy) on
 * [Dispatchers.Default], memoises the result by `(label, quantised colour bucket)`, and exposes
 * them via a snapshot state map so the main thread only ever does cheap lookups. A fresh viewport
 * only generates a handful of unique bitmaps regardless of how many stations are visible.
 */
@Composable
internal fun rememberPriceMarkerIconCache(colorblindMode: Boolean = false): PriceMarkerIconCache {
    val cheapColor = if (colorblindMode) ColorblindCheapColor else DefaultCheapColor
    val expensiveColor = if (colorblindMode) ColorblindExpensiveColor else DefaultExpensiveColor
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val pillTextStyle =
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    val clusterTextStyle =
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
    return remember(
        density,
        textMeasurer,
        pillTextStyle,
        clusterTextStyle,
        cheapColor,
        expensiveColor,
    ) {
        PriceMarkerIconCache(
            density,
            textMeasurer,
            pillTextStyle,
            clusterTextStyle,
            cheapColor,
            expensiveColor,
        )
    }
}

internal class PriceMarkerIconCache(
    private val density: Density,
    private val textMeasurer: TextMeasurer,
    private val pillTextStyle: TextStyle,
    private val clusterTextStyle: TextStyle,
    private val cheapColor: Color,
    private val expensiveColor: Color,
) {
    private val descriptors = mutableStateMapOf<Key, BitmapDescriptor>()
    private val renderMutex = Mutex()

    /** Returns the cached pill descriptor for this station, or null if it's not yet rendered. */
    fun getOrNull(label: String, colorPosition: Float?): BitmapDescriptor? =
        descriptors[pillKey(label, colorPosition)]

    /** Returns the cached cluster badge descriptor, or null if it's not yet rendered. */
    fun getClusterBadgeOrNull(countLabel: String, colorPosition: Float?): BitmapDescriptor? =
        descriptors[clusterKey(countLabel, colorPosition)]

    /**
     * Render any pills not already in the cache on [Dispatchers.Default]. Completes once every
     * requested combination has a descriptor available via [getOrNull]. Safe to call on every
     * marker list update — already-cached entries are skipped.
     */
    suspend fun ensure(requests: List<Pair<String, Float?>>) {
        ensureAll(requests.map { (label, cp) -> pillKey(label, cp) })
    }

    /**
     * Render any cluster badges not already in the cache on [Dispatchers.Default]. Same semantics
     * as [ensure].
     */
    suspend fun ensureClusterBadges(requests: List<Pair<String, Float?>>) {
        ensureAll(requests.map { (countLabel, cp) -> clusterKey(countLabel, cp) })
    }

    private suspend fun ensureAll(keys: List<Key>) {
        if (keys.isEmpty()) return
        val missing = buildSet { for (key in keys) if (key !in descriptors) add(key) }
        if (missing.isEmpty()) return
        withContext(Dispatchers.Default) {
            renderMutex.withLock {
                for (key in missing) {
                    yield()
                    if (key in descriptors) continue
                    val color = colorFor(key.colorBucket)
                    val bitmap =
                        when (key) {
                            is Key.Pill -> renderPillBitmap(key.label, color)
                            is Key.ClusterBadge -> renderClusterBadgeBitmap(key.countLabel, color)
                        }
                    descriptors[key] = BitmapDescriptorFactory.fromBitmap(bitmap)
                }
            }
        }
    }

    private fun pillKey(label: String, colorPosition: Float?): Key.Pill =
        Key.Pill(label, bucketOf(colorPosition))

    private fun clusterKey(countLabel: String, colorPosition: Float?): Key.ClusterBadge =
        Key.ClusterBadge(countLabel, bucketOf(colorPosition))

    private fun bucketOf(colorPosition: Float?): Int? = colorPosition?.let {
        (it.coerceIn(0f, 1f) * COLOR_BUCKETS).roundToInt()
    }

    private fun colorFor(bucket: Int?): Color =
        if (bucket != null) {
            lerp(cheapColor, expensiveColor, bucket.toFloat() / COLOR_BUCKETS)
        } else {
            PriceMissingColor
        }

    private fun renderPillBitmap(label: String, pillColor: Color): android.graphics.Bitmap {
        val layout = textMeasurer.measure(label, pillTextStyle)
        val paddingH = with(density) { 8.dp.roundToPx() }
        val paddingV = with(density) { 4.dp.roundToPx() }
        val borderPx = with(density) { 1.dp.toPx() }

        val totalWidth = layout.size.width + paddingH * 2
        val totalHeight = layout.size.height + paddingV * 2

        val imageBitmap = ImageBitmap(totalWidth, totalHeight, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(imageBitmap)
        val size = Size(totalWidth.toFloat(), totalHeight.toFloat())
        val radius = CornerRadius(size.height / 2f)

        // Inset the stroke by half its width so its outer edge aligns with the bitmap bounds
        // instead of spilling outside and getting clipped.
        val halfBorder = borderPx / 2f
        val strokeSize = Size(size.width - borderPx, size.height - borderPx)
        val strokeRadius = CornerRadius(strokeSize.height / 2f)

        CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) {
            drawRoundRect(color = pillColor, size = size, cornerRadius = radius)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(halfBorder, halfBorder),
                size = strokeSize,
                cornerRadius = strokeRadius,
                style = Stroke(width = borderPx),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(paddingH.toFloat(), paddingV.toFloat()),
            )
        }

        return stampDeviceDensity(imageBitmap.asAndroidBitmap())
    }

    private fun renderClusterBadgeBitmap(
        countLabel: String,
        badgeColor: Color,
    ): android.graphics.Bitmap {
        val layout = textMeasurer.measure(countLabel, clusterTextStyle)
        val minDiameter = with(density) { 36.dp.roundToPx() }
        val padding = with(density) { 10.dp.roundToPx() }
        val borderPx = with(density) { 1.5.dp.toPx() }

        // Diameter is large enough to contain the text plus padding on every side. Using the text's
        // longest dimension keeps two- and three-character counts sitting comfortably inside the
        // circle.
        val contentSpan = max(layout.size.width, layout.size.height)
        val diameter = max(minDiameter, contentSpan + padding * 2)

        val imageBitmap = ImageBitmap(diameter, diameter, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(imageBitmap)
        val size = Size(diameter.toFloat(), diameter.toFloat())
        val center = Offset(size.width / 2f, size.height / 2f)

        val halfBorder = borderPx / 2f
        val fillRadius = size.width / 2f
        val strokeRadius = fillRadius - halfBorder

        val textTopLeft =
            Offset((diameter - layout.size.width) / 2f, (diameter - layout.size.height) / 2f)

        CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) {
            drawCircle(color = badgeColor, radius = fillRadius, center = center)
            drawCircle(
                color = Color.White,
                radius = strokeRadius,
                center = center,
                style = Stroke(width = borderPx),
            )
            drawText(textLayoutResult = layout, topLeft = textTopLeft)
        }

        return stampDeviceDensity(imageBitmap.asAndroidBitmap())
    }

    /**
     * Bitmap.createBitmap(w, h) (used internally by ImageBitmap) leaves the bitmap's density at the
     * default 160dpi. Google Maps interprets that as an mdpi-authored icon and upscales it to match
     * the device density — but the bitmap is *already* in device pixels, so the extra scaling
     * produces blurry edges. Stamping the device density makes the SDK draw the bitmap 1:1 in
     * physical pixels.
     */
    private fun stampDeviceDensity(bitmap: android.graphics.Bitmap): android.graphics.Bitmap =
        bitmap.also {
            it.density = (density.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
        }

    private sealed interface Key {
        val colorBucket: Int?

        data class Pill(val label: String, override val colorBucket: Int?) : Key

        data class ClusterBadge(val countLabel: String, override val colorBucket: Int?) : Key
    }
}
