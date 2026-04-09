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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Discriminated empty-state for the List screen. The four reasons use different copy and CTAs. */
sealed interface ListEmptyReason {
    /** No fix yet, and no custom pin. Offer to request permission or pick a location. */
    data object NoLocation : ListEmptyReason

    /** Radius yielded zero results for the current filters. */
    data class NoneInRadius(val radiusMi: Float) : ListEmptyReason

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
                Text(text = "We need a location", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Share your location to see nearby stations, or pick a spot on a map.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestLocation) { Text("Use my location") }
                    OutlinedButton(onClick = onPickCustomLocation) { Text("Pick a location") }
                }
            }
            is ListEmptyReason.NoneInRadius -> {
                EmptyIllustration(icon = Icons.Outlined.SearchOff)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No stations within ${formatRadiusMi(reason.radiusMi)} mi",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Try widening the search radius or showing more brands.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (reason.radiusMi < 50f) {
                    Button(onClick = onIncreaseRadius) { Text("Increase radius") }
                }
            }
            is ListEmptyReason.Error -> {
                EmptyIllustration(icon = Icons.Outlined.ErrorOutline)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Couldn't load stations", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reason.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Try again") }
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
                reason = ListEmptyReason.NoneInRadius(radiusMi = 5f),
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
