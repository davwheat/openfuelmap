package dev.davwheat.openfuelmap.list.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.common.ui.R as CommonUiR
import dev.davwheat.openfuelmap.common.ui.warning
import dev.davwheat.openfuelmap.data.DistanceUnit
import dev.davwheat.openfuelmap.forecourts.api.model.Forecourt
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtFuelPrice
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtWithDistance
import dev.davwheat.openfuelmap.list.impl.R
import java.util.Locale

/**
 * One row in the list. Shows trading name, brand (if different), city/postcode, distance, and
 * price. Tapping the card selects the station for detail.
 */
@Composable
fun ForecourtListItem(
    item: ForecourtWithDistance,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forecourt = item.forecourt
    val isSameName = forecourt.tradingName.equals(forecourt.brandName, ignoreCase = true)
    val tradingTitle = remember(forecourt.tradingName) { forecourt.tradingName.toTitleCase() }
    val isInaccurate = forecourt.price?.possiblyInaccurate != null

    Surface(
        modifier = modifier.fillMaxWidth().alpha(if (isInaccurate) 0.5f else 1f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!isSameName) {
                    Text(
                        text = forecourt.brandName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = tradingTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text =
                            formatDistance(LocalContext.current, item.distanceMiles, distanceUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            listOfNotNull(
                                    forecourt.city.takeIf { it.isNotBlank() }?.toTitleCase(),
                                    forecourt.postcode,
                                )
                                .joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val price = forecourt.price?.price
                if (price != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isInaccurate) {
                            Icon(
                                painter = painterResource(CommonUiR.drawable.warning_20dp),
                                contentDescription =
                                    stringResource(R.string.list_item_price_inaccurate),
                                tint = MaterialTheme.colorScheme.warning,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.list_item_price_pence, price),
                            style = MaterialTheme.typography.bodyLargeEmphasized,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.list_item_no_price),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun String.toTitleCase(): String =
    split(' ').joinToString(" ") { word ->
        if (word.isEmpty()) word
        else
            word.lowercase(Locale.getDefault()).replaceFirstChar {
                it.titlecase(Locale.getDefault())
            }
    }

private fun previewForecourt(
    tradingName: String = "PETROLMART SERVICE STATION",
    brandName: String = "SHELL",
    priceP: Double? = 142.9,
): Forecourt =
    Forecourt(
        nodeId = "preview",
        tradingName = tradingName,
        brandName = brandName,
        postcode = "SW1A 1AA",
        city = "London",
        latitude = 51.5,
        longitude = -0.14,
        isMotorwayServiceStation = false,
        isSupermarketServiceStation = false,
        temporaryClosure = false,
        permanentClosure = false,
        fuelTypes = listOf("E10"),
        price =
            priceP?.let {
                ForecourtFuelPrice(
                    price = it,
                    priceLastUpdated = "2026-04-05T08:00:00.000Z",
                    priceChangeEffectiveTimestamp = "2026-04-05T08:00:00.000Z",
                )
            },
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ForecourtListItemPreview() {
    MaterialExpressiveTheme {
        Surface {
            ForecourtListItem(
                item = ForecourtWithDistance(forecourt = previewForecourt(), distanceMiles = 0.8),
                distanceUnit = DistanceUnit.MILES,
                onClick = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun ForecourtListItemNoPricePreview() {
    MaterialExpressiveTheme {
        Surface {
            ForecourtListItem(
                item =
                    ForecourtWithDistance(
                        forecourt =
                            previewForecourt(
                                tradingName = "ASDA LONDON ENFIELD",
                                brandName = "ASDA",
                                priceP = null,
                            ),
                        distanceMiles = 4.2,
                    ),
                distanceUnit = DistanceUnit.MILES,
                onClick = {},
            )
        }
    }
}
