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
package dev.davwheat.openfuelmap.forecourts.api.model

data class ForecourtDetail(
    val nodeId: String,
    val tradingName: String,
    val brandName: String,
    val isSameTradingAndBrandName: Boolean,
    val publicPhoneNumber: String?,
    val temporaryClosure: Boolean,
    val permanentClosure: Boolean?,
    val permanentClosureDate: String?,
    val isMotorwayServiceStation: Boolean,
    val isSupermarketServiceStation: Boolean,
    val location: Location,
    val amenities: List<String>,
    val openingTimes: OpeningTimes?,
    val fuelTypes: List<String>,
    val updatedAt: String,
    val currentPrices: List<FuelPrice>,
)
