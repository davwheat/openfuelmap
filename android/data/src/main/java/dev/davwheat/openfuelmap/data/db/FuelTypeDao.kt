package dev.davwheat.openfuelmap.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelTypeDao {
    @Query("SELECT * FROM fuel_types") fun getAll(): Flow<List<FuelTypeEntity>>

    @Query("SELECT * FROM fuel_types WHERE id = :id") fun getById(id: String): Flow<FuelTypeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fuelTypes: List<FuelTypeEntity>)

    @Query("DELETE FROM fuel_types") suspend fun deleteAll()
}
