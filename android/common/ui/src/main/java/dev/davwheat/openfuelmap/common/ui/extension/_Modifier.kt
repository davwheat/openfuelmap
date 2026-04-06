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
