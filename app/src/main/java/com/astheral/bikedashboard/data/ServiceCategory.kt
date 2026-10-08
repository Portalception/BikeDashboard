package com.astheral.bikedashboard.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * A kind of servicing work, like "Engine oil" or "Bike wash". Categories are shared by every
 * bike, and you can add, rename and remove them. [id] never changes once made, so records and
 * intervals (which store it as their `type`) keep pointing at it through a rename. The built-in
 * ones use the names they had when the list was fixed, like "ENGINE_OIL".
 */
@Serializable
@Entity(tableName = "service_categories")
data class ServiceCategory(
    @PrimaryKey val id: String,
    val name: String,
    /** Where it's listed; lower comes first. */
    val sortOrder: Int,
    /** Whether it's an item on the Service intervals page, so it can be due and remind you. */
    val hasInterval: Boolean = false,
)

/** The ids of the categories every install starts with. */
object DefaultCategory {
    const val MAJOR_SERVICING = "MAJOR_SERVICING"
    const val MINOR_SERVICING = "MINOR_SERVICING"
    const val ENGINE_OIL = "ENGINE_OIL"
    const val FLUID_CHANGE = "FLUID_CHANGE"
    const val REPAIR = "REPAIR"
    const val PERFORMANCE_MOD = "PERFORMANCE_MOD"
    const val COSMETIC_MOD = "COSMETIC_MOD"
    const val BIKE_WASH = "BIKE_WASH"

    /** Where records go when their category is removed. It can be renamed but never removed. */
    const val OTHER = "OTHER"
}

/** The categories a new install starts with: the spreadsheet's kinds of work, plus "Other". */
val DEFAULT_CATEGORIES = listOf(
    ServiceCategory(DefaultCategory.MAJOR_SERVICING, "Major servicing", 0, hasInterval = true),
    ServiceCategory(DefaultCategory.MINOR_SERVICING, "Minor servicing", 1, hasInterval = true),
    ServiceCategory(DefaultCategory.ENGINE_OIL, "Engine oil", 2, hasInterval = true),
    ServiceCategory(DefaultCategory.FLUID_CHANGE, "Fluid change", 3, hasInterval = true),
    ServiceCategory(DefaultCategory.REPAIR, "Repair", 4),
    ServiceCategory(DefaultCategory.PERFORMANCE_MOD, "Performance mod", 5),
    ServiceCategory(DefaultCategory.COSMETIC_MOD, "Cosmetic mod", 6),
    ServiceCategory(DefaultCategory.BIKE_WASH, "Bike wash", 7, hasInterval = true),
    ServiceCategory(DefaultCategory.OTHER, "Other", 8),
)

/** "CHAIN_CLEAN" → "Chain clean", for a category that's missing its name. */
fun nameFromId(id: String): String =
    id.lowercase(Locale.ROOT).replace('_', ' ').trim().replaceFirstChar { it.titlecase(Locale.ROOT) }.ifEmpty { "Other" }

/** The name to show for category [id], or one made from the id if it's missing. */
fun List<ServiceCategory>.nameOf(id: String): String = firstOrNull { it.id == id }?.name ?: nameFromId(id)

/** The category called [name] (or with that id), ignoring case and spaces at the ends. */
fun List<ServiceCategory>.findByName(name: String): ServiceCategory? {
    val wanted = name.trim()
    return firstOrNull { it.name.trim().equals(wanted, ignoreCase = true) } ?: firstOrNull { it.id.equals(wanted, ignoreCase = true) }
}

/** An id for a new category called [name]: "Chain clean" → "CHAIN_CLEAN", or "CHAIN_CLEAN_2" if that's [taken]. */
fun newCategoryId(name: String, taken: Set<String>): String {
    val base = name.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_').ifEmpty { "CATEGORY" }
    if (base !in taken) return base
    var n = 2
    while ("${base}_$n" in taken) n++
    return "${base}_$n"
}

/** A new category called [name], listed after all of [existing]. */
fun newCategory(name: String, existing: List<ServiceCategory>, hasInterval: Boolean = false) = ServiceCategory(
    id = newCategoryId(name, existing.map { it.id }.toSet()),
    name = name.trim(),
    sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
    hasInterval = hasInterval,
)

/**
 * [base] plus a category for every id in [used] that it doesn't have (named from the id), and
 * always "Other". Ids in [withInterval] are put on the intervals page. Used when restoring a
 * backup, which may be from before categories could be changed.
 */
fun withMissingCategories(base: List<ServiceCategory>, used: Collection<String>, withInterval: Set<String> = emptySet()): List<ServiceCategory> {
    val all = base.toMutableList()
    (used + DefaultCategory.OTHER).distinct().forEach { id ->
        if (all.none { it.id == id }) {
            val default = DEFAULT_CATEGORIES.firstOrNull { it.id == id }
            all += ServiceCategory(
                id = id,
                name = default?.name ?: nameFromId(id),
                sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                hasInterval = id in withInterval || default?.hasInterval == true,
            )
        }
    }
    return all
}

/** Adds [categories] straight into the database, skipping any id that's already there. */
fun SupportSQLiteDatabase.insertCategories(categories: List<ServiceCategory>) {
    categories.forEach { c ->
        execSQL(
            "INSERT OR IGNORE INTO `service_categories` (`id`, `name`, `sortOrder`, `hasInterval`) VALUES (?, ?, ?, ?)",
            arrayOf<Any>(c.id, c.name, c.sortOrder, if (c.hasInterval) 1 else 0),
        )
    }
}

/** How many records (on every bike) are in one category. */
data class CategoryCount(val id: String, val count: Int)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM service_categories ORDER BY sortOrder, name")
    fun observe(): Flow<List<ServiceCategory>>

    @Query("SELECT * FROM service_categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<ServiceCategory>

    @Query("SELECT type AS id, COUNT(*) AS count FROM service_records GROUP BY type")
    fun observeCounts(): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM service_records WHERE type = :id")
    suspend fun recordCount(id: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<ServiceCategory>)

    @Upsert
    suspend fun save(category: ServiceCategory)

    @Upsert
    suspend fun saveInterval(interval: ServiceInterval)

    @Query("UPDATE service_categories SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE service_categories SET hasInterval = :on WHERE id = :id")
    suspend fun setHasInterval(id: String, on: Boolean)

    @Query("UPDATE service_records SET type = :to WHERE type = :from")
    suspend fun moveRecords(from: String, to: String)

    @Query("DELETE FROM service_intervals WHERE type = :id")
    suspend fun deleteIntervals(id: String)

    @Query("DELETE FROM service_categories WHERE id = :id")
    suspend fun deleteOnly(id: String)

    /** Removes a category: its records (on every bike) move to "Other" and its intervals go. "Other" itself stays. */
    @Transaction
    suspend fun delete(id: String) {
        if (id == DefaultCategory.OTHER) return
        moveRecords(id, DefaultCategory.OTHER)
        deleteIntervals(id)
        deleteOnly(id)
    }

    /** Puts [category] on the intervals page (adding it if it's new), with [interval] for one bike. */
    @Transaction
    suspend fun addInterval(category: ServiceCategory, interval: ServiceInterval) {
        save(category.copy(hasInterval = true))
        saveInterval(interval)
    }

    /** Takes a category off the intervals page, deleting its interval on every bike. Its records stay. */
    @Transaction
    suspend fun removeInterval(id: String) {
        setHasInterval(id, false)
        deleteIntervals(id)
    }
}
