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
package dev.davwheat.openfuelmap.app.api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.list.api.ListNav
import dev.davwheat.openfuelmap.map.api.MapNav
import dev.davwheat.openfuelmap.settings.api.SettingsNav
import dev.davwheat.openfuelmap.stats.api.StatsNav

private data class TopLevelNavMetadata(
    val icon: @Composable (Modifier, selected: Boolean) -> Unit,
    val nameRes: Int,
)

private val topLevelNav =
    mutableMapOf(
        MapNav.ForecourtMap to
            TopLevelNavMetadata(
                icon = { modifier, selected ->
                    Icon(
                        painterResource(
                            if (selected) R.drawable.map_search_filled_24dp
                            else R.drawable.map_search_24dp
                        ),
                        contentDescription = null,
                        modifier = modifier,
                    )
                },
                nameRes = R.string.nav_map,
            ),
        ListNav.ForecourtList to
            TopLevelNavMetadata(
                icon = { modifier, selected ->
                    Icon(
                        painterResource(
                            if (selected) R.drawable.list_filled_24dp else R.drawable.list_24dp
                        ),
                        contentDescription = null,
                        modifier = modifier,
                    )
                },
                nameRes = R.string.nav_list,
            ),
        StatsNav.FuelStatistics to
            TopLevelNavMetadata(
                icon = { modifier, selected ->
                    Icon(
                        painterResource(
                            if (selected) R.drawable.leaderboard_filled_24dp
                            else R.drawable.leaderboard_24dp
                        ),
                        contentDescription = null,
                        modifier = modifier,
                    )
                },
                nameRes = R.string.nav_stats,
            ),
        SettingsNav.Settings to
            TopLevelNavMetadata(
                icon = { modifier, selected ->
                    Icon(
                        painterResource(
                            if (selected) R.drawable.settings_filled_24dp
                            else R.drawable.settings_24dp
                        ),
                        contentDescription = null,
                        modifier = modifier,
                    )
                },
                nameRes = R.string.nav_settings,
            ),
    )

/**
 * App-wide bottom navigation bar. Highlights the tab matching [navigator]'s current top-level
 * route.
 */
@Composable
fun BottomNavBar(modifier: Modifier = Modifier, navigator: INavigator) {
    NavigationBar(modifier = modifier) {
        topLevelNav.forEach { (key, metadata) ->
            val selected = navigator.topLevelRoute == key

            NavigationBarItem(
                selected = selected,
                onClick = dropUnlessResumed { navigator.navigate(key) },
                icon = { metadata.icon(Modifier, selected) },
                label = { Text(stringResource(metadata.nameRes)) },
            )
        }
    }
}

private class PreviewNavigator(override val topLevelRoute: NavKey) : INavigator {
    override fun navigate(route: NavKey, replaceExisting: Boolean) = Unit

    override fun goBack() = Unit
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun BottomNavBarMapSelectedPreview() {
    MaterialExpressiveTheme { BottomNavBar(navigator = PreviewNavigator(MapNav.ForecourtMap)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun BottomNavBarListSelectedPreview() {
    MaterialExpressiveTheme { BottomNavBar(navigator = PreviewNavigator(ListNav.ForecourtList)) }
}
