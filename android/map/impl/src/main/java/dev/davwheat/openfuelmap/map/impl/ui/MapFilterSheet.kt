package dev.davwheat.openfuelmap.map.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.data.db.BrandEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapFilterSheet(
    fuelTypes: List<FuelTypeEntity>,
    selectedFuelType: String?,
    onFuelTypeSelected: (String) -> Unit,
    brands: List<BrandEntity>,
    selectedBrand: String?,
    onBrandSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "Filter stations", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text =
                    "Only show stations offering this fuel type. " +
                        "Prices shown on the map will also be for this fuel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Fuel type", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(8.dp))

            if (fuelTypes.isNotEmpty()) {
                FuelTypeSelector(
                    fuelTypes = fuelTypes,
                    selectedFuelType = selectedFuelType,
                    onFuelTypeSelected = onFuelTypeSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "Brand", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Restrict the map to stations from a single brand.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            BrandSelector(
                brands = brands,
                selectedBrand = selectedBrand,
                onBrandSelected = onBrandSelected,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
