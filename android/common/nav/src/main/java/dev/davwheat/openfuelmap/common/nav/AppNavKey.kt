package dev.davwheat.openfuelmap.common.nav

import androidx.navigation3.runtime.NavKey

/**
 * Base [NavKey] whose [toString] returns the fully qualified class name, ensuring unique content
 * keys.
 */
abstract class AppNavKey : NavKey {
    override fun toString(): String =
        this::class.qualifiedName ?: this::class.simpleName ?: super.toString()
}
