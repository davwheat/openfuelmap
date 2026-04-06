package dev.davwheat.openfuelmap.nav

import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.list.api.ListNav
import dev.davwheat.openfuelmap.map.api.MapNav
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

/** NavKey → persisted ID. Returns null for any non-top-level key (shouldn't happen in practice). */
fun topLevelRouteId(key: NavKey): String? =
    when (key) {
        MapNav.Home -> ROUTE_MAP
        ListNav.Home -> ROUTE_LIST
        StatsNav.Home -> ROUTE_STATS
        else -> null
    }

/**
 * Persisted ID → NavKey. Returns null for unknown / dropped identifiers; caller should fall back.
 */
fun topLevelRouteFromId(id: String?): NavKey? =
    when (id) {
        ROUTE_MAP -> MapNav.Home
        ROUTE_LIST -> ListNav.Home
        ROUTE_STATS -> StatsNav.Home
        else -> null
    }
