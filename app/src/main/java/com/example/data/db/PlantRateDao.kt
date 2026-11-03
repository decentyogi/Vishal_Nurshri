package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PlantRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantRateDao {
    @Query("SELECT * FROM plant_rates ORDER BY name ASC")
    fun getAllRates(): Flow<List<PlantRateEntity>>

    @Query("SELECT * FROM plant_rates ORDER BY name ASC")
    suspend fun getAllRatesList(): List<PlantRateEntity>

    @Query("SELECT * FROM plant_rates WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchRates(query: String): Flow<List<PlantRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: PlantRateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRates(rates: List<PlantRateEntity>)

    @Update
    suspend fun updateRate(rate: PlantRateEntity)

    @Query("DELETE FROM plant_rates WHERE id = :id")
    suspend fun deleteRate(id: Long)

    @Query("DELETE FROM plant_rates")
    suspend fun deleteAllRates()

    @Query("SELECT COUNT(*) FROM plant_rates")
    suspend fun getRateCount(): Int
}
