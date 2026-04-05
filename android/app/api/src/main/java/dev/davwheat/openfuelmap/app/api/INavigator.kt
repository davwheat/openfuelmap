package dev.davwheat.openfuelmap.app.api

import androidx.navigation3.runtime.NavKey

interface INavigator {
    val topLevelRoute: NavKey

    fun navigate(route: NavKey, replaceExisting: Boolean = false)

    fun goBack()
}
