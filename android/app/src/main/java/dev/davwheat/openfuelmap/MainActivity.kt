package dev.davwheat.openfuelmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import dev.davwheat.openfuelmap.app.api.BottomNavBar
import dev.davwheat.openfuelmap.app.api.LocalBottomNavBarProvider
import dev.davwheat.openfuelmap.app.api.LocalNavigator
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.list.api.ListNav
import dev.davwheat.openfuelmap.list.impl.listEntryBuilder
import dev.davwheat.openfuelmap.map.api.MapNav
import dev.davwheat.openfuelmap.map.impl.mapEntryBuilder
import dev.davwheat.openfuelmap.nav.Navigator
import dev.davwheat.openfuelmap.nav.topLevelRouteFromId
import dev.davwheat.openfuelmap.nav.topLevelRouteId
import dev.davwheat.openfuelmap.ui.theme.AppTheme
import dev.davwheat.smartpromptpilot.nav.rememberNavigationState
import dev.davwheat.smartpromptpilot.nav.toEntries
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val entryProvider = entryProvider {
    mapEntryBuilder()
    listEntryBuilder()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()

        // Hold the splash on screen until we've resolved which top-level route to start on.
        // The DataStore read is fast, but it's still async — this keeps the launch flash-free.
        var resolvedStartRoute by mutableStateOf<NavKey?>(null)
        splash.setKeepOnScreenCondition { resolvedStartRoute == null }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val savedId = userPreferencesRepository.lastTopLevelRoute.first()
            resolvedStartRoute = topLevelRouteFromId(savedId) ?: MapNav.Home
        }

        setContent {
            val startRoute = resolvedStartRoute ?: return@setContent

            val navigationState =
                rememberNavigationState(
                    startRoute = startRoute,
                    topLevelRoutes = setOf(MapNav.Home, ListNav.Home),
                )

            val navigator = remember { Navigator(navigationState) }

            // Persist every top-level tab switch so the next cold launch lands on the same tab.
            // `drop(1)` skips the initial value so we don't re-save what we just loaded.
            LaunchedEffect(navigationState) {
                snapshotFlow { navigationState.topLevelRoute }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { route ->
                        topLevelRouteId(route)?.let {
                            userPreferencesRepository.setLastTopLevelRoute(it)
                        }
                    }
            }

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
