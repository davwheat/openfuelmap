package dev.davwheat.openfuelmap.settings.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.settings.impl.R

@Composable
internal fun AboutSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.about_heading),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Text(
            stringResource(R.string.about_body_1),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(stringResource(R.string.about_body_2), style = MaterialTheme.typography.bodyMedium)
    }
}
