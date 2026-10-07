package com.astheral.bikedashboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FillUpDao {
    /** Oldest first, which is the order the calculations need. */
    @Query("SELECT * FROM fill_ups ORDER BY date, odometerKm")
    fun observeAll(): Flow<List<FillUp>>

    @Query("SELECT * FROM fill_ups ORDER BY date, odometerKm")
    suspend fun getAll(): List<FillUp>

    @Query("SELECT * FROM fill_ups WHERE id = :id")
    suspend fun getById(id: Long): FillUp?

    /** Most recent exchange rate, used as the default for the next MY fill-up. */
    @Query("SELECT rmPerSgd FROM fill_ups WHERE rmPerSgd IS NOT NULL ORDER BY date DESC, odometerKm DESC LIMIT 1")
    suspend fun latestRmPerSgd(): Double?

    @Insert
    suspend fun insert(fillUp: FillUp): Long

    @Insert
    suspend fun insertAll(fillUps: List<FillUp>)

    @Update
    suspend fun update(fillUp: FillUp)

    @Delete
    suspend fun delete(fillUp: FillUp)

    @Query("DELETE FROM fill_ups")
    suspend fun deleteAll()

    /** Wipes everything and loads [fillUps] in one go, so a failed import leaves the old data untouched. */
    @Transaction
    suspend fun replaceAll(fillUps: List<FillUp>) {
        deleteAll()
        insertAll(fillUps)
    }
}
