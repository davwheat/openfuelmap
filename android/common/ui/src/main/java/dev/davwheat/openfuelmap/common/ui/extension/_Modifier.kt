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
package dev.davwheat.openfuelmap.common.ui.extension

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

private val VerticalScrollConsumer =
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            return available.copy(x = 0f)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            return available.copy(x = 0f)
        }
    }

private val HorizontalScrollConsumer =
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource) =
            available.copy(y = 0f)

        override suspend fun onPreFling(available: Velocity) = available.copy(y = 0f)
    }

/**
 * This prevents any excess vertical scroll from being passed to a parent within a nested scroll
 * connection.
 */
fun Modifier.consumeExtraVerticalScroll(disabled: Boolean = true) =
    if (disabled) this.nestedScroll(VerticalScrollConsumer) else this

/**
 * This prevents any excess horizontal scroll from being passed to a parent within a nested scroll
 * connection.
 */
fun Modifier.consumeExtraHorizontalScroll(disabled: Boolean = true) =
    if (disabled) this.nestedScroll(HorizontalScrollConsumer) else this
