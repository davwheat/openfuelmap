package dev.davwheat.openfuelmap.map.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.map.api.MapNav
import dev.davwheat.openfuelmap.map.impl.ui.MapScreen
import dev.davwheat.openfuelmap.map.impl.viewmodel.MapViewModel

fun EntryProviderScope<NavKey>.mapEntryBuilder() {
    entry<MapNav.ForecourtMap> {
        val viewModel = hiltViewModel<MapViewModel>()
        MapScreen(viewModel = viewModel)
    }
}
