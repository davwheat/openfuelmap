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
package dev.davwheat.openfuelmap.nav

import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.list.api.ListNav
import dev.davwheat.openfuelmap.map.api.MapNav
import dev.davwheat.openfuelmap.settings.api.SettingsNav
import dev.davwheat.openfuelmap.stats.api.StatsNav

/**
 * Stable string identifiers for each top-level route, used to persist the user's currently selected
 * tab to DataStore. Kept out of `:data` so the persistence layer doesn't take a dependency on
 * specific feature nav keys.
 *
 * **Never rename these values** — they're stored on user devices. Adding a new tab means adding a
 * new constant + a new branch in the maps below.
 */
private const val ROUTE_MAP = "map"
private const val ROUTE_LIST = "list"
private const val ROUTE_STATS = "stats"
private const val ROUTE_SETTINGS = "settings"

/** NavKey → persisted ID. Returns null for any non-top-level key (shouldn't happen in practice). */
fun topLevelRouteId(key: NavKey): String? =
    when (key) {
        MapNav.ForecourtMap -> ROUTE_MAP
        ListNav.ForecourtList -> ROUTE_LIST
        StatsNav.FuelStatistics -> ROUTE_STATS
        SettingsNav.Settings -> ROUTE_SETTINGS
        else -> null
    }

/**
 * Persisted ID → NavKey. Returns null for unknown / dropped identifiers; caller should fall back.
 */
fun topLevelRouteFromId(id: String?): NavKey? =
    when (id) {
        ROUTE_MAP -> MapNav.ForecourtMap
        ROUTE_LIST -> ListNav.ForecourtList
        ROUTE_STATS -> StatsNav.FuelStatistics
        ROUTE_SETTINGS -> SettingsNav.Settings
        else -> null
    }
