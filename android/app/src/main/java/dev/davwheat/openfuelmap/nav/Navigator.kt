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

import androidx.navigation3.runtime.NavKey
import dev.davwheat.openfuelmap.app.api.INavigator

/** Handles navigation events (forward and back) by updating the navigation state. */
class Navigator(val state: NavigationState) : INavigator {
    override val topLevelRoute: NavKey
        get() = state.topLevelRoute

    override fun navigate(route: NavKey, replaceExisting: Boolean) {
        if (route in state.backStacks.keys) {
            // This is a top level route, just switch to it.
            state.topLevelRoute = route
        } else {
            if (replaceExisting) {
                state.backStacks[state.topLevelRoute]?.removeLastOrNull()
            }

            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    override fun goBack() {
        val currentStack =
            state.backStacks[state.topLevelRoute]
                ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()

        // If we're at the base of the current route, go back to the start route stack.
        if (currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}
