package com.astheral.bikedashboard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.astheral.bikedashboard.reminders.Reminders
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** Dates are written as "2026-10-08" in backups. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/**
 * Every saved setting (theme, colours, progress line, units, which bike is showing …) by its
 * saved name. Values are written with their type in front, like "b:true" or "i:-16777216", so
 * settings added in later versions are backed up without changing this format.
 */
@Serializable
data class SettingsBackup(val values: Map<String, String> = emptyMap())

/** One bike and everything kept for it. */
@Serializable
data class BikeBackup(
    val profile: Profile,
    val details: BikeDetails? = null,
    val fillUps: List<FillUp> = emptyList(),
    val services: List<ServiceRecord> = emptyList(),
    /** Only intervals that were changed from the defaults. */
    val intervals: List<ServiceInterval> = emptyList(),
)

/**
 * A complete copy of the app: every bike with its fill-ups and details, the settings
 * (theme, colours, units …) and the ideas notebook. Restoring it replaces everything.
 */
@Serializable
data class FullBackup(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: String = "",
    val appVersion: String = "",
    val settings: SettingsBackup = SettingsBackup(),
    val ideas: String = "",
    /** The service categories, shared by all bikes. Empty in backups from before they could be changed. */
    val categories: List<ServiceCategory> = emptyList(),
    val bikes: List<BikeBackup> = emptyList(),
) {
    companion object {
        const val FORMAT = "bike-dashboard-backup"
    }
}

/**
 * The categories to restore: the backup's own, or the built-in ones for an older backup, plus one
 * for any kind of work its records or intervals use that isn't among them.
 */
fun FullBackup.restoredCategories(): List<ServiceCategory> {
    val intervalTypes = bikes.flatMap { b -> b.intervals.map { it.type } }
    val used = bikes.flatMap { b -> b.services.map { it.type } } + intervalTypes
    return withMissingCategories(categories.ifEmpty { DEFAULT_CATEGORIES }, used, intervalTypes.toSet())
}

/** Lenient, so a backup from a newer version of the app still opens in an older one. */
val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/** True if [text] looks like a full backup rather than a CSV. */
fun looksLikeFullBackup(text: String): Boolean = text.trimStart().startsWith("{") && text.contains(FullBackup.FORMAT)

/** Reads a full backup, or throws [IllegalArgumentException] saying what's wrong. */
fun readFullBackup(text: String): FullBackup {
    val backup = try {
        BackupJson.decodeFromString<FullBackup>(text)
    } catch (e: SerializationException) {
        throw IllegalArgumentException("The backup file is damaged: ${e.message}")
    }
    require(backup.format == FullBackup.FORMAT) { "This isn't a Bike Dashboard backup." }
    require(backup.bikes.isNotEmpty()) { "The backup has no bikes in it." }
    return backup
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun profiles(): List<Profile>

    @Query("SELECT * FROM fill_ups ORDER BY date, odometerKm")
    suspend fun fillUps(): List<FillUp>

    @Query("SELECT * FROM bike_details")
    suspend fun details(): List<BikeDetails>

    @Query("SELECT * FROM service_records ORDER BY date, id")
    suspend fun services(): List<ServiceRecord>

    @Query("SELECT * FROM service_intervals")
    suspend fun intervals(): List<ServiceInterval>

    @Query("SELECT * FROM service_categories ORDER BY sortOrder, name")
    suspend fun categories(): List<ServiceCategory>

    @Query("DELETE FROM service_categories")
    suspend fun clearCategories()

    @Insert
    suspend fun insertCategories(categories: List<ServiceCategory>)

    @Query("DELETE FROM service_intervals")
    suspend fun clearIntervals()

    @Insert
    suspend fun insertIntervals(intervals: List<ServiceInterval>)

    @Query("DELETE FROM service_records")
    suspend fun clearServices()

    @Insert
    suspend fun insertServices(services: List<ServiceRecord>)

    @Query("DELETE FROM fill_ups")
    suspend fun clearFillUps()

    @Query("DELETE FROM bike_details")
    suspend fun clearDetails()

    @Query("DELETE FROM profiles")
    suspend fun clearProfiles()

    @Insert
    suspend fun insertProfiles(profiles: List<Profile>)

    @Insert
    suspend fun insertFillUps(fillUps: List<FillUp>)

    @Insert
    suspend fun insertDetails(details: List<BikeDetails>)

    /** Swaps everything for the backup's bikes and categories in one go, so a failed restore changes nothing. */
    @Transaction
    suspend fun replaceEverything(bikes: List<BikeBackup>, categories: List<ServiceCategory>) {
        clearCategories()
        insertCategories(categories)
        clearFillUps()
        clearDetails()
        clearServices()
        clearIntervals()
        clearProfiles()
        insertProfiles(bikes.map { it.profile })
        insertDetails(bikes.mapNotNull { b -> b.details?.copy(profileId = b.profile.id) })
        insertFillUps(bikes.flatMap { b -> b.fillUps.map { it.copy(id = 0, profileId = b.profile.id) } })
        insertServices(bikes.flatMap { b -> b.services.map { it.copy(id = 0, profileId = b.profile.id) } })
        insertIntervals(bikes.flatMap { b -> b.intervals.map { it.copy(profileId = b.profile.id) } })
    }
}

/** The ideas notebook's text file. */
fun ideasFile(context: Context) = File(context.filesDir, "ideas.txt")

/** Makes and restores [FullBackup]s. */
class Backups(private val context: Context) {
    private val dao = AppDatabase.get(context).backupDao()
    private val settings = Settings.get(context)

    suspend fun create(appVersion: String): FullBackup {
        val fillUps = dao.fillUps().groupBy { it.profileId }
        val details = dao.details().associateBy { it.profileId }
        val services = dao.services().groupBy { it.profileId }
        val intervals = dao.intervals().groupBy { it.profileId }
        val ideas = ideasFile(context).takeIf { it.exists() }?.readText().orEmpty()
        return FullBackup(
            exportedAt = java.time.LocalDateTime.now().withNano(0).toString(),
            appVersion = appVersion,
            settings = settings.backup(),
            ideas = ideas,
            categories = dao.categories(),
            bikes = dao.profiles().map { p ->
                BikeBackup(p, details[p.id], fillUps[p.id].orEmpty(), services[p.id].orEmpty(), intervals[p.id].orEmpty())
            },
        )
    }

    suspend fun restore(backup: FullBackup) {
        dao.replaceEverything(backup.bikes, backup.restoredCategories())
        settings.restore(backup.settings)
        ideasFile(context).writeText(backup.ideas)
        Reminders.schedule(context)
        // The restored app icon is switched to once you leave the app (see MainActivity).
    }
}
