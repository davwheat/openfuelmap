package dev.davwheat.openfuelmap.app.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalBottomNavBarProvider =
    compositionLocalOf<@Composable () -> Unit> { error("No bottom nav bar provided") }

val LocalNavigator = compositionLocalOf<INavigator> { error("No navigator provided") }
