package dev.davwheat.openfuelmap.forecourts.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.davwheat.openfuelmap.forecourts.api.repository.ForecourtRepository
import dev.davwheat.openfuelmap.forecourts.data.repository.ForecourtRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class ForecourtsDataModule {
    @Binds abstract fun bindForecourtRepository(impl: ForecourtRepositoryImpl): ForecourtRepository
}
