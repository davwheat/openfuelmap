/**
 * Open Fuel Map
 * Copyright (C) 2026  David Wheatley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.davwheat.openfuelmap.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope

/**
 * A [Scene] decorator that wraps another scene for use with the app's outer Scaffold. Screens set
 * their TopAppBar content via [dev.davwheat.openfuelmap.app.api.ProvideTopBar], which writes to the
 * [dev.davwheat.openfuelmap.app.api.LocalTopAppBarState] provided by MainActivity.
 *
 * The decorator also gives each screen an opaque background. The map draws into a `SurfaceView`,
 * which the system composites in its own hardware layer and not with the other views. During a
 * movement between screens, the two scenes animate but that layer does not move with them, thus a
 * screen with no background of its own shows the window behind it as a black area. An opaque colour
 * on each scene hides that area.
 */
data class TopAppBarScene<T : Any>(private val scene: Scene<T>) : Scene<T> by scene {
    override val key = scene::class to scene.key

    override val content =
        @Composable {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                scene.content()
            }
        }
}

class TopAppBarDecoratorStrategy<T : Any> : SceneDecoratorStrategy<T> {
    override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> {
        return TopAppBarScene(scene)
    }
}

/** Remembers a [TopAppBarDecoratorStrategy] to pass to the navigation3 [Scene] host. */
@Composable
fun <T : Any> rememberTopAppBarDecoratorStrategy(): TopAppBarDecoratorStrategy<T> {
    return remember { TopAppBarDecoratorStrategy() }
}
