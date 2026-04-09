package dev.davwheat.openfuelmap.settings.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.settings.impl.R
import dev.davwheat.openfuelmap.settings.impl.model.SettingsItem

/**
 * Dispatches a [SettingsItem] to the appropriate row composable (toggle, single-select, or
 * multi-select chips).
 */
@Composable
internal fun SettingsItemRow(item: SettingsItem, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        when (item) {
            is SettingsItem.Toggle -> ToggleSettingsRow(item)
            is SettingsItem.SingleSelectChips<*> -> SingleSelectChipsRow(item)
            is SettingsItem.MultiSelectChips<*> -> MultiSelectChipsRow(item)
        }
    }
}

/**
 * Row with a title, optional description, and a trailing [Switch] bound to [item]'s checked state.
 */
@Composable
private fun ToggleSettingsRow(item: SettingsItem.Toggle) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(
                    value = item.checked,
                    role = Role.Switch,
                    onValueChange = item.onCheckedChange,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            if (item.description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = item.checked, onCheckedChange = null)
    }
}

/**
 * Row with a title, optional description, and a [FlowRow] of [FilterChip]s for single-selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SingleSelectChipsRow(item: SettingsItem.SingleSelectChips<T>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
        if (item.description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.forEach { option ->
                val selected = option == item.selectedOption
                FilterChip(
                    selected = selected,
                    onClick = dropUnlessResumed { item.onOptionSelected(option) },
                    label = { Text(item.optionLabel(option)) },
                    leadingIcon =
                        if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else null,
                )
            }
        }
    }
}

/**
 * Row with a title, optional description, "Select all"/"Deselect all" buttons, and a [FlowRow] of
 * toggleable [FilterChip]s. Excluded options show a block icon; included ones show a check.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> MultiSelectChipsRow(item: SettingsItem.MultiSelectChips<T>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
        if (item.description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(
                onClick = dropUnlessResumed { item.onSelectAll() },
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
            ) {
                Text(stringResource(R.string.select_all))
            }
            TextButton(
                onClick = dropUnlessResumed { item.onDeselectAll() },
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
            ) {
                Text(stringResource(R.string.deselect_all))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.forEach { option ->
                key(item.optionLabel(option)) {
                    val included = option !in item.excludedOptions
                    FilterChip(
                        selected = included,
                        onClick = dropUnlessResumed { item.onOptionToggled(option) },
                        label = { Text(item.optionLabel(option)) },
                        leadingIcon = {
                            Icon(
                                imageVector =
                                    if (included) Icons.Rounded.Done else Icons.Rounded.Block,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}
