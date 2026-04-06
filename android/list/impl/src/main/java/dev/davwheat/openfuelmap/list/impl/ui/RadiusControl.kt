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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Non-linear list of search radii (miles). Better resolution at short distances — which is where
 * most users will live — with two larger steps at the top of the scale for the occasional road
 * trip. Maxes out at 50 miles per product decision.
 */
internal val RADIUS_STOPS: List<Float> = listOf(1f, 2f, 3f, 5f, 10f, 15f, 20f, 30f, 50f)

/**
 * Slider with discrete non-linear stops. The slider is driven in "stop index" space (0..N-1) and we
 * translate to/from miles for the caller. This keeps the slider's `steps` simple and snaps touch to
 * the nearest advertised value.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun RadiusSlider(radiusMi: Float, onRadiusChanged: (Float) -> Unit, modifier: Modifier = Modifier) {
    val currentIndex =
        remember(radiusMi) {
            RADIUS_STOPS.indexOfFirst { it >= radiusMi }
                .let { if (it < 0) RADIUS_STOPS.lastIndex else it }
        }

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Search radius",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                radiusMi,
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
            ) { radiusMi ->
                Text(
                    text = "${formatRadiusMi(radiusMi)} mi",
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
                val idx = newIndex.toInt().coerceIn(0, RADIUS_STOPS.lastIndex)
                onRadiusChanged(RADIUS_STOPS[idx])
            },
            valueRange = 0f..RADIUS_STOPS.lastIndex.toFloat(),
            // One step between every pair of stops, minus the two endpoints.
            steps = RADIUS_STOPS.size - 2,
        )
    }
}

internal fun formatRadiusMi(radiusMi: Float): String =
    if (radiusMi < 10f) "%.0f".format(radiusMi) else radiusMi.toInt().toString()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun RadiusSliderPreview() {
    MaterialExpressiveTheme {
        Surface {
            RadiusSlider(radiusMi = 5f, onRadiusChanged = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
