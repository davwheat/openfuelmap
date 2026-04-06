package dev.davwheat.openfuelmap.stats.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.davwheat.openfuelmap.stats.api.repository.StatsRepository
import dev.davwheat.openfuelmap.stats.data.repository.StatsRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class StatsDataModule {
    @Binds abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository
}
