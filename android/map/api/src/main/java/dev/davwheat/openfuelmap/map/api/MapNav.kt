package dev.davwheat.openfuelmap.map.api

import dev.davwheat.openfuelmap.common.nav.AppNavKey
import kotlinx.serialization.Serializable

object MapNav {
    @Serializable data object Home : AppNavKey()
}
