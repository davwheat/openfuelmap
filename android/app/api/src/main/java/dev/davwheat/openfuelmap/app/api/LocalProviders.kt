package dev.davwheat.openfuelmap.app.api

import androidx.compose.runtime.compositionLocalOf

val LocalNavigator = compositionLocalOf<INavigator> { error("No navigator provided") }
