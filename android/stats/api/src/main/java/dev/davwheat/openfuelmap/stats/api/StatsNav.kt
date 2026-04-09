package dev.davwheat.openfuelmap.stats.api

import dev.davwheat.openfuelmap.common.nav.AppNavKey
import kotlinx.serialization.Serializable

object StatsNav {
    @Serializable data object FuelStatistics : AppNavKey()
}
