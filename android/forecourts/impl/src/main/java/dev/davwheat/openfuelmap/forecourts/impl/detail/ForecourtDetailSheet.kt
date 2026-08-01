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
package dev.davwheat.openfuelmap.forecourts.impl.detail

import android.content.Context
import android.content.Intent
import android.telephony.PhoneNumberUtils
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.dropUnlessResumed
import com.valentinilk.shimmer.Shimmer
import dev.davwheat.openfuelmap.common.ui.R as CommonUiR
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import dev.davwheat.openfuelmap.common.ui.SkeletonBox
import dev.davwheat.openfuelmap.common.ui.Tag
import dev.davwheat.openfuelmap.common.ui.chart.ChartDataPoint
import dev.davwheat.openfuelmap.common.ui.chart.ChartStyle
import dev.davwheat.openfuelmap.common.ui.chart.StepChart
import dev.davwheat.openfuelmap.common.ui.onWarningContainer
import dev.davwheat.openfuelmap.common.ui.rememberSkeletonShimmer
import dev.davwheat.openfuelmap.common.ui.warning
import dev.davwheat.openfuelmap.common.ui.warningContainer
import dev.davwheat.openfuelmap.forecourts.api.model.BankHolidayHours
import dev.davwheat.openfuelmap.forecourts.api.model.DayHours
import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.forecourts.api.model.FuelPrice
import dev.davwheat.openfuelmap.forecourts.api.model.Location
import dev.davwheat.openfuelmap.forecourts.api.model.OpeningTimes
import dev.davwheat.openfuelmap.forecourts.api.model.PriceHistoryEntry
import dev.davwheat.openfuelmap.forecourts.api.model.PriceInaccuracyReason
import dev.davwheat.openfuelmap.forecourts.impl.R
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Modal bottom sheet showing the details of a selected forecourt: name, brand, address, fuel prices
 * with expandable price-history charts, amenity tags, opening hours, and contact actions. Shows
 * skeleton placeholders while [detail] is still loading.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun ForecourtDetailSheet(
    forecourt: Forecourt,
    detail: ForecourtDetail?,
    fuelTypeNames: Map<String, String>,
    selectedFuelType: String? = null,
    onDismiss: () -> Unit,
    priceHistory: Map<String, List<PriceHistoryEntry>> = emptyMap(),
    priceHistoryLoading: Set<String> = emptySet(),
    onRequestPriceHistory: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val shimmer = rememberSkeletonShimmer()
    var expandedFuelType by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val isSameTradingAndBrandName =
        detail?.isSameTradingAndBrandName
            ?: forecourt.tradingName.equals(forecourt.brandName, ignoreCase = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f).align(Alignment.CenterVertically).semantics(
                            mergeDescendants = true
                        ) {}
                ) {
                    Text(
                        text = forecourt.tradingName.toTitleCase(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (!isSameTradingAndBrandName) {
                        Text(
                            text = forecourt.brandName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SimpleTooltip(stringResource(R.string.detail_navigate_tooltip)) {
                    FilledIconButton(
                        onClick =
                            dropUnlessResumed {
                                val gmmIntentUri =
                                    "geo:${forecourt.latitude},${forecourt.longitude}?q=${forecourt.latitude},${forecourt.longitude}"
                                        .toUri()
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                runCatching { context.startActivity(mapIntent) }
                            },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painterResource(R.drawable.directions_24dp),
                            contentDescription =
                                stringResource(R.string.detail_navigate_description),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val pricesByFuelType =
                remember(detail) { detail?.currentPrices?.associateBy { it.fuelType }.orEmpty() }

            val otherFuelTypes =
                remember(forecourt.fuelTypes, selectedFuelType) {
                    forecourt.fuelTypes.filter { it != selectedFuelType }
                }

            // Selected fuel in a filled container
            if (selectedFuelType != null && selectedFuelType in forecourt.fuelTypes) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    FuelPriceRow(
                        fuelType = selectedFuelType,
                        fuelTypeNames = fuelTypeNames,
                        price = pricesByFuelType[selectedFuelType],
                        detailLoaded = detail != null,
                        shimmer = shimmer,
                        expandedFuelType = expandedFuelType,
                        onToggleExpanded = { checked ->
                            expandedFuelType = if (checked) selectedFuelType else null
                            if (checked) onRequestPriceHistory(selectedFuelType)
                        },
                        priceHistory = priceHistory,
                        priceHistoryLoading = priceHistoryLoading,
                        modifier =
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                .padding(start = 4.dp),
                    )
                }
            }

            // Other fuels in a collapsible section
            if (otherFuelTypes.isNotEmpty()) {
                var otherFuelsExpanded by remember { mutableStateOf(selectedFuelType == null) }

                val otherFuelsHaveInaccuracy =
                    remember(otherFuelTypes, pricesByFuelType) {
                        otherFuelTypes.any { pricesByFuelType[it]?.possiblyInaccurate != null }
                    }

                val expandedLabel = stringResource(R.string.detail_section_expanded)
                val collapsedLabel = stringResource(R.string.detail_section_collapsed)

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                stateDescription =
                                    if (otherFuelsExpanded) expandedLabel else collapsedLabel
                            }
                            .clickable(
                                onClick =
                                    dropUnlessResumed { otherFuelsExpanded = !otherFuelsExpanded }
                            )
                            .padding(start = 32.dp, end = 40.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (selectedFuelType != null) R.string.detail_other_fuels
                                    else R.string.detail_current_prices
                                ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (otherFuelsHaveInaccuracy) {
                            SimpleTooltip(
                                stringResource(R.string.detail_section_inaccuracy_tooltip)
                            ) {
                                Icon(
                                    painter = painterResource(CommonUiR.drawable.warning_20dp),
                                    contentDescription =
                                        stringResource(
                                            R.string.detail_section_inaccuracy_description
                                        ),
                                    tint = MaterialTheme.colorScheme.warning,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    if (selectedFuelType != null) {
                        Icon(
                            imageVector =
                                if (otherFuelsExpanded) Icons.Outlined.KeyboardArrowUp
                                else Icons.Outlined.KeyboardArrowDown,
                            contentDescription =
                                stringResource(
                                    if (otherFuelsExpanded) R.string.detail_section_collapse
                                    else R.string.detail_section_expand
                                ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = otherFuelsExpanded,
                    enter =
                        expandVertically(
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                        ),
                    exit =
                        shrinkVertically(
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                        ),
                ) {
                    Column {
                        otherFuelTypes.forEach { fuelType ->
                            FuelPriceRow(
                                fuelType = fuelType,
                                fuelTypeNames = fuelTypeNames,
                                price = pricesByFuelType[fuelType],
                                detailLoaded = detail != null,
                                shimmer = shimmer,
                                expandedFuelType = expandedFuelType,
                                onToggleExpanded = { checked ->
                                    expandedFuelType = if (checked) fuelType else null
                                    if (checked) onRequestPriceHistory(fuelType)
                                },
                                priceHistory = priceHistory,
                                priceHistoryLoading = priceHistoryLoading,
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                            )
                        }
                    }
                }
            }

            val inaccuracyReasons =
                remember(detail) {
                    detail?.currentPrices?.mapNotNull { it.possiblyInaccurate }?.toSet().orEmpty()
                }
            if (inaccuracyReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.warningContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(CommonUiR.drawable.warning_24dp),
                            contentDescription =
                                stringResource(R.string.detail_warning_description),
                            tint = MaterialTheme.colorScheme.warning,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = inaccuracyBannerText(context, inaccuracyReasons),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onWarningContainer,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
            TextButton(
                onClick = dropUnlessResumed { launchPriceReport(context, toolbarColor) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.detail_report_incorrect_prices))
            }

            detail
                ?.openingTimes
                ?.takeIf { it.usualDays.isNotEmpty() || it.bankHoliday != null }
                ?.let { openingTimes ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.detail_opening_hours),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OpeningHoursList(
                        openingTimes,
                        context = context,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.detail_amenities),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (detail != null) {
                if (detail.amenities.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        detail.amenities.forEach { amenity ->
                            val label =
                                remember(amenity) {
                                    amenity.replace('_', ' ').lowercase().replaceFirstChar {
                                        it.uppercase()
                                    }
                                }
                            Tag(label = label)
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.detail_amenities_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBox(
                        shimmer = shimmer,
                        modifier = Modifier.size(width = 72.dp, height = 28.dp),
                        shape = MaterialTheme.shapes.small,
                    )
                    SkeletonBox(
                        shimmer = shimmer,
                        modifier = Modifier.size(width = 96.dp, height = 28.dp),
                        shape = MaterialTheme.shapes.small,
                    )
                    SkeletonBox(
                        shimmer = shimmer,
                        modifier = Modifier.size(width = 64.dp, height = 28.dp),
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.detail_address),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (detail != null) {
                Text(
                    text =
                        listOfNotNull(
                                detail.location.addressLine1,
                                detail.location.addressLine2,
                                detail.location.city,
                                detail.location.county,
                                detail.location.postcode,
                                detail.location.country,
                            )
                            .joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Street address lines aren't available without detail — shimmer while loading.
                    SkeletonBox(shimmer = shimmer, modifier = Modifier.width(180.dp).height(16.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBox(shimmer = shimmer, modifier = Modifier.width(140.dp).height(16.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    // ...but city & postcode come from the list payload, so show them immediately.
                    Text(
                        text = listOf(forecourt.city, forecourt.postcode).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            detail
                ?.publicPhoneNumber
                ?.takeIf { it.isNotBlank() }
                ?.let { phone ->
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick =
                            dropUnlessResumed {
                                val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                                runCatching { context.startActivity(intent) }
                            },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = formatPhoneNumber(phone))
                    }
                }

            if (forecourt.temporaryClosure) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.detail_temporarily_closed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (forecourt.permanentClosure == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.detail_permanently_closed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            detail?.updatedAt?.let { updatedAt ->
                formatRelativeTime(context, updatedAt)?.let { relative ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.detail_station_updated, relative),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * A single fuel-type row inside the detail sheet. Shows the fuel name and price (or a skeleton
 * while loading), with an expandable [StepChart] of historical prices toggled by the history
 * button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FuelPriceRow(
    fuelType: String,
    fuelTypeNames: Map<String, String>,
    price: FuelPrice?,
    detailLoaded: Boolean,
    shimmer: Shimmer,
    expandedFuelType: String?,
    onToggleExpanded: (Boolean) -> Unit,
    priceHistory: Map<String, List<PriceHistoryEntry>>,
    priceHistoryLoading: Set<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = fuelTypeNames[fuelType] ?: fuelType)
            when {
                price != null ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (price.possiblyInaccurate != null) {
                            SimpleTooltip(stringResource(R.string.fuel_row_inaccurate_tooltip)) {
                                Icon(
                                    painter = painterResource(CommonUiR.drawable.warning_20dp),
                                    contentDescription =
                                        stringResource(R.string.fuel_row_inaccurate_description),
                                    tint = MaterialTheme.colorScheme.warning,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        val context = LocalContext.current
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.fuel_row_price_pence, price.price),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            formatRelativeTime(context, price.priceLastUpdated)?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        SimpleTooltip(stringResource(R.string.fuel_row_history_tooltip)) {
                            FilledTonalIconToggleButton(
                                checked = expandedFuelType == fuelType,
                                onCheckedChange = onToggleExpanded,
                                modifier =
                                    Modifier.minimumInteractiveComponentSize()
                                        .size(IconButtonDefaults.smallContainerSize()),
                                shapes =
                                    IconButtonDefaults.toggleableShapes(
                                        shape = IconButtonDefaults.smallRoundShape,
                                        pressedShape = IconButtonDefaults.smallPressedShape,
                                        checkedShape = IconButtonDefaults.smallSquareShape,
                                    ),
                            ) {
                                Icon(
                                    if (expandedFuelType == fuelType)
                                        painterResource(R.drawable.chart_data_filled_24dp)
                                    else painterResource(R.drawable.chart_data_24dp),
                                    contentDescription =
                                        stringResource(R.string.fuel_row_history_description),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                !detailLoaded ->
                    SkeletonBox(shimmer = shimmer, modifier = Modifier.width(56.dp).height(20.dp))
                else ->
                    Text(
                        text = stringResource(R.string.fuel_row_no_price),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        AnimatedVisibility(
            visible = expandedFuelType == fuelType,
            enter =
                expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            val history = priceHistory[fuelType]
            val isLoading = fuelType in priceHistoryLoading
            when {
                isLoading || history == null ->
                    SkeletonBox(
                        shimmer = shimmer,
                        modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp),
                        shape = MaterialTheme.shapes.small,
                    )
                history.isEmpty() ->
                    Text(
                        text = stringResource(R.string.fuel_row_no_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                else -> {
                    val chartData =
                        remember(history) {
                            history.mapNotNull { entry ->
                                parseInstant(entry.priceChangeEffectiveTimestamp)?.let { ts ->
                                    ChartDataPoint(value = entry.price, timestamp = ts)
                                }
                            }
                        }
                    StepChart(
                        data = chartData,
                        chartStyle = ChartStyle.STEP,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Renders [openingTimes] as a compact list with consecutive days that share the same hours grouped
 * into ranges (e.g. "Mon - Fri: 06:00 - 22:00"). Bank-holiday hours are appended when present.
 */
@Composable
private fun OpeningHoursList(
    openingTimes: OpeningTimes,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val groups = remember(openingTimes) { groupConsecutiveDays(openingTimes.usualDays) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { group ->
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDayRange(group.start, group.end),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatDayHours(context, group.hours),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        openingTimes.bankHoliday?.let { bh ->
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.detail_bank_holidays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        if (bh.is24Hours) stringResource(R.string.detail_open_24_hours)
                        else "${trimSeconds(bh.openTime)} – ${trimSeconds(bh.closeTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class OpeningHoursGroup(val start: DayOfWeek, val end: DayOfWeek, val hours: DayHours)

private fun groupConsecutiveDays(days: Map<DayOfWeek, DayHours>): List<OpeningHoursGroup> {
    val ordered = DayOfWeek.entries.mapNotNull { day -> days[day]?.let { day to it } }
    if (ordered.isEmpty()) return emptyList()
    val groups = mutableListOf<OpeningHoursGroup>()
    var start = ordered[0].first
    var end = start
    var hours = ordered[0].second
    for (i in 1 until ordered.size) {
        val (day, h) = ordered[i]
        if (h == hours && day.ordinal == end.ordinal + 1) {
            end = day
        } else {
            groups += OpeningHoursGroup(start, end, hours)
            start = day
            end = day
            hours = h
        }
    }
    groups += OpeningHoursGroup(start, end, hours)
    return groups
}

private fun formatDayRange(start: DayOfWeek, end: DayOfWeek): String {
    val locale = Locale.getDefault()
    val startName = start.getDisplayName(TextStyle.SHORT, locale)
    if (start == end) return startName
    val endName = end.getDisplayName(TextStyle.SHORT, locale)
    return "$startName – $endName"
}

private fun formatDayHours(context: Context, hours: DayHours): String =
    if (hours.is24Hours) context.getString(R.string.detail_open_24_hours)
    else "${trimSeconds(hours.open)} – ${trimSeconds(hours.close)}"

private fun trimSeconds(time: String): String = time.take(5)

private fun inaccuracyBannerText(context: Context, reasons: Set<PriceInaccuracyReason>): String =
    when {
        reasons.size == 1 ->
            when (reasons.single()) {
                PriceInaccuracyReason.PRICE_TOO_LOW ->
                    context.getString(R.string.detail_inaccuracy_price_too_low)
                PriceInaccuracyReason.STALE_PRICE ->
                    context.getString(R.string.detail_inaccuracy_stale_price)
                PriceInaccuracyReason.UNKNOWN ->
                    context.getString(R.string.detail_inaccuracy_unknown)
            }
        else -> context.getString(R.string.detail_inaccuracy_multiple)
    }

private fun formatPhoneNumber(raw: String): String =
    PhoneNumberUtils.formatNumber(raw, Locale.getDefault().country.ifEmpty { "GB" }) ?: raw

/**
 * Convert an ALL-CAPS or mixed-case string to Title Case. Splits on whitespace and capitalises the
 * first letter of each word; the rest is lowercased. Naive — doesn't special-case abbreviations
 * (e.g. "M&S", "BP") or internal capitals ("McDonalds"). Good enough for forecourt names from the
 * gov.uk feed which are typically uppercase.
 */
private fun String.toTitleCase(): String =
    split(' ').joinToString(" ") { word ->
        if (word.isEmpty()) word
        else
            word.lowercase(Locale.getDefault()).replaceFirstChar {
                it.titlecase(Locale.getDefault())
            }
    }

private const val PRICE_REPORT_URL = "https://www.fuel-finder.service.gov.uk/motorist/price-report"

private fun launchPriceReport(context: Context, toolbarColor: Int) {
    val colorSchemeParams =
        CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build()
    val customTabsIntent =
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setDefaultColorSchemeParams(colorSchemeParams)
            .build()
    runCatching { customTabsIntent.launchUrl(context, PRICE_REPORT_URL.toUri()) }
        .onFailure {
            // Fallback to a regular view intent if no Custom Tabs provider / browser is installed.
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, PRICE_REPORT_URL.toUri()))
            }
        }
}

/**
 * Format an API timestamp as a relative English phrase like "2h ago". Accepts both ISO-8601
 * (`2026-04-03T09:16:27.000Z`) and the SQL-style `2026-04-04 02:33:12` format the API emits.
 * Returns null if the timestamp can't be parsed.
 */
private fun formatRelativeTime(context: Context, raw: String): String? {
    val instant = parseInstant(raw) ?: return null
    val now = Instant.now()
    val duration = Duration.between(instant, now)
    val seconds = duration.seconds
    return when {
        seconds < 0 -> context.getString(R.string.time_just_now)
        seconds < 60 -> context.getString(R.string.time_just_now)
        seconds < 3_600 -> context.getString(R.string.time_minutes_ago, duration.toMinutes())
        seconds < 86_400 -> context.getString(R.string.time_hours_ago, duration.toHours())
        seconds < 604_800 -> context.getString(R.string.time_days_ago, duration.toDays())
        else -> context.getString(R.string.time_weeks_ago, duration.toDays() / 7)
    }
}

private val sqlDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun parseInstant(raw: String): Instant? {
    runCatching {
        return Instant.parse(raw)
    }
    runCatching {
        return LocalDateTime.parse(raw, sqlDateFormatter).toInstant(ZoneOffset.UTC)
    }
    return null
}

private val previewForecourt =
    Forecourt(
        nodeId = "preview-1",
        tradingName = "PETROLMART FILLING STATION",
        brandName = "SHELL",
        postcode = "SW1A 1AA",
        city = "London",
        latitude = 51.5014,
        longitude = -0.1419,
        isMotorwayServiceStation = false,
        isSupermarketServiceStation = false,
        temporaryClosure = false,
        permanentClosure = false,
        fuelTypes = listOf("E10", "E5", "B7_STANDARD"),
        price = null,
    )

private val previewFuelTypeNames =
    mapOf("E10" to "Unleaded", "E5" to "Super Unleaded", "B7_STANDARD" to "Diesel")

private val previewDetail =
    ForecourtDetail(
        nodeId = "preview-1",
        tradingName = "PETROLMART FILLING STATION",
        brandName = "SHELL",
        isSameTradingAndBrandName = false,
        publicPhoneNumber = "02079460000",
        temporaryClosure = false,
        permanentClosure = false,
        permanentClosureDate = null,
        isMotorwayServiceStation = false,
        isSupermarketServiceStation = false,
        location =
            Location(
                addressLine1 = "10 Downing Street",
                addressLine2 = null,
                city = "London",
                country = "United Kingdom",
                county = "Greater London",
                postcode = "SW1A 1AA",
                latitude = 51.5014,
                longitude = -0.1419,
            ),
        amenities = listOf("CAR_WASH", "ATM", "TOILETS", "SHOP"),
        openingTimes =
            OpeningTimes(
                usualDays =
                    DayOfWeek.entries.associateWith {
                        if (it == DayOfWeek.SUNDAY) DayHours("08:00:00", "20:00:00", false)
                        else DayHours("06:00:00", "22:00:00", false)
                    },
                bankHoliday =
                    BankHolidayHours(
                        type = "REDUCED",
                        openTime = "09:00:00",
                        closeTime = "18:00:00",
                        is24Hours = false,
                    ),
            ),
        fuelTypes = listOf("E10", "E5", "B7_STANDARD"),
        updatedAt = "2026-04-05T08:30:00.000Z",
        currentPrices =
            listOf(
                FuelPrice(
                    fuelType = "E10",
                    price = 142.9,
                    priceLastUpdated = "2026-04-05T08:00:00.000Z",
                    priceChangeEffectiveTimestamp = "2026-04-05T08:00:00.000Z",
                ),
                FuelPrice(
                    fuelType = "E5",
                    price = 155.9,
                    priceLastUpdated = "2026-04-05T08:00:00.000Z",
                    priceChangeEffectiveTimestamp = "2026-04-05T08:00:00.000Z",
                ),
                FuelPrice(
                    fuelType = "B7_STANDARD",
                    price = 148.9,
                    priceLastUpdated = "2026-04-05T08:00:00.000Z",
                    priceChangeEffectiveTimestamp = "2026-04-05T08:00:00.000Z",
                ),
            ),
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ForecourtDetailSheetLoadedPreview() {
    MaterialExpressiveTheme {
        Surface {
            ForecourtDetailSheet(
                forecourt = previewForecourt,
                detail = previewDetail,
                fuelTypeNames = previewFuelTypeNames,
                selectedFuelType = "E10",
                onDismiss = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ForecourtDetailSheetLoadingPreview() {
    MaterialExpressiveTheme {
        Surface {
            ForecourtDetailSheet(
                forecourt = previewForecourt,
                detail = null,
                fuelTypeNames = previewFuelTypeNames,
                onDismiss = {},
            )
        }
    }
}
