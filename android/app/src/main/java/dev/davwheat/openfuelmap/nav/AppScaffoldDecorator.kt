package dev.davwheat.openfuelmap.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope

/**
 * A [Scene] decorator that wraps another scene for use with the app's outer Scaffold. Screens set
 * their TopAppBar content via [dev.davwheat.openfuelmap.app.api.ProvideTopBar], which writes to the
 * [dev.davwheat.openfuelmap.app.api.LocalTopAppBarState] provided by MainActivity.
 */
data class TopAppBarScene<T : Any>(private val scene: Scene<T>) : Scene<T> by scene {
    override val key = scene::class to scene.key

    override val content = @Composable { scene.content() }
}

class TopAppBarDecoratorStrategy<T : Any> : SceneDecoratorStrategy<T> {
    override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> {
        return TopAppBarScene(scene)
    }
}

@Composable
fun <T : Any> rememberTopAppBarDecoratorStrategy(): TopAppBarDecoratorStrategy<T> {
    return remember { TopAppBarDecoratorStrategy() }
}
