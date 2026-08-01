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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.davwheat.openfuelmap.app.api.ProvideTopBar
import dev.davwheat.openfuelmap.common.ui.chart.ChartDataPoint
import dev.davwheat.openfuelmap.data.db.FuelTypeIds
import dev.davwheat.openfuelmap.stats.api.model.DailyMedianPrice
import dev.davwheat.openfuelmap.stats.api.model.PriceStat
import dev.davwheat.openfuelmap.stats.impl.R
import dev.davwheat.openfuelmap.stats.impl.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/**
 * National fuel-price statistics screen. Shows a [TimeRangeSelector], a price-stat toggle
 * (mean/median/min/max), and a [MedianPriceCard] per fuel type with the user's preferred fuel type
 * promoted to a tonal surface at the top.
 */
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

    ProvideTopBar {
        TopAppBar(
            titleHorizontalAlignment = Alignment.CenterHorizontally,
            title = { Text(stringResource(R.string.stats_title)) },
            subtitle = {},
        )
    }

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

    val loadingLabel = stringResource(R.string.stats_loading)

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
            val stringMap =
                PriceStat.entries.associateWith {
                    stringResource(
                        when (it) {
                            PriceStat.MEDIAN -> R.string.price_stat_median
                            PriceStat.TRIMMED_MEAN -> R.string.price_stat_trimmed_mean
                        }
                    )
                }

            ButtonGroup(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
            ) {
                PriceStat.entries.forEach { stat ->
                    toggleableItem(
                        checked = stat == priceStat,
                        label = stringMap[stat]!!,
                        onCheckedChange = { if (it) viewModel.setPriceStat(stat) },
                    )
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
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics {
                            contentDescription = loadingLabel
                            liveRegion = LiveRegionMode.Polite
                        }
                )
            }
        }

        if (isLoading && prices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator(
                        modifier =
                            Modifier.semantics {
                                contentDescription = loadingLabel
                                liveRegion = LiveRegionMode.Polite
                            }
                    )
                }
            }
        } else if (error != null && prices.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.stats_failed_to_load),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = dropUnlessResumed { viewModel.retry() }) {
                        Text(stringResource(R.string.stats_retry))
                    }
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
