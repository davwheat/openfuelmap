package dev.davwheat.openfuelmap.stats.api.repository

import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.stats.api.model.DailyMedianPrice
import dev.davwheat.openfuelmap.stats.api.model.PriceStat
import dev.davwheat.openfuelmap.stats.api.model.TimeRange

interface StatsRepository {
    suspend fun getDailyPrices(
        timeRange: TimeRange,
        stat: PriceStat,
    ): ApiResult<List<DailyMedianPrice>>
}
