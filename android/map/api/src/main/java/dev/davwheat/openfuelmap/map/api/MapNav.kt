package dev.davwheat.openfuelmap.map.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

object MapNav {
    @Serializable data object Home : NavKey
}
