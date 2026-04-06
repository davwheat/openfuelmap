package dev.davwheat.openfuelmap.stats.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.stats.api.StatsNav
import dev.davwheat.openfuelmap.stats.impl.ui.StatsScreen
import dev.davwheat.openfuelmap.stats.impl.viewmodel.StatsViewModel

fun EntryProviderScope<NavKey>.statsEntryBuilder() {
    entry<StatsNav.Home> {
        val viewModel = hiltViewModel<StatsViewModel>()
        StatsScreen(viewModel = viewModel)
    }
}
