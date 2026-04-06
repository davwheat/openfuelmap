package dev.davwheat.openfuelmap.forecourts.impl.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
    val sorted =
        remember(fuelTypes) {
            fuelTypes.sortedBy { type ->
                val idx = FuelTypeIds.PRIORITY_ORDER.indexOf(type.id)
                if (idx >= 0) idx else FuelTypeIds.PRIORITY_ORDER.size
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun FuelTypeSelectorPreview() {
    MaterialExpressiveTheme {
        Surface {
            FuelTypeSelector(
                fuelTypes =
                    listOf(
                        FuelTypeEntity(id = FuelTypeIds.B7_STANDARD, name = "Diesel"),
                        FuelTypeEntity(id = FuelTypeIds.E5, name = "Super Unleaded"),
                        FuelTypeEntity(id = FuelTypeIds.E10, name = "Unleaded"),
                    ),
                selectedFuelType = FuelTypeIds.E10,
                onFuelTypeSelected = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
