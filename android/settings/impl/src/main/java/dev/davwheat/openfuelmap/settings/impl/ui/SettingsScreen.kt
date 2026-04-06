package dev.davwheat.openfuelmap.settings.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davwheat.openfuelmap.app.api.LocalBottomNavBarProvider
import dev.davwheat.openfuelmap.settings.impl.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settingsItems by viewModel.settingsItems.collectAsStateWithLifecycle()
    val bottomNavBar = LocalBottomNavBarProvider.current

    Scaffold(
        topBar = {
            TopAppBar(
                titleHorizontalAlignment = Alignment.CenterHorizontally,
                title = { Text("Settings") },
                subtitle = {},
            )
        },
        bottomBar = bottomNavBar,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(settingsItems, key = { it.key }) { item ->
                SettingsItemRow(item, modifier = Modifier.animateItem())
            }
        }
    }
}
