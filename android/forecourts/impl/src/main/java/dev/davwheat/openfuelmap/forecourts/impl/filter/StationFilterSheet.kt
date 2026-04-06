package dev.davwheat.openfuelmap.forecourts.impl.filter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.data.db.BrandEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeEntity
import dev.davwheat.openfuelmap.data.db.FuelTypeIds

/**
 * Fuel-type + brand filter sheet shared between the Map and List screens. The caller owns all
 * state; this sheet simply emits selection callbacks and closes on dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationFilterSheet(
    fuelTypes: List<FuelTypeEntity>,
    selectedFuelType: String?,
    onFuelTypeSelected: (String) -> Unit,
    brands: List<BrandEntity>,
    excludedBrands: Set<String>,
    onBrandToggled: (String) -> Unit,
    onDismiss: () -> Unit,
    fuelTypeBlurb: String =
        "Only show stations offering this fuel type. Prices shown will also be for this fuel.",
    brandBlurb: String = "Tap a brand to hide its stations from results.",
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
                text = fuelTypeBlurb,
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
                text = brandBlurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            BrandSelector(
                brands = brands,
                excludedBrands = excludedBrands,
                onBrandToggled = onBrandToggled,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun StationFilterSheetPreview() {
    MaterialExpressiveTheme {
        Surface {
            StationFilterSheet(
                fuelTypes =
                    listOf(
                        FuelTypeEntity(id = FuelTypeIds.E10, name = "Unleaded"),
                        FuelTypeEntity(id = FuelTypeIds.E5, name = "Super Unleaded"),
                        FuelTypeEntity(id = FuelTypeIds.B7_STANDARD, name = "Diesel"),
                    ),
                selectedFuelType = FuelTypeIds.E10,
                onFuelTypeSelected = {},
                brands =
                    listOf(
                        BrandEntity(name = "Shell", forecourtCount = 1200),
                        BrandEntity(name = "BP", forecourtCount = 1100),
                        BrandEntity(name = "Esso", forecourtCount = 900),
                        BrandEntity(name = "Tesco", forecourtCount = 500),
                    ),
                excludedBrands = setOf("Esso"),
                onBrandToggled = {},
                onDismiss = {},
            )
        }
    }
}
