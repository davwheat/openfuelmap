package dev.davwheat.openfuelmap.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.davwheat.openfuelmap.data.ApiConstants
import dev.davwheat.openfuelmap.data.BuildConfig
import dev.davwheat.openfuelmap.data.db.AppDatabase
import dev.davwheat.openfuelmap.data.db.BrandDao
import dev.davwheat.openfuelmap.data.db.FuelTypeDao
import dev.davwheat.openfuelmap.data.utils.DefaultDispatcherProvider
import dev.davwheat.openfuelmap.data.utils.DispatcherProvider
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "openfuelmap.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideFuelTypeDao(database: AppDatabase): FuelTypeDao = database.fuelTypeDao()

    @Provides @Singleton fun provideBrandDao(database: AppDatabase): BrandDao = database.brandDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), 10L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level =
                        if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()

    @Provides @Named("baseUrl") fun provideBaseUrl(): String = ApiConstants.BASE_URL

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
