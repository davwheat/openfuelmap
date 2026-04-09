package dev.davwheat.openfuelmap.list.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.data.DistanceUnit
import dev.davwheat.openfuelmap.list.impl.R

/** Discriminated empty-state for the List screen. The four reasons use different copy and CTAs. */
sealed interface ListEmptyReason {
    /** No fix yet, and no custom pin. Offer to request permission or pick a location. */
    data object NoLocation : ListEmptyReason

    /** Radius yielded zero results for the current filters. */
    data class NoneInRadius(val radiusMi: Float, val distanceUnit: DistanceUnit) : ListEmptyReason

    /** API call failed. */
    data class Error(val message: String) : ListEmptyReason
}

/**
 * Full-width empty state for the list screen. Renders a different illustration, message, and set of
 * CTAs depending on the [reason] (no location, no results in radius, or API error).
 */
@Composable
fun ListEmptyState(
    reason: ListEmptyReason,
    onRequestLocation: () -> Unit,
    onPickCustomLocation: () -> Unit,
    onIncreaseRadius: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (reason) {
            ListEmptyReason.NoLocation -> {
                EmptyIllustration(icon = Icons.Outlined.LocationOff)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.empty_no_location_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.empty_no_location_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestLocation) {
                        Text(stringResource(R.string.empty_use_my_location))
                    }
                    OutlinedButton(onClick = onPickCustomLocation) {
                        Text(stringResource(R.string.empty_pick_a_location))
                    }
                }
            }
            is ListEmptyReason.NoneInRadius -> {
                val displayRadius =
                    when (reason.distanceUnit) {
                        DistanceUnit.MILES -> reason.radiusMi
                        DistanceUnit.KILOMETERS ->
                            (reason.radiusMi * DistanceUnit.KM_PER_MILE).toFloat()
                    }
                val titleRes =
                    when (reason.distanceUnit) {
                        DistanceUnit.MILES -> R.string.empty_none_in_radius_title_mi
                        DistanceUnit.KILOMETERS -> R.string.empty_none_in_radius_title_km
                    }
                EmptyIllustration(icon = Icons.Outlined.SearchOff)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(titleRes, formatRadius(displayRadius)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.empty_none_in_radius_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                val maxStop = radiusStopsFor(reason.distanceUnit).last()
                if (reason.radiusMi < maxStop) {
                    Button(onClick = onIncreaseRadius) {
                        Text(stringResource(R.string.empty_increase_radius))
                    }
                }
            }
            is ListEmptyReason.Error -> {
                EmptyIllustration(icon = Icons.Outlined.ErrorOutline)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.empty_error_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reason.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.empty_try_again)) }
            }
        }
    }
}

@Composable
private fun EmptyIllustration(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(56.dp).width(56.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ListEmptyStateNoLocationPreview() {
    MaterialExpressiveTheme {
        Surface {
            ListEmptyState(
                reason = ListEmptyReason.NoLocation,
                onRequestLocation = {},
                onPickCustomLocation = {},
                onIncreaseRadius = {},
                onRetry = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ListEmptyStateNoneInRadiusPreview() {
    MaterialExpressiveTheme {
        Surface {
            ListEmptyState(
                reason =
                    ListEmptyReason.NoneInRadius(radiusMi = 5f, distanceUnit = DistanceUnit.MILES),
                onRequestLocation = {},
                onPickCustomLocation = {},
                onIncreaseRadius = {},
                onRetry = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ListEmptyStateErrorPreview() {
    MaterialExpressiveTheme {
        Surface {
            ListEmptyState(
                reason = ListEmptyReason.Error(message = "Couldn't reach the server."),
                onRequestLocation = {},
                onPickCustomLocation = {},
                onIncreaseRadius = {},
                onRetry = {},
            )
        }
    }
}
