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

import java.io.IOException

/**
 * `true` when the app cannot reach the server, and thus a message about the connection of the user
 * is correct.
 *
 * OkHttp reports each fault of this type as an `IOException`: no route to the host
 * (`UnknownHostException`), a timeout (`SocketTimeoutException`), a closed socket
 * (`SocketException`), and a TLS fault (`SSLException`) are all subclasses. A fault in the data
 * that the server sends is not an `IOException`, thus it does not become a network error here.
 */
fun Throwable.isNetworkError(): Boolean = this is IOException
