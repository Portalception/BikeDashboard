package com.astheral.bikedashboard.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Paperwork for one bike, as kept on the spreadsheet's dashboard: what it is, what it cost,
 * and when the COE, road tax, inspection and insurance run out. Everything is optional.
 */
@Serializable
@Entity(tableName = "bike_details")
data class BikeDetails(
    @PrimaryKey val profileId: Long,
    val brand: String? = null,
    val model: String? = null,
    val plate: String? = null,
    val chassisNumber: String? = null,
    val engineNumber: String? = null,
    /** No longer shown on the Bike details page; kept so older data and backups still load. */
    val iuNumber: String? = null,
    val registeredIu: String? = null,
    /** When the bike was registered, from the log card. */
    @Serializable(with = LocalDateSerializer::class) val registrationDate: LocalDate? = null,
    val manufactureYear: Int? = null,
    /** Engine capacity in cc. */
    val engineCc: Int? = null,
    /** The colour as on the log card, like "Blue / White" (primary / secondary). */
    val colour: String? = null,

    // The money fields and start odometer are no longer shown on the Bike details page (the
    // "Value" section was removed); they're kept so older data and backups still load.

    /** What you paid for the bike, in SGD. */
    val purchasePrice: Double? = null,
    val coePaid: Double? = null,
    val openMarketValue: Double? = null,
    /** The odometer reading when you got the bike, so you can see how far you've ridden it. */
    val startOdometerKm: Int? = null,

    @Serializable(with = LocalDateSerializer::class) val coeExpiry: LocalDate? = null,
    val coeNumber: String? = null,
    @Serializable(with = LocalDateSerializer::class) val roadTaxExpiry: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val inspectionExpiry: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val insuranceExpiry: LocalDate? = null,
    val insurancePolicy: String? = null,

    val notes: String? = null,
)

@Dao
interface BikeDetailsDao {
    @Query("SELECT * FROM bike_details WHERE profileId = :profileId")
    fun observe(profileId: Long): Flow<BikeDetails?>

    @Query("SELECT * FROM bike_details WHERE profileId = :profileId")
    suspend fun get(profileId: Long): BikeDetails?

    @Query("SELECT * FROM bike_details")
    suspend fun getAll(): List<BikeDetails>

    @Upsert
    suspend fun save(details: BikeDetails)

    @Query("DELETE FROM bike_details WHERE profileId = :profileId")
    suspend fun delete(profileId: Long)
}
