package dev.davwheat.openfuelmap.stats.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object StatsNavModule {
    @Provides
    @IntoSet
    fun provideStatsEntryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        statsEntryBuilder()
    }
}
