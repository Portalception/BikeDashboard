package com.astheral.bikedashboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class ProfileKind(val label: String) { OWN("Own Bike"), RENTAL("Rental") }

/** Usual size of a sports bike tank, e.g. the Yamaha R3's 14 L. */
const val DEFAULT_TANK_LITRES = 14.0

/** A bike whose fill-ups are tracked separately: your own, or a rental. */
@Serializable
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: ProfileKind = ProfileKind.OWN,
    /** Used to warn when a fill-up is more than the tank holds. */
    val tankLitres: Double = DEFAULT_TANK_LITRES,
    /**
     * No longer used: the trip into Malaysia is one setting for every bike now ([Settings.tripKm]).
     * The column stays so old databases and backups still load, and its value seeds that setting once.
     */
    val tripKm: Double = DEFAULT_TRIP_KM,
)

data class ProfileCount(val profileId: Long, val count: Int)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun getAll(): List<Profile>

    @Query("SELECT profileId, COUNT(*) AS count FROM fill_ups GROUP BY profileId")
    fun observeCounts(): Flow<List<ProfileCount>>

    @Insert
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("DELETE FROM fill_ups WHERE profileId = :profileId")
    suspend fun deleteFillUps(profileId: Long)

    @Query("DELETE FROM bike_details WHERE profileId = :profileId")
    suspend fun deleteDetails(profileId: Long)

    @Query("DELETE FROM service_records WHERE profileId = :profileId")
    suspend fun deleteServices(profileId: Long)

    @Query("DELETE FROM service_intervals WHERE profileId = :profileId")
    suspend fun deleteIntervals(profileId: Long)

    /** Removes the bike and everything kept for it together. */
    @Transaction
    suspend fun deleteWithFillUps(profile: Profile) {
        deleteFillUps(profile.id)
        deleteDetails(profile.id)
        deleteServices(profile.id)
        deleteIntervals(profile.id)
        delete(profile)
    }
}
