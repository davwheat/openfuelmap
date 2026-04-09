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
package dev.davwheat.openfuelmap.forecourts.data.api.dto

import dev.davwheat.openfuelmap.forecourts.api.model.Location
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationDto(
    @SerialName("address_line_1") val addressLine1: String?,
    @SerialName("address_line_2") val addressLine2: String?,
    val city: String?,
    val country: String?,
    val county: String?,
    val postcode: String?,
    val latitude: Double,
    val longitude: Double,
) {
    fun toDomain(): Location =
        Location(
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            country = country,
            county = county,
            postcode = postcode,
            latitude = latitude,
            longitude = longitude,
        )
}
