package dev.davwheat.openfuelmap.app.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Holds the current top app bar content, allowing screens to provide their own TopAppBar to the
 * scene decorator's Scaffold.
 */
class TopAppBarState {
    var content: @Composable () -> Unit by mutableStateOf({})
}

val LocalTopAppBarState = compositionLocalOf<TopAppBarState> { error("No TopAppBarState provided") }

/**
 * Provides a TopAppBar composable to the scene decorator's Scaffold. Call this at the top of each
 * screen composable.
 */
@Composable
fun ProvideTopBar(content: @Composable () -> Unit) {
    val topAppBarState = LocalTopAppBarState.current
    val currentContent by rememberUpdatedState(content)
    topAppBarState.content = { currentContent() }
}
