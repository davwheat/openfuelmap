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
package dev.davwheat.openfuelmap.data.result

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing> {
        val message: String
    }

    /** The server answered, but it reports that it cannot complete the request. */
    data class ApiError(override val message: String) : Failure

    /** The app cannot reach the server. See [isNetworkError] for the causes. */
    data class NetworkError(override val message: String, val cause: Throwable? = null) : Failure

    /**
     * The server answered, but the app cannot read the answer. This is a fault in the app or in the
     * API, and not a fault of the connection of the user. Thus it stays separate from
     * [NetworkError]: a message that tells the user to examine their connection would be incorrect.
     */
    data class ParseError(override val message: String, val cause: Throwable? = null) : Failure
}
