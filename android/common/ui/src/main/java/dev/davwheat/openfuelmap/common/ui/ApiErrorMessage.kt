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
package dev.davwheat.openfuelmap.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import dev.davwheat.openfuelmap.data.result.ApiResult

/**
 * A message about [this] failure for the user.
 *
 * The `message` field of a failure holds the text of the exception, for example "Failed to connect
 * to /192.0.2.1:443". That text helps a developer, but it tells a user nothing that they can act
 * on. This function gives a message for each type of failure instead.
 *
 * An [ApiResult.ApiError] keeps its message, because the server writes that text for the user.
 */
@Composable
@ReadOnlyComposable
fun ApiResult.Failure.userMessage(): String =
    when (this) {
        is ApiResult.NetworkError -> stringResource(R.string.error_network)
        is ApiResult.ParseError -> stringResource(R.string.error_unexpected_response)
        is ApiResult.ApiError -> message
    }
