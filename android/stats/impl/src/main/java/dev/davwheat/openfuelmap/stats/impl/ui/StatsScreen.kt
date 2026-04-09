package dev.davwheat.openfuelmap.stats.impl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davwheat.openfuelmap.app.api.ProvideTopBar
import dev.davwheat.openfuelmap.common.ui.chart.ChartDataPoint
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.stats.api.model.DailyMedianPrice
import dev.davwheat.openfuelmap.stats.api.model.PriceStat
import dev.davwheat.openfuelmap.stats.impl.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val timeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val priceStat by viewModel.priceStat.collectAsStateWithLifecycle()
    val prices by viewModel.prices.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFuelType by viewModel.selectedFuelType.collectAsStateWithLifecycle()
    val fuelTypeNames by viewModel.fuelTypeNames.collectAsStateWithLifecycle()
    val fuelTypes by viewModel.fuelTypes.collectAsStateWithLifecycle()

    var showLoadingBar by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(300)
            showLoadingBar = true
        } else {
            showLoadingBar = false
        }
    }

    ProvideTopBar { TopAppBar(title = { Text("Stats") }) }

    val orderedFuelTypes =
        remember(prices, selectedFuelType, fuelTypes) {
            val availableTypes = prices.keys
            availableTypes.sortedWith(
                compareBy { typeId ->
                    val idx = FuelTypeIds.PRIORITY_ORDER.indexOf(typeId)
                    if (idx >= 0) idx else FuelTypeIds.PRIORITY_ORDER.size
                }
            )
        }

    val primaryFuelType = selectedFuelType?.takeIf { it in prices }
    val otherFuelTypes =
        remember(orderedFuelTypes, primaryFuelType) {
            orderedFuelTypes.filter { it != primaryFuelType }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            TimeRangeSelector(
                selectedRange = timeRange,
                onRangeSelected = viewModel::setTimeRange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }

        item {
            ButtonGroup(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                PriceStat.entries.forEach { stat ->
                    val checked = stat == priceStat
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { if (it) viewModel.setPriceStat(stat) },
                    ) {
                        Text(stat.label)
                    }
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = showLoadingBar && prices.isNotEmpty(),
                enter =
                    expandVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
                exit =
                    shrinkVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }

        if (isLoading && prices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            }
        } else if (error != null && prices.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "Failed to load prices", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::retry) { Text("Retry") }
                }
            }
        } else {
            if (primaryFuelType != null) {
                item(key = "primary_$primaryFuelType") {
                    val fuelPrices = prices[primaryFuelType].orEmpty()
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        MedianPriceCard(
                            fuelTypeName = fuelTypeNames[primaryFuelType] ?: primaryFuelType,
                            latestPrice = fuelPrices.lastOrNull()?.price,
                            chartData = fuelPrices.toChartData(),
                        )
                    }
                }
            }

            items(otherFuelTypes, key = { "other_$it" }) { fuelType ->
                val fuelPrices = prices[fuelType].orEmpty()
                MedianPriceCard(
                    fuelTypeName = fuelTypeNames[fuelType] ?: fuelType,
                    latestPrice = fuelPrices.lastOrNull()?.price,
                    chartData = fuelPrices.toChartData(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

private fun List<DailyMedianPrice>.toChartData(): List<ChartDataPoint> = map { entry ->
    ChartDataPoint(
        value = entry.price,
        timestamp = LocalDate.parse(entry.date).atStartOfDay().toInstant(ZoneOffset.UTC),
    )
}
