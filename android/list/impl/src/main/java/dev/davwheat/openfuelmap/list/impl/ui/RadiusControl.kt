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
package dev.davwheat.openfuelmap.list.impl.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.data.DistanceUnit
import dev.davwheat.openfuelmap.list.impl.R

/**
 * Non-linear list of search radii (miles). Better resolution at short distances — which is where
 * most users will live — with two larger steps at the top of the scale for the occasional road
 * trip. Maxes out at 50 miles per product decision.
 */
internal val RADIUS_STOPS_MI: List<Float> = listOf(1f, 2f, 3f, 5f, 10f, 15f, 20f, 30f, 50f)

/**
 * Equivalent stops for kilometres. Nice round numbers that roughly match the mile stops in spirit.
 */
internal val RADIUS_STOPS_KM: List<Float> = listOf(1f, 3f, 5f, 10f, 15f, 25f, 40f, 50f, 80f)

internal fun radiusStopsFor(unit: DistanceUnit): List<Float> =
    when (unit) {
        DistanceUnit.MILES -> RADIUS_STOPS_MI
        DistanceUnit.KILOMETERS -> RADIUS_STOPS_KM
    }

/**
 * Slider with discrete non-linear stops. The slider is driven in "stop index" space (0..N-1) and we
 * translate to/from the display unit for the caller. [radiusMi] is always in miles; when [unit] is
 * km we convert for display and snap to [RADIUS_STOPS_KM], converting back to miles for the
 * callback.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun RadiusSlider(
    radiusMi: Float,
    onRadiusChanged: (Float) -> Unit,
    unit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    val stops = radiusStopsFor(unit)
    val displayValue =
        when (unit) {
            DistanceUnit.MILES -> radiusMi
            DistanceUnit.KILOMETERS -> (radiusMi * DistanceUnit.KM_PER_MILE).toFloat()
        }
    val currentIndex =
        remember(displayValue, unit) {
            stops.indexOfFirst { it >= displayValue }.let { if (it < 0) stops.lastIndex else it }
        }

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.radius_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                displayValue,
                transitionSpec = {
                    // Slide up/down depending on value change
                    if (targetState > initialState) {
                        slideInVertically(motionScheme.defaultSpatialSpec()) { height -> height } +
                            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                            slideOutVertically(motionScheme.defaultSpatialSpec()) { height ->
                                -height
                            } + fadeOut(motionScheme.defaultEffectsSpec()) using
                            SizeTransform(clip = false)
                    } else {
                        slideInVertically(motionScheme.defaultSpatialSpec()) { height -> -height } +
                            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                            slideOutVertically(motionScheme.defaultSpatialSpec()) { height ->
                                height
                            } + fadeOut(motionScheme.defaultEffectsSpec()) using
                            SizeTransform(clip = false)
                    }
                },
                contentAlignment = Alignment.CenterEnd,
            ) { value ->
                Text(
                    text =
                        when (unit) {
                            DistanceUnit.MILES ->
                                stringResource(R.string.radius_value_mi, formatRadius(value))
                            DistanceUnit.KILOMETERS ->
                                stringResource(R.string.radius_value_km, formatRadius(value))
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { newIndex ->
                val idx = newIndex.toInt().coerceIn(0, stops.lastIndex)
                val stopValue = stops[idx]
                val miles =
                    when (unit) {
                        DistanceUnit.MILES -> stopValue
                        DistanceUnit.KILOMETERS -> (stopValue * DistanceUnit.MILES_PER_KM).toFloat()
                    }
                onRadiusChanged(miles)
            },
            valueRange = 0f..stops.lastIndex.toFloat(),
            // One step between every pair of stops, minus the two endpoints.
            steps = stops.size - 2,
        )
    }
}

internal fun formatRadius(value: Float): String =
    if (value < 10f) "%.0f".format(value) else value.toInt().toString()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun RadiusSliderMiPreview() {
    MaterialExpressiveTheme {
        Surface {
            RadiusSlider(
                radiusMi = 5f,
                onRadiusChanged = {},
                unit = DistanceUnit.MILES,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun RadiusSliderKmPreview() {
    MaterialExpressiveTheme {
        Surface {
            RadiusSlider(
                radiusMi = 5f,
                onRadiusChanged = {},
                unit = DistanceUnit.KILOMETERS,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
