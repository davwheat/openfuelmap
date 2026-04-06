package dev.davwheat.openfuelmap.list.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.list.api.ListNav
import dev.davwheat.openfuelmap.list.impl.ui.ListScreen
import dev.davwheat.openfuelmap.list.impl.viewmodel.ListViewModel

fun EntryProviderScope<NavKey>.listEntryBuilder() {
    entry<ListNav.Home> {
        val viewModel = hiltViewModel<ListViewModel>()
        ListScreen(viewModel = viewModel)
    }
}
