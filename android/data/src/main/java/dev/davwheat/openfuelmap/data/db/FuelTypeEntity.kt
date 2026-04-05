package dev.davwheat.openfuelmap.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_types")
data class FuelTypeEntity(@PrimaryKey val id: String, val name: String)
