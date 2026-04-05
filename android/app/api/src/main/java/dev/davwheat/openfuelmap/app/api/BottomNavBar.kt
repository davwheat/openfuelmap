package dev.davwheat.openfuelmap.app.api

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.davwheat.openfuelmap.map.api.MapNav

private data class TopLevelNavMetadata(
    val icon: @Composable (Modifier, selected: Boolean) -> Unit,
    val name: String,
)

private val topLevelNav =
    mutableMapOf(
        MapNav.Home to
            TopLevelNavMetadata(
                icon = { modifier, selected ->
                    Icon(
                        painterResource(
                            if (selected) R.drawable.map_search_filled_24dp
                            else R.drawable.map_search_24dp
                        ),
                        contentDescription = null,
                        modifier = modifier,
                    )
                },
                name = "Map",
            )
    )

@Composable
fun BottomNavBar(modifier: Modifier = Modifier, navigator: INavigator) {
    NavigationBar(modifier = modifier) {
        topLevelNav.forEach { (key, metadata) ->
            val selected = navigator.topLevelRoute == key

            NavigationBarItem(
                selected = selected,
                onClick = { navigator.navigate(key) },
                icon = { metadata.icon(Modifier.size(20.dp), selected) },
                label = { Text(metadata.name) },
            )
        }
    }
}
