/**
 * Open Fuel Map
 * Copyright (C) 2026  David Wheatley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.davwheat.openfuelmap.settings.impl.ui

// cannot use v2: https://github.com/google/play-services-plugins/issues/400
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
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
            item(key = "about") { AboutSection(Modifier.padding(horizontal = 16.dp)) }

            item(key = "settings_heading") {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.settings_heading),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier =
                        Modifier.padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 6.dp),
                )
            }

            items(items, key = { it.key }) { item ->
                SettingsItemRow(item, modifier = Modifier.animateItem())
            }

            item(key = "oss_licenses") {
                val context = LocalContext.current

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(Modifier.fillMaxWidth())

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable(
                                onClick =
                                    dropUnlessResumed {
                                        context.startActivity(
                                            Intent(context, OssLicensesMenuActivity::class.java)
                                        )
                                    }
                            )
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .semantics(true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.open_source_licenses),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
