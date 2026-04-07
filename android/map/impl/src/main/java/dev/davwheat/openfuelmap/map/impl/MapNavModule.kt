package dev.davwheat.openfuelmap.map.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object MapNavModule {
    @Provides
    @IntoSet
    fun provideMapEntryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        mapEntryBuilder()
    }
}
