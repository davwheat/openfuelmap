package dev.davwheat.openfuelmap.map.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.davwheat.openfuelmap.data.ApiConstants
import dev.davwheat.openfuelmap.map.api.repository.ForecourtRepository
import dev.davwheat.openfuelmap.map.data.repository.ForecourtRepositoryImpl
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class MapDataModule {
    @Binds abstract fun bindForecourtRepository(impl: ForecourtRepositoryImpl): ForecourtRepository

    companion object {
        @Provides @Named("baseUrl") fun provideBaseUrl(): String = ApiConstants.BASE_URL
    }
}
