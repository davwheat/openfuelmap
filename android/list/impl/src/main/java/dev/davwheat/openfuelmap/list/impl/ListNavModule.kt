package dev.davwheat.openfuelmap.list.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object ListNavModule {
    @Provides
    @IntoSet
    fun provideListEntryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        listEntryBuilder()
    }
}
