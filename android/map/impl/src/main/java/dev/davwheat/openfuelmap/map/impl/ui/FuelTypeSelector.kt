package dev.davwheat.openfuelmap.map.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeIds

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuelTypeSelector(
    fuelTypes: List<FuelTypeEntity>,
    selectedFuelType: String?,
    onFuelTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val priorityOrder = listOf(FuelTypeIds.E10, FuelTypeIds.E5, FuelTypeIds.B7_STANDARD)
    val sorted =
        remember(fuelTypes) {
            fuelTypes.sortedBy { type ->
                val idx = priorityOrder.indexOf(type.id)
                if (idx >= 0) idx else priorityOrder.size
            }
        }

    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sorted.forEach { fuelType ->
            val selected = fuelType.id == selectedFuelType
            FilterChip(
                selected = selected,
                onClick = { onFuelTypeSelected(fuelType.id) },
                label = { Text(fuelType.name) },
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
