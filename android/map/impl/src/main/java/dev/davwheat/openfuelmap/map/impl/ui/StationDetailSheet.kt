package dev.davwheat.openfuelmap.map.impl.ui

import android.content.Intent
import android.telephony.PhoneNumberUtils
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.davwheat.openfuelmap.common.ui.SkeletonBox
import dev.davwheat.openfuelmap.common.ui.Tag
import dev.davwheat.openfuelmap.common.ui.rememberSkeletonShimmer
import dev.davwheat.openfuelmap.map.api.model.DayHours
import dev.davwheat.openfuelmap.map.api.model.Forecourt
import dev.davwheat.openfuelmap.map.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.map.api.model.OpeningTimes
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationDetailSheet(
    forecourt: Forecourt,
    detail: ForecourtDetail?,
    fuelTypeNames: Map<String, String>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val shimmer = rememberSkeletonShimmer()
    val context = LocalContext.current

    val isSameTradingAndBrandName =
        detail?.isSameTradingAndBrandName
            ?: forecourt.tradingName.equals(forecourt.brandName, ignoreCase = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = forecourt.tradingName, style = MaterialTheme.typography.headlineSmall)
            if (!isSameTradingAndBrandName) {
                Text(
                    text = forecourt.brandName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                )
            } else {
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

            detail
                ?.publicPhoneNumber
                ?.takeIf { it.isNotBlank() }
                ?.let { phone ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                                    runCatching { context.startActivity(intent) }
                                }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatPhoneNumber(phone),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Current Prices", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val pricesByFuelType =
                remember(detail) { detail?.currentPrices?.associateBy { it.fuelType }.orEmpty() }

            forecourt.fuelTypes.forEach { fuelType ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = fuelTypeNames[fuelType] ?: fuelType)
                    val price = pricesByFuelType[fuelType]
                    when {
                        price != null ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${price.price}p",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                formatRelativeTime(price.priceLastUpdated)?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        detail == null ->
                            SkeletonBox(
                                shimmer = shimmer,
                                modifier = Modifier.width(56.dp).height(20.dp),
                            )
                        else ->
                            Text(
                                text = "No price",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                }
            }

            detail
                ?.openingTimes
                ?.takeIf { it.usualDays.isNotEmpty() || it.bankHoliday != null }
                ?.let { openingTimes ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Opening hours", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OpeningHoursList(openingTimes)
                }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Amenities", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (detail != null) {
                if (detail.amenities.isNotEmpty()) {
                    FlowRow(
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
                        text = "None listed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FlowRow(
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

            if (forecourt.temporaryClosure) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Temporarily closed", color = MaterialTheme.colorScheme.error)
            }
            if (forecourt.permanentClosure == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Permanently closed", color = MaterialTheme.colorScheme.error)
            }

            detail?.updatedAt?.let { updatedAt ->
                formatRelativeTime(updatedAt)?.let { relative ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Station details updated $relative",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OpeningHoursList(openingTimes: OpeningTimes) {
    val groups = remember(openingTimes) { groupConsecutiveDays(openingTimes.usualDays) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { group ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDayRange(group.start, group.end),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatDayHours(group.hours),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        openingTimes.bankHoliday?.let { bh ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Bank holidays",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        if (bh.is24Hours) "Open 24 hours"
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

private fun formatDayHours(hours: DayHours): String =
    if (hours.is24Hours) "Open 24 hours"
    else "${trimSeconds(hours.open)} – ${trimSeconds(hours.close)}"

private fun trimSeconds(time: String): String = time.take(5)

private fun formatPhoneNumber(raw: String): String =
    PhoneNumberUtils.formatNumber(raw, Locale.getDefault().country.ifEmpty { "GB" }) ?: raw

/**
 * Format an API timestamp as a relative English phrase like "2h ago". Accepts both ISO-8601
 * (`2026-04-03T09:16:27.000Z`) and the SQL-style `2026-04-04 02:33:12` format the API emits.
 * Returns null if the timestamp can't be parsed.
 */
private fun formatRelativeTime(raw: String): String? {
    val instant = parseInstant(raw) ?: return null
    val now = Instant.now()
    val duration = Duration.between(instant, now)
    val seconds = duration.seconds
    return when {
        seconds < 0 -> "just now"
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${duration.toMinutes()}m ago"
        seconds < 86_400 -> "${duration.toHours()}h ago"
        seconds < 604_800 -> "${duration.toDays()}d ago"
        else -> "${duration.toDays() / 7}w ago"
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
