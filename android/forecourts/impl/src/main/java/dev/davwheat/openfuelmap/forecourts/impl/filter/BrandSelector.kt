package dev.davwheat.openfuelmap.forecourts.impl.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.data.db.BrandEntity

/**
 * Brand filter. Every brand is selected (included) by default; tapping a chip toggles it to
 * excluded, which populates the API's `exclude_brand` query parameter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrandSelector(
    brands: List<BrandEntity>,
    excludedBrands: Set<String>,
    onBrandToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        brands.sortedBy { it.name }.forEach { brand ->
            val included = brand.name !in excludedBrands
            FilterChip(
                selected = included,
                onClick = { onBrandToggled(brand.name) },
                label = { Text(brand.name) },
                leadingIcon = {
                    Icon(
                        imageVector = if (included) Icons.Rounded.Done else Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun BrandSelectorPreview() {
    MaterialExpressiveTheme {
        Surface {
            BrandSelector(
                brands =
                    listOf(
                        BrandEntity(name = "Shell", forecourtCount = 1200),
                        BrandEntity(name = "BP", forecourtCount = 1100),
                        BrandEntity(name = "Esso", forecourtCount = 900),
                        BrandEntity(name = "Tesco", forecourtCount = 500),
                        BrandEntity(name = "Asda", forecourtCount = 400),
                    ),
                excludedBrands = setOf("Esso"),
                onBrandToggled = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
