package dev.davwheat.openfuelmap.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brands")
data class BrandEntity(@PrimaryKey val name: String, val forecourtCount: Int)
