package dev.davwheat.openfuelmap.forecourts.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.davwheat.openfuelmap.data.ApiConstants
import dev.davwheat.openfuelmap.forecourts.api.repository.ForecourtRepository
import dev.davwheat.openfuelmap.forecourts.data.repository.ForecourtRepositoryImpl
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class ForecourtsDataModule {
    @Binds abstract fun bindForecourtRepository(impl: ForecourtRepositoryImpl): ForecourtRepository

    companion object {
        @Provides @Named("baseUrl") fun provideBaseUrl(): String = ApiConstants.BASE_URL
    }
}
