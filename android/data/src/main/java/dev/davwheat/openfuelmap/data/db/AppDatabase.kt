package dev.davwheat.openfuelmap.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FuelTypeEntity::class, BrandEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fuelTypeDao(): FuelTypeDao

    abstract fun brandDao(): BrandDao
}
