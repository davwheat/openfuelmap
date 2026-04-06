package dev.davwheat.openfuelmap.stats.data.repository

import dev.davwheat.openfuelmap.data.result.ApiResult
import dev.davwheat.openfuelmap.stats.api.model.DailyMedianPrice
import dev.davwheat.openfuelmap.stats.api.model.PriceStat
import dev.davwheat.openfuelmap.stats.api.model.TimeRange
import dev.davwheat.openfuelmap.stats.api.repository.StatsRepository
import dev.davwheat.openfuelmap.stats.data.api.StatsApiClient
import javax.inject.Inject

class StatsRepositoryImpl @Inject constructor(private val apiClient: StatsApiClient) :
    StatsRepository {

    override suspend fun getDailyPrices(
        timeRange: TimeRange,
        stat: PriceStat,
    ): ApiResult<List<DailyMedianPrice>> = apiClient.getDailyPrices(timeRange, stat)
}
