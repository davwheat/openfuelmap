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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private val PriceCheapColor = Color(0xFF2E7D32) // green 800
private val PriceExpensiveColor = Color(0xFFC62828) // red 800
private val PriceMissingColor = Color(0xFF757575) // grey 600

/**
 * Number of discrete buckets to quantise `colorPosition` into. Quantising lets the icon cache
 * collapse arbitrarily-close floating-point positions onto a finite set of keys so that the bitmap
 * for two stations one pence apart is generated only once.
 */
private const val COLOR_BUCKETS = 24

/**
 * Builds and caches [BitmapDescriptor]s for the price-pill marker icon.
 *
 * Previously the map used `MarkerComposable` which — per marker, on the main thread — spins up a
 * `ComposeView`, runs measure/layout/draw, and captures the result to a bitmap. That was the source
 * of significant jank when the viewport's station set changed (every marker has a distinct `nodeId`
 * key so nothing was ever cached).
 *
 * This cache renders the pill directly via [CanvasDrawScope] (no view hierarchy) on
 * [Dispatchers.Default], memoises the result by `(label, quantised colour bucket)`, and exposes it
 * via a snapshot state map so the main thread only ever does cheap lookups. A fresh viewport only
 * generates a handful of unique bitmaps regardless of how many stations are visible.
 */
@Composable
internal fun rememberPriceMarkerIconCache(): PriceMarkerIconCache {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val textStyle =
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    return remember(density, textMeasurer, textStyle) {
        PriceMarkerIconCache(density, textMeasurer, textStyle)
    }
}

internal class PriceMarkerIconCache(
    private val density: Density,
    private val textMeasurer: TextMeasurer,
    private val textStyle: TextStyle,
) {
    private val descriptors = mutableStateMapOf<Key, BitmapDescriptor>()
    private val renderMutex = Mutex()

    /** Returns the cached descriptor for this station, or null if it's not yet rendered. */
    fun getOrNull(label: String, colorPosition: Float?): BitmapDescriptor? =
        descriptors[keyFor(label, colorPosition)]

    /**
     * Render any pills not already in the cache on [Dispatchers.Default]. Completes once every
     * requested combination has a descriptor available via [getOrNull]. Safe to call on every
     * marker list update — already-cached entries are skipped.
     */
    suspend fun ensure(requests: List<Pair<String, Float?>>) {
        if (requests.isEmpty()) return
        val missing = buildSet {
            for ((label, colorPosition) in requests) {
                val key = keyFor(label, colorPosition)
                if (key !in descriptors) add(key)
            }
        }
        if (missing.isEmpty()) return
        withContext(Dispatchers.Default) {
            renderMutex.withLock {
                for (key in missing) {
                    yield()
                    if (key in descriptors) continue
                    val bitmap = renderBitmap(key.label, colorFor(key.colorBucket))
                    descriptors[key] = BitmapDescriptorFactory.fromBitmap(bitmap)
                }
            }
        }
    }

    private fun keyFor(label: String, colorPosition: Float?): Key {
        val bucket = colorPosition?.let { (it.coerceIn(0f, 1f) * COLOR_BUCKETS).roundToInt() }
        return Key(label, bucket)
    }

    private fun colorFor(bucket: Int?): Color =
        if (bucket != null) {
            lerp(PriceCheapColor, PriceExpensiveColor, bucket.toFloat() / COLOR_BUCKETS)
        } else {
            PriceMissingColor
        }

    private fun renderBitmap(label: String, pillColor: Color): android.graphics.Bitmap {
        val layout = textMeasurer.measure(label, textStyle)
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

        // Bitmap.createBitmap(w, h) (used internally by ImageBitmap) leaves the bitmap's density
        // at the default 160dpi. Google Maps interprets that as an mdpi-authored icon and
        // upscales it to match the device density — but the bitmap is *already* in device pixels,
        // so the extra scaling produces blurry edges. Stamping the device density makes the SDK
        // draw the bitmap 1:1 in physical pixels.
        return imageBitmap.asAndroidBitmap().also {
            it.density = (density.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
        }
    }

    private data class Key(val label: String, val colorBucket: Int?)
}
