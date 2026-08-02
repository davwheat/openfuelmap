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
package dev.davwheat.openfuelmap.common.maps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.common.ui.HtmlText
import dev.davwheat.openfuelmap.common.ui.SimpleTooltip
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.maplibre.android.maps.Style

/**
 * The attribution strings that this session has already shown. The popup opens by itself only on
 * the first map of a session, thus a user who moves between map screens does not see it each time.
 */
private val seenAttributions = mutableSetOf<String>()

/**
 * The time the popup stays open after it opens by itself. The attribution guidelines of the OSM
 * Foundation permit an interactive map to close the credit after a short time.
 *
 * See https://osmfoundation.org/wiki/Licence/Attribution_Guidelines#Interactive_maps
 */
private val AutoShowDuration = 5.seconds

/**
 * The credit of the map data, which the ODbL licence of OpenStreetMap makes necessary. The text
 * comes from the `attribution` field of the sources in [style], thus the map style controls the
 * credit and this app does not repeat it.
 *
 * Gives `null` if no source has an attribution. The control below then shows nothing.
 *
 * `Style.getSources` fails if the style is not fully loaded, and the style can close before Compose
 * removes its content block. Thus this function tests the state first, in the same way as
 * [createSymbolManagerOrNull].
 */
fun Style.attributionHtml(): String? {
    if (!isFullyLoaded) return null
    return sources
        .mapNotNull { it.attribution.takeIf(String::isNotEmpty) }
        .distinct()
        .joinToString(" ")
        .takeIf(String::isNotEmpty)
}

/**
 * An information button with a popup that holds the map credit. It shows nothing when
 * [attributionHtml] is `null`.
 *
 * The native attribution button of MapLibre does the same, but it opens a dialog that hides the map
 * and needs two touches to read a link. This control keeps the credit on the map, thus it agrees
 * with the attribution guidelines.
 *
 * @param textSide The side of the button that holds the popup, and thus the direction in which the
 *   popup opens. Use [Alignment.Start] (the default) with a top-end position in the parent, and
 *   [Alignment.End] with a top-start position.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapAttributionControl(
    attributionHtml: String?,
    modifier: Modifier = Modifier,
    textSide: Alignment.Horizontal = Alignment.Start,
) {
    if (attributionHtml == null) return

    // Read the set during composition, and change it in the effect below. Thus composition has no
    // side effect.
    val isFirstShow = remember(attributionHtml) { attributionHtml !in seenAttributions }
    var attributionShowing by remember(attributionHtml) { mutableStateOf(isFirstShow) }

    LaunchedEffect(attributionHtml) {
        seenAttributions.add(attributionHtml)
        if (isFirstShow) {
            delay(AutoShowDuration)
            attributionShowing = false
        }
    }

    val buttonText = stringResource(R.string.map_attribution_button)
    val buttonSize = IconButtonDefaults.extraSmallContainerSize()

    val popup =
        @Composable {
            AnimatedVisibility(attributionShowing, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier.heightIn(min = buttonSize.height),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.wrapContentHeight(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        HtmlText(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
                            text = attributionHtml,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    val infoButton =
        @Composable {
            SimpleTooltip(text = buttonText) {
                FilledTonalIconButton(
                    onClick = { attributionShowing = !attributionShowing },
                    modifier = Modifier.size(buttonSize),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = buttonText,
                        modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
                    )
                }
            }
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // This order puts the popup on the requested side, thus it opens away from the button.
        if (textSide == Alignment.Start) {
            popup()
            infoButton()
        } else {
            infoButton()
            popup()
        }
    }
}
