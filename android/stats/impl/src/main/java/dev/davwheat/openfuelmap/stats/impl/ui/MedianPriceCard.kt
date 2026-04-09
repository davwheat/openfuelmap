package dev.davwheat.openfuelmap.stats.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.common.ui.chart.ChartDataPoint
import dev.davwheat.openfuelmap.common.ui.chart.ChartStyle
import dev.davwheat.openfuelmap.common.ui.chart.StepChart
import dev.davwheat.openfuelmap.stats.impl.R
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Card displaying a fuel type's name, latest median price, and a [StepChart] of daily median prices
 * over the selected time range.
 */
@Composable
fun MedianPriceCard(
    fuelTypeName: String,
    latestPrice: Double?,
    chartData: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = fuelTypeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (latestPrice != null) {
                Text(
                    text = stringResource(R.string.stats_price_pence, latestPrice),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (chartData.isNotEmpty()) {
            val tooltipFormat = stringResource(R.string.stats_tooltip_format)
            StepChart(
                data = chartData,
                chartStyle = ChartStyle.LINE,
                endTimestamp = chartData.last().timestamp,
                tooltipFormatter = { point ->
                    val dateStr =
                        LocalDateTime.ofInstant(point.timestamp, ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                    tooltipFormat.format(point.value, dateStr)
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
