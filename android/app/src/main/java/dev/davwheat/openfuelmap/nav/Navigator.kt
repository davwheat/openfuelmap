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
