package dev.davwheat.openfuelmap.common.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

enum class ChartStyle {
    /** Horizontal then vertical segments — best for discrete price-change events. */
    STEP,
    /** Direct line between points — best for daily aggregate data. */
    LINE,
}

/**
 * A generic chart composable that can render data as either a step chart or a line chart.
 *
 * @param data Data points to plot. Will be sorted by timestamp internally.
 * @param modifier Modifier applied to the chart canvas.
 * @param chartStyle Whether to draw step (horizontal-then-vertical) or line (diagonal) segments.
 * @param endTimestamp The right edge of the X axis. Defaults to [Instant.now].
 * @param tooltipFormatter Formats a data point for the scrub tooltip.
 * @param valueFormatter Formats a numeric value for Y-axis labels.
 * @param topPadding Space above the chart area, reserved for the scrub tooltip.
 * @param bottomPadding Space below the chart area, reserved for X-axis date labels.
 * @param rightPadding Space to the right of the chart area.
 * @param yAxisGap Horizontal gutter between the Y-axis labels and the chart area.
 */
@Composable
fun StepChart(
    data: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    chartStyle: ChartStyle = ChartStyle.STEP,
    endTimestamp: Instant = Instant.now(),
    tooltipFormatter: (ChartDataPoint) -> String = { point ->
        val dateStr =
            LocalDateTime.ofInstant(point.timestamp, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
        "${point.value}p  $dateStr"
    },
    valueFormatter: (Double) -> String = { "${it.roundToInt()}p" },
    topPadding: Dp = 28.dp,
    bottomPadding: Dp = 24.dp,
    rightPadding: Dp = 8.dp,
    yAxisGap: Dp = 8.dp,
) {
    val textMeasurer = rememberTextMeasurer()
    var scrubX by remember { mutableStateOf<Float?>(null) }

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrubLineColor = MaterialTheme.colorScheme.onSurface
    val scrubBgColor = MaterialTheme.colorScheme.inverseSurface
    val scrubTextColor = MaterialTheme.colorScheme.inverseOnSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val scrubLabelStyle = MaterialTheme.typography.labelSmall.copy(color = scrubTextColor)

    val sorted = remember(data) { data.sortedBy { it.timestamp } }
    if (sorted.isEmpty()) return

    val priceMin = remember(sorted) { sorted.minOf { it.value } }
    val priceMax = remember(sorted) { sorted.maxOf { it.value } }
    val pricePad =
        remember(priceMin, priceMax) {
            if (priceMax - priceMin < 0.1) 1.0 else (priceMax - priceMin) * 0.1
        }
    val yMin = remember(priceMin, pricePad) { floor(priceMin - pricePad).coerceAtLeast(0.0) }
    val yMax = remember(priceMax, pricePad) { ceil(priceMax + pricePad) }
    val gridSteps = remember(yMin, yMax) { computeGridSteps(yMin, yMax) }
    val maxYLabelWidth =
        remember(gridSteps, textMeasurer, labelStyle) {
            gridSteps.maxOfOrNull { price ->
                textMeasurer.measure(valueFormatter(price), labelStyle).size.width
            } ?: 0
        }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            scrubX = it.x
                            val released = tryAwaitRelease()
                            if (released) scrubX = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> scrubX = offset.x },
                        onDragEnd = { scrubX = null },
                        onDragCancel = { scrubX = null },
                        onHorizontalDrag = { change, _ ->
                            scrubX = change.position.x
                            change.consume()
                        },
                    )
                }
    ) {
        val yAxisGapPx = yAxisGap.toPx()
        val chartLeft = maxYLabelWidth + yAxisGapPx
        val chartRight = size.width - rightPadding.toPx()
        val chartTop = topPadding.toPx()
        val chartBottom = size.height - bottomPadding.toPx()
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        val timeMin = sorted.first().timestamp.epochSecond.toFloat()
        val timeMax = endTimestamp.epochSecond.toFloat()
        val timeRange = (timeMax - timeMin).coerceAtLeast(1f)

        val yRange = (yMax - yMin).toFloat().coerceAtLeast(1f)

        fun timeToX(t: Instant): Float =
            chartLeft + (t.epochSecond.toFloat() - timeMin) / timeRange * chartWidth

        fun valueToY(p: Double): Float = chartBottom - ((p - yMin).toFloat() / yRange) * chartHeight

        // Grid lines + Y-axis labels
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (gridPrice in gridSteps) {
            val y = valueToY(gridPrice)
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                pathEffect = dashEffect,
                strokeWidth = 1.dp.toPx(),
            )
            val label = valueFormatter(gridPrice)
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                measured,
                topLeft =
                    Offset(
                        chartLeft - measured.size.width - yAxisGapPx,
                        y - measured.size.height / 2f,
                    ),
            )
        }

        // Path + fill
        val linePath = Path()
        val fillPath = Path()

        val firstX = timeToX(sorted.first().timestamp)
        val firstY = valueToY(sorted.first().value)
        linePath.moveTo(firstX, firstY)
        fillPath.moveTo(firstX, chartBottom)
        fillPath.lineTo(firstX, firstY)

        for (i in 1 until sorted.size) {
            val nextX = timeToX(sorted[i].timestamp)
            val nextY = valueToY(sorted[i].value)
            when (chartStyle) {
                ChartStyle.STEP -> {
                    val prevY = valueToY(sorted[i - 1].value)
                    linePath.lineTo(nextX, prevY)
                    linePath.lineTo(nextX, nextY)
                    fillPath.lineTo(nextX, prevY)
                    fillPath.lineTo(nextX, nextY)
                }
                ChartStyle.LINE -> {
                    linePath.lineTo(nextX, nextY)
                    fillPath.lineTo(nextX, nextY)
                }
            }
        }

        // Extend last value to right edge
        val lastY = valueToY(sorted.last().value)
        linePath.lineTo(chartRight, lastY)
        fillPath.lineTo(chartRight, lastY)
        fillPath.lineTo(chartRight, chartBottom)
        fillPath.close()

        drawPath(fillPath, color = fillColor)
        drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))

        // Data point dots
        for (entry in sorted) {
            val cx = timeToX(entry.timestamp)
            val cy = valueToY(entry.value)
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(cx, cy))
        }

        // X-axis date labels
        drawXAxisLabels(
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
            chartLeft = chartLeft,
            chartRight = chartRight,
            chartBottom = chartBottom,
            timeMin = timeMin,
            timeRange = timeRange,
            chartWidth = chartWidth,
        )

        // Scrub indicator
        val sx = scrubX
        if (sx != null && sx >= chartLeft && sx <= chartRight) {
            drawScrubIndicator(
                scrubX = sx,
                sorted = sorted,
                textMeasurer = textMeasurer,
                scrubLabelStyle = scrubLabelStyle,
                scrubLineColor = scrubLineColor,
                scrubBgColor = scrubBgColor,
                chartLeft = chartLeft,
                chartRight = chartRight,
                chartTop = chartTop,
                chartBottom = chartBottom,
                chartWidth = chartWidth,
                timeMin = timeMin,
                timeRange = timeRange,
                lineColor = lineColor,
                valueToY = ::valueToY,
                tooltipFormatter = tooltipFormatter,
                chartStyle = chartStyle,
            )
        }
    }
}

private fun DrawScope.drawXAxisLabels(
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    chartLeft: Float,
    chartRight: Float,
    chartBottom: Float,
    timeMin: Float,
    timeRange: Float,
    chartWidth: Float,
) {
    val labelCount = 4
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM")
    for (i in 0..labelCount) {
        val fraction = i.toFloat() / labelCount
        val epochSec = (timeMin + fraction * timeRange).toLong()
        val date = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSec), ZoneId.systemDefault())
        val label = date.format(dateFormatter)
        val measured = textMeasurer.measure(label, labelStyle)
        val x = chartLeft + fraction * chartWidth - measured.size.width / 2f
        drawText(
            measured,
            topLeft =
                Offset(
                    x.coerceIn(chartLeft, chartRight - measured.size.width),
                    chartBottom + 4.dp.toPx(),
                ),
        )
    }
}

private fun DrawScope.drawScrubIndicator(
    scrubX: Float,
    sorted: List<ChartDataPoint>,
    textMeasurer: TextMeasurer,
    scrubLabelStyle: TextStyle,
    scrubLineColor: Color,
    scrubBgColor: Color,
    chartLeft: Float,
    chartRight: Float,
    chartTop: Float,
    chartBottom: Float,
    chartWidth: Float,
    timeMin: Float,
    timeRange: Float,
    lineColor: Color,
    valueToY: (Double) -> Float,
    tooltipFormatter: (ChartDataPoint) -> String,
    chartStyle: ChartStyle,
) {
    // Vertical scrub line
    drawLine(
        color = scrubLineColor.copy(alpha = 0.4f),
        start = Offset(scrubX, chartTop),
        end = Offset(scrubX, chartBottom),
        strokeWidth = 1.dp.toPx(),
    )

    // Find active data point at scrub position
    val scrubFraction = (scrubX - chartLeft) / chartWidth
    val scrubEpoch = (timeMin + scrubFraction * timeRange).toLong()
    val scrubInstant = Instant.ofEpochSecond(scrubEpoch)
    val activeEntry =
        when (chartStyle) {
            ChartStyle.STEP -> sorted.lastOrNull { it.timestamp <= scrubInstant } ?: sorted.first()
            ChartStyle.LINE ->
                sorted.minByOrNull { kotlin.math.abs(it.timestamp.epochSecond - scrubEpoch) }
                    ?: sorted.first()
        }

    // Highlight dot
    val dotY = valueToY(activeEntry.value)
    drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(scrubX, dotY))

    // Tooltip
    val tooltipText = tooltipFormatter(activeEntry)
    val measured = textMeasurer.measure(tooltipText, scrubLabelStyle)
    val tooltipPadding = 4.dp.toPx()
    val tooltipWidth = measured.size.width + tooltipPadding * 2
    val tooltipHeight = measured.size.height + tooltipPadding * 2
    val tooltipX = (scrubX - tooltipWidth / 2).coerceIn(chartLeft, chartRight - tooltipWidth)
    val tooltipY = chartTop - tooltipHeight - 2.dp.toPx()

    drawRoundRect(
        color = scrubBgColor,
        topLeft = Offset(tooltipX, tooltipY),
        size = Size(tooltipWidth, tooltipHeight),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    drawText(measured, topLeft = Offset(tooltipX + tooltipPadding, tooltipY + tooltipPadding))
}

private fun computeGridSteps(yMin: Double, yMax: Double): List<Double> {
    val range = yMax - yMin
    val rawStep =
        when {
            range <= 5 -> 1.0
            range <= 15 -> 2.0
            range <= 30 -> 5.0
            else -> 10.0
        }
    val steps = mutableListOf<Double>()
    var v = ceil(yMin / rawStep) * rawStep
    while (v <= yMax) {
        steps.add(v)
        v += rawStep
    }
    return steps
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun StepChartPreview() {
    MaterialExpressiveTheme {
        Surface {
            StepChart(
                data =
                    listOf(
                        ChartDataPoint(
                            value = 142.9,
                            timestamp = Instant.parse("2026-03-01T08:00:00.000Z"),
                        ),
                        ChartDataPoint(
                            value = 143.9,
                            timestamp = Instant.parse("2026-03-08T08:00:00.000Z"),
                        ),
                        ChartDataPoint(
                            value = 141.9,
                            timestamp = Instant.parse("2026-03-15T08:00:00.000Z"),
                        ),
                        ChartDataPoint(
                            value = 144.9,
                            timestamp = Instant.parse("2026-03-22T08:00:00.000Z"),
                        ),
                        ChartDataPoint(
                            value = 143.4,
                            timestamp = Instant.parse("2026-03-29T08:00:00.000Z"),
                        ),
                    ),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
