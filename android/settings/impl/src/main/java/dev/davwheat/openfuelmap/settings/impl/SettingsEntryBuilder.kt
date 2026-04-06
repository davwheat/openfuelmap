package dev.davwheat.openfuelmap.settings.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.settings.api.SettingsNav
import dev.davwheat.openfuelmap.settings.impl.ui.SettingsScreen
import dev.davwheat.openfuelmap.settings.impl.viewmodel.SettingsViewModel

fun EntryProviderScope<NavKey>.settingsEntryBuilder() {
    entry<SettingsNav.Home> {
        val viewModel = hiltViewModel<SettingsViewModel>()
        SettingsScreen(viewModel = viewModel)
    }
}
