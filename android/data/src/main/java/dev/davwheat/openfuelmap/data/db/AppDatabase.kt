package dev.davwheat.openfuelmap.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FuelTypeEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fuelTypeDao(): FuelTypeDao
}
