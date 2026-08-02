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

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Records the baseline profile for `:app`.
 *
 * Run `./gradlew :app:generateBaselineProfile` with a rooted emulator (or a userdebug device)
 * connected. The result lands in `app/src/<flavor>/generated/baselineProfiles/`.
 */
@RunWith(JUnit4::class)
class BaselineProfileGenerator {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(
            packageName = targetPackage,
            // Cold start plus a full pass over every tab is long. More iterations add little
            // beyond this, and each one costs a device minute.
            maxIterations = 8,
            stableIterations = 2,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            device.waitForAppContent()
            device.exerciseMap()

            device.selectTab(TAB_LIST)
            device.scrollThroughContent()

            device.selectTab(TAB_STATS)
            device.scrollThroughContent()

            device.selectTab(TAB_SETTINGS)
            device.scrollThroughContent()

            // Returning to the map covers the restore path, which differs from first composition.
            device.selectTab(TAB_MAP)
        }
}
