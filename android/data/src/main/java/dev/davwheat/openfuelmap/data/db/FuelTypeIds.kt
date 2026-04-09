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
package dev.davwheat.openfuelmap.data.db

/**
 * Canonical fuel type IDs as returned by the API and stored in [FuelTypeEntity.id]. These are
 * stable identifiers used for API filtering and UI ordering.
 */
object FuelTypeIds {
    const val E10 = "E10"
    const val E5 = "E5"
    const val B7_STANDARD = "B7_STANDARD"

    /**
     * Canonical display/selection priority. Used both for ordering the fuel-type chips in the UI
     * and for picking the initial default when the user has not yet chosen one. Fuel types not in
     * this list sort after these, in their natural order.
     */
    val PRIORITY_ORDER: List<String> = listOf(E10, E5, B7_STANDARD)
}
