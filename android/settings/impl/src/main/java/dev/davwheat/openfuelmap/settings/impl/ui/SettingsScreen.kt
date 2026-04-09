package dev.davwheat.openfuelmap.settings.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davwheat.openfuelmap.app.api.ProvideTopBar
import dev.davwheat.openfuelmap.settings.impl.R
import dev.davwheat.openfuelmap.settings.impl.viewmodel.SettingsViewModel

/**
 * Settings screen. Shows a loading spinner until items are ready, then a scrollable list of
 * [SettingsItemRow]s.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settingsItems by viewModel.settingsItems.collectAsStateWithLifecycle()

    ProvideTopBar {
        TopAppBar(
            titleHorizontalAlignment = Alignment.CenterHorizontally,
            title = { Text(stringResource(R.string.settings_title)) },
            subtitle = {},
        )
    }

    val items = settingsItems
    if (items == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(items, key = { it.key }) { item ->
                SettingsItemRow(item, modifier = Modifier.animateItem())
            }
        }
    }
}
