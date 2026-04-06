package dev.davwheat.openfuelmap.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

object SettingsNav {
    @Serializable data object Home : NavKey
}
