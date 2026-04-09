package dev.davwheat.openfuelmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import dev.davwheat.openfuelmap.app.api.BottomNavBar
import dev.davwheat.openfuelmap.app.api.LocalNavigator
import dev.davwheat.openfuelmap.app.api.LocalTopAppBarState
import dev.davwheat.openfuelmap.app.api.TopAppBarState
import dev.davwheat.openfuelmap.data.repository.UserPreferencesRepository
import dev.davwheat.openfuelmap.list.api.ListNav
import dev.davwheat.openfuelmap.map.api.MapNav
import dev.davwheat.openfuelmap.nav.Navigator
import dev.davwheat.openfuelmap.nav.rememberNavigationState
import dev.davwheat.openfuelmap.nav.rememberTopAppBarDecoratorStrategy
import dev.davwheat.openfuelmap.nav.toEntries
import dev.davwheat.openfuelmap.nav.topLevelRouteFromId
import dev.davwheat.openfuelmap.nav.topLevelRouteId
import dev.davwheat.openfuelmap.settings.api.SettingsNav
import dev.davwheat.openfuelmap.stats.api.StatsNav
import dev.davwheat.openfuelmap.ui.theme.AppTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var entryBuilders: Set<@JvmSuppressWildcards EntryProviderScope<NavKey>.() -> Unit>

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
            resolvedStartRoute = topLevelRouteFromId(savedId) ?: MapNav.ForecourtMap
        }

        setContent {
            val startRoute = resolvedStartRoute ?: return@setContent

            val navigationState =
                rememberNavigationState(
                    startRoute = startRoute,
                    topLevelRoutes =
                        setOf(
                            MapNav.ForecourtMap,
                            ListNav.ForecourtList,
                            StatsNav.FuelStatistics,
                            SettingsNav.Settings,
                        ),
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
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    val topAppBarState = remember { TopAppBarState() }
                    val topAppBarDecorator = rememberTopAppBarDecoratorStrategy<NavKey>()

                    CompositionLocalProvider(LocalTopAppBarState provides topAppBarState) {
                        Scaffold(
                            topBar = { topAppBarState.content() },
                            bottomBar = { BottomNavBar(navigator = navigator) },
                        ) { contentPadding ->
                            Box(Modifier.padding(contentPadding)) {
                                NavDisplay(
                                    entries =
                                        navigationState.toEntries(
                                            entryProvider {
                                                entryBuilders.forEach { builder -> builder() }
                                            }
                                        ),
                                    sceneDecoratorStrategies = listOf(topAppBarDecorator),
                                    onBack = navigator::goBack,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
