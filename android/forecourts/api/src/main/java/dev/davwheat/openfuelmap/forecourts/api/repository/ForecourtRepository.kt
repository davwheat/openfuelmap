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
package dev.davwheat.openfuelmap.forecourts.api.repository

import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.forecourts.api.model.BoundingBox
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtDetail
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtListResult
import dev.davwheat.openfuelmap.forecourts.api.model.ForecourtWithDistance
import dev.davwheat.openfuelmap.forecourts.api.model.PriceHistoryEntry

interface ForecourtRepository {
    suspend fun getForecourts(
        bounds: BoundingBox,
        fuelType: String? = null,
        excludeBrands: Set<String> = emptySet(),
        limit: Int = 1_000,
    ): ApiResult<ForecourtListResult>

    /**
     * Fetch forecourts within [radiusMiles] of [centerLat], [centerLng], sorted nearest-first.
     *
     * Implementations convert the circle to an enclosing bounding box, call the existing bbox
     * endpoint, and filter results by straight-line (haversine) distance so the returned set is a
     * true circle rather than its enclosing square.
     */
    suspend fun getForecourtsNear(
        centerLat: Double,
        centerLng: Double,
        radiusMiles: Double,
        fuelType: String? = null,
        excludeBrands: Set<String> = emptySet(),
        limit: Int = 1_000,
    ): ApiResult<List<ForecourtWithDistance>>

    suspend fun getForecourtDetail(nodeId: String): ApiResult<ForecourtDetail>

    suspend fun getPriceHistory(
        nodeId: String,
        fuelType: String,
        since: String? = null,
        limit: Int = 500,
    ): ApiResult<List<PriceHistoryEntry>>
}
