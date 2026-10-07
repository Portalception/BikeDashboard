package com.astheral.bikedashboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Fill-ups always belong to one bike profile; every list is for a single [profileId]. */
@Dao
interface FillUpDao {
    /** Oldest first, which is the order the calculations need. */
    @Query("SELECT * FROM fill_ups WHERE profileId = :profileId ORDER BY date, odometerKm")
    fun observeAll(profileId: Long): Flow<List<FillUp>>

    @Query("SELECT * FROM fill_ups WHERE profileId = :profileId ORDER BY date, odometerKm")
    suspend fun getAll(profileId: Long): List<FillUp>

    @Query("SELECT * FROM fill_ups WHERE id = :id")
    suspend fun getById(id: Long): FillUp?

    /** Most recent exchange rate on any bike, used as the default for the next MY fill-up. */
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

    @Query("DELETE FROM fill_ups WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)

    /**
     * Wipes this bike's fill-ups and loads [fillUps] for it in one go, so a failed import leaves
     * the old data untouched. Other bikes aren't affected.
     */
    @Transaction
    suspend fun replaceAll(profileId: Long, fillUps: List<FillUp>) {
        deleteAll(profileId)
        insertAll(fillUps.map { it.copy(id = 0, profileId = profileId) })
    }
}
