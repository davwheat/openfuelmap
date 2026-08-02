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
package dev.davwheat.openfuelmap.baselineprofile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

const val TAB_MAP = "Map"
const val TAB_LIST = "List"
const val TAB_STATS = "Stats"
const val TAB_SETTINGS = "Settings"

private const val UI_TIMEOUT_MS = 10_000L
private const val SETTLE_TIMEOUT_MS = 3_000L

/** Package name of the app under test, supplied by the `targetAppId` instrumentation argument. */
val targetPackage: String
    get() =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId instrumentation argument not set")

/**
 * Waits for the first frame of real app content. The splash screen stays up until the start route
 * resolves from DataStore, thus waiting on the window alone returns too early.
 */
fun UiDevice.waitForAppContent() {
    wait(Until.hasObject(By.pkg(targetPackage).depth(0)), UI_TIMEOUT_MS)
    wait(Until.hasObject(By.text(TAB_MAP)), UI_TIMEOUT_MS)
    waitForIdle()
}

/** Selects a bottom navigation tab and lets the destination settle. */
fun UiDevice.selectTab(label: String) {
    val tab =
        wait(Until.findObject(By.text(label)), UI_TIMEOUT_MS)
            // A map gesture can land on a marker and open the forecourt detail sheet over the
            // navigation bar. Dismissing it is part of a real journey, thus this is a retry
            // rather than a failure.
            ?: run {
                pressBack()
                waitForIdle()
                wait(Until.findObject(By.text(label)), UI_TIMEOUT_MS)
            }
            ?: error("Bottom navigation tab \"$label\" not found")

    tab.click()
    waitForIdle()

    // Feature screens load their data asynchronously. A settle window lets the loaded state
    // compose, so its classes reach the profile as well as the loading state's.
    wait(Until.hasObject(By.text(label)), SETTLE_TIMEOUT_MS)
    Thread.sleep(SETTLE_TIMEOUT_MS)
}

/**
 * Scrolls the tallest scrollable on screen, if there is one. A screen showing an error or an empty
 * state has nothing to scroll, thus a missing scrollable is not a failure.
 */
fun UiDevice.scrollThroughContent() {
    // Shimmer placeholders and loading state changes can invalidate the node mid-gesture, which
    // says nothing about the profile, thus a stale node ends the scroll instead of the run.
    runCatching {
        val scrollable =
            findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() } ?: return

        scrollable.setGestureMarginPercentage(0.2f)
        scrollable.fling(Direction.DOWN)
        waitForIdle()
        scrollable.fling(Direction.UP)
    }

    waitForIdle()
}

/** Pans and zooms the map so tile loading, annotation drawing and gesture handling all run. */
fun UiDevice.exerciseMap() {
    mapGesture { it.swipe(Direction.LEFT, 0.5f, 1_000) }
    mapGesture { it.swipe(Direction.UP, 0.5f, 1_000) }
    mapGesture { it.pinchOpen(0.6f) }
    mapGesture { it.pinchClose(0.6f) }

    // Give MapLibre time to fetch and render the tiles for the new viewport.
    Thread.sleep(SETTLE_TIMEOUT_MS)
}

/**
 * Runs one gesture over the app window.
 *
 * The map redraws on every frame, so an accessibility node captured for an earlier gesture is
 * already stale by the next one. Each gesture therefore re-finds the window, and a stale node only
 * drops that gesture instead of failing the whole run.
 */
private fun UiDevice.mapGesture(gesture: (UiObject2) -> Unit) {
    runCatching {
        val window = wait(Until.findObject(By.pkg(targetPackage).depth(0)), UI_TIMEOUT_MS) ?: return

        // The margin keeps gestures clear of the system back-gesture edges and of the bottom
        // navigation bar.
        window.setGestureMarginPercentage(0.25f)
        gesture(window)
    }

    waitForIdle()
}
