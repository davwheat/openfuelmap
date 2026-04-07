package dev.davwheat.openfuelmap.settings.api

import dev.davwheat.openfuelmap.common.nav.AppNavKey
import kotlinx.serialization.Serializable

object SettingsNav {
    @Serializable data object Home : AppNavKey()
}
