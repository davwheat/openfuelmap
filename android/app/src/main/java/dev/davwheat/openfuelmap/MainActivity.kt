package dev.davwheat.openfuelmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import dev.davwheat.openfuelmap.app.api.BottomNavBar
import dev.davwheat.openfuelmap.app.api.LocalBottomNavBarProvider
import dev.davwheat.openfuelmap.app.api.LocalNavigator
import dev.davwheat.openfuelmap.map.api.MapNav
import dev.davwheat.openfuelmap.map.impl.mapEntryBuilder
import dev.davwheat.openfuelmap.nav.Navigator
import dev.davwheat.openfuelmap.ui.theme.AppTheme
import dev.davwheat.smartpromptpilot.nav.rememberNavigationState
import dev.davwheat.smartpromptpilot.nav.toEntries

private val entryProvider = entryProvider { mapEntryBuilder() }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val navigationState =
                rememberNavigationState(
                    startRoute = MapNav.Home,
                    topLevelRoutes = setOf(MapNav.Home),
                )

            val navigator = remember { Navigator(navigationState) }

            AppTheme {
                CompositionLocalProvider(
                    LocalNavigator provides navigator,
                    LocalBottomNavBarProvider provides { BottomNavBar(navigator = navigator) },
                ) {
                    NavDisplay(
                        modifier =
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        entries = navigationState.toEntries(entryProvider),
                        onBack = { navigator.goBack() },
                        sceneStrategy = remember { DialogSceneStrategy() },
                    )
                }
            }
        }
    }
}
