package com.astheral.bikedashboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class Currency(val symbol: String) { SGD("S$"), MYR("RM") }

/** One visit to the workshop (or a wash, or a part you fitted yourself) for one bike. */
@Serializable
@Entity(tableName = "service_records", indices = [Index("profileId")])
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    /** Odometer reading at the time, if known. */
    val odometerKm: Int? = null,
    /** The [ServiceCategory.id] of the kind of work, e.g. "ENGINE_OIL". */
    val type: String,
    val description: String = "",
    val cost: Double? = null,
    val currency: Currency = Currency.SGD,
)

/** The highest odometer reading among fill-ups and services, i.e. roughly where the bike is now. */
fun currentOdometer(fillUps: List<FillUp>, records: List<ServiceRecord>): Int? =
    (fillUps.map { it.odometerKm } + records.mapNotNull { it.odometerKm }).maxOrNull()

@Dao
interface ServiceDao {
    /** Newest first. */
    @Query("SELECT * FROM service_records WHERE profileId = :profileId ORDER BY date DESC, odometerKm DESC, id DESC")
    fun observe(profileId: Long): Flow<List<ServiceRecord>>

    @Query("SELECT * FROM service_records WHERE profileId = :profileId ORDER BY date DESC, odometerKm DESC, id DESC")
    suspend fun getAll(profileId: Long): List<ServiceRecord>

    @Query("SELECT * FROM service_records WHERE id = :id")
    suspend fun getById(id: Long): ServiceRecord?

    @Insert
    suspend fun insert(record: ServiceRecord): Long

    @Insert
    suspend fun insertAll(records: List<ServiceRecord>)

    @Update
    suspend fun update(record: ServiceRecord)

    @Delete
    suspend fun delete(record: ServiceRecord)

    @Query("DELETE FROM service_records WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)

    /** Swaps this bike's records for [records] in one go, e.g. when importing the spreadsheet. */
    @Transaction
    suspend fun replaceAll(profileId: Long, records: List<ServiceRecord>) {
        deleteAll(profileId)
        insertAll(records.map { it.copy(id = 0, profileId = profileId) })
    }
}
