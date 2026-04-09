package dev.davwheat.openfuelmap.list.api

import dev.davwheat.openfuelmap.common.nav.AppNavKey
import kotlinx.serialization.Serializable

object ListNav {
    @Serializable data object ForecourtList : AppNavKey()
}
