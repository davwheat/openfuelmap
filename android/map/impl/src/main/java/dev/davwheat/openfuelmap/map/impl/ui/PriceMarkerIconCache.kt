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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dev.davwheat.openfuelmap.common.ui.R as CommonUiR
import dev.davwheat.openfuelmap.map.impl.viewmodel.StationMarker
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

/** One pill to draw. */
internal data class PillRequest(
    val label: String,
    val colorPosition: Float?,
    /** Draws a warning glyph before the label when the price can be incorrect. */
    val showWarning: Boolean,
)

internal fun StationMarker.toPillRequest(): PillRequest =
    PillRequest(label = label, colorPosition = colorPosition, showWarning = isPriceInaccurate)

/** The size the warning glyph rasterises at. The pill scales it down to the height of the label. */
private const val WARNING_GLYPH_PX = 64

/**
 * Number of discrete buckets to quantise `colorPosition` into. Quantising lets the icon cache
 * collapse arbitrarily-close floating-point positions onto a finite set of keys so that the bitmap
 * for two stations one pence apart is generated only once.
 */
private const val COLOR_BUCKETS = 24

/**
 * Makes and keeps the bitmaps for the price pills and the cluster badges. Each bitmap has a stable
 * image ID. The map adds the bitmap with `Style.addImage` and uses that ID in
 * `SymbolOptions.withIconImage`.
 *
 * A [CanvasDrawScope] draws each bitmap on [Dispatchers.Default], with no view hierarchy. The cache
 * keeps each result by `(label, colour bucket)` in a snapshot state map. Thus the main thread does
 * only quick reads, and a new view of the map makes only a small number of bitmaps.
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

    // The warning glyph is a vector drawable, and the pill draws onto a Canvas. Rasterise it one
    // time here, then each pill scales the result to the height of its label.
    val context = LocalContext.current
    val warningGlyph =
        remember(context) {
            ContextCompat.getDrawable(context, CommonUiR.drawable.warning_24dp)
                ?.toBitmap(WARNING_GLYPH_PX, WARNING_GLYPH_PX)
                ?.asImageBitmap()
        }

    return remember(
        density,
        textMeasurer,
        pillTextStyle,
        clusterTextStyle,
        cheapColor,
        expensiveColor,
        warningGlyph,
    ) {
        PriceMarkerIconCache(
            density,
            textMeasurer,
            pillTextStyle,
            clusterTextStyle,
            cheapColor,
            expensiveColor,
            warningGlyph,
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
    private val warningGlyph: ImageBitmap?,
) {
    private val bitmaps = mutableStateMapOf<Key, android.graphics.Bitmap>()
    private val renderMutex = Mutex()

    /**
     * All the bitmaps, with the image ID that the map must use for each one. A read from a
     * composition also gets the new bitmaps.
     */
    val images: Map<String, android.graphics.Bitmap>
        get() = bitmaps.mapKeys { (key, _) -> key.imageId }

    /** The image ID of the pill of this station, or null if the bitmap is not ready. */
    fun getOrNull(request: PillRequest): String? =
        pillKey(request).takeIf { it in bitmaps }?.imageId

    /** The image ID of this cluster badge, or null if the bitmap is not ready. */
    fun getClusterBadgeOrNull(countLabel: String, colorPosition: Float?): String? =
        clusterKey(countLabel, colorPosition).takeIf { it in bitmaps }?.imageId

    /**
     * Render any pills not already in the cache on [Dispatchers.Default]. Completes once every
     * requested combination has a descriptor available via [getOrNull]. Safe to call on every
     * marker list update — already-cached entries are skipped.
     */
    suspend fun ensure(requests: List<PillRequest>) {
        ensureAll(requests.map(::pillKey))
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
        val missing = buildSet { for (key in keys) if (key !in bitmaps) add(key) }
        if (missing.isEmpty()) return
        withContext(Dispatchers.Default) {
            renderMutex.withLock {
                for (key in missing) {
                    yield()
                    if (key in bitmaps) continue
                    val color = colorFor(key.colorBucket)
                    bitmaps[key] =
                        when (key) {
                            is Key.Pill -> renderPillBitmap(key.label, color, key.showWarning)
                            is Key.ClusterBadge -> renderClusterBadgeBitmap(key.countLabel, color)
                        }
                }
            }
        }
    }

    private fun pillKey(request: PillRequest): Key.Pill =
        Key.Pill(request.label, bucketOf(request.colorPosition), request.showWarning)

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

    private fun renderPillBitmap(
        label: String,
        pillColor: Color,
        showWarning: Boolean,
    ): android.graphics.Bitmap {
        val layout = textMeasurer.measure(label, pillTextStyle)
        val paddingH = with(density) { 8.dp.roundToPx() }
        val paddingV = with(density) { 4.dp.roundToPx() }
        val borderPx = with(density) { 1.dp.toPx() }

        // The glyph gets the height of the text, thus it stays in proportion with the label at
        // each font scale.
        val warningSize = if (showWarning) layout.size.height else 0
        val warningGap = if (showWarning) with(density) { 3.dp.roundToPx() } else 0

        val totalWidth = layout.size.width + warningSize + warningGap + paddingH * 2
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
            if (showWarning && warningGlyph != null) {
                // White, in the same way as the label. The pill colour goes from green to red with
                // the price, thus the orange of the list row has too little contrast on some pills.
                drawImage(
                    image = warningGlyph,
                    dstOffset = IntOffset(paddingH, paddingV),
                    dstSize = IntSize(warningSize, warningSize),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
            drawText(
                textLayoutResult = layout,
                topLeft =
                    Offset((paddingH + warningSize + warningGap).toFloat(), paddingV.toFloat()),
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
     * Bitmap.createBitmap(w, h), which ImageBitmap uses, gives the bitmap the default density of
     * 160 dpi. MapLibre calculates the pixel ratio of an image from that density, thus it makes the
     * bitmap larger for the device. But the bitmap is already in device pixels, and the increase in
     * size makes the edges unclear. Set the density of the device, and the renderer draws the
     * bitmap 1:1 in physical pixels.
     */
    private fun stampDeviceDensity(bitmap: android.graphics.Bitmap): android.graphics.Bitmap =
        bitmap.also {
            it.density = (density.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
        }

    private sealed interface Key {
        val colorBucket: Int?

        /** The identifier of this icon in the image list of the map style. */
        val imageId: String

        data class Pill(
            val label: String,
            override val colorBucket: Int?,
            val showWarning: Boolean,
        ) : Key {
            override val imageId: String
                get() = "ofm-pill-$colorBucket-${if (showWarning) "w" else "n"}-$label"
        }

        data class ClusterBadge(val countLabel: String, override val colorBucket: Int?) : Key {
            override val imageId: String
                get() = "ofm-cluster-$colorBucket-$countLabel"
        }
    }
}
