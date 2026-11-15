package com.astheral.bikedashboard.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun epochDayToDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun countryToName(country: Country): String = country.name

    @TypeConverter
    fun nameToCountry(name: String): Country = Country.valueOf(name)

    @TypeConverter
    fun kindToName(kind: ProfileKind): String = kind.name

    @TypeConverter
    fun nameToKind(name: String): ProfileKind = ProfileKind.valueOf(name)
}

/**
 * Version 2 adds bike profiles. Everything already in the app becomes the first profile,
 * "My bike", keeping the trip distance that was set in Settings.
 */
fun migration1To2(tripKm: Double) = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `tankLitres` REAL NOT NULL, `tripKm` REAL NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `profiles` (`id`, `name`, `kind`, `tankLitres`, `tripKm`) VALUES (1, 'My bike', 'OWN', ?, ?)",
            arrayOf<Any>(DEFAULT_TANK_LITRES, tripKm),
        )
        db.execSQL("ALTER TABLE `fill_ups` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fill_ups_profileId` ON `fill_ups` (`profileId`)")
    }
}

/**
 * Version 6 turns the fixed list of kinds of service work into editable categories. Records and
 * intervals already stored the kind's name ("ENGINE_OIL" …), which become the built-in
 * categories' ids, so nothing in them changes; the new table just gets the built-in list.
 */
class SeedServiceCategories : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) = db.insertCategories(DEFAULT_CATEGORIES)
}

/** Fills in the categories on a fresh install, and makes sure "Other" is always there. */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = db.insertCategories(DEFAULT_CATEGORIES)

    override fun onOpen(db: SupportSQLiteDatabase) =
        db.insertCategories(DEFAULT_CATEGORIES.filter { it.id == DefaultCategory.OTHER })
}

/**
 * Version 3 adds each bike's paperwork (plate, chassis number, expiry dates …) in its own table,
 * version 4 its servicing records, version 5 its service intervals, version 6 the service
 * categories shared by all bikes, version 7 marks service odometer readings that were estimated
 * version 8 adds the registration date, year, engine size and colour from the log card,
 * version 10 marks partial fill-ups (existing ones are full tanks) and version 11 adds the
 * petrol brand to fill-ups (none recorded for existing ones).
 */
@Database(
    entities = [FillUp::class, Profile::class, BikeDetails::class, ServiceRecord::class, ServiceInterval::class, ServiceCategory::class],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = SeedServiceCategories::class),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fillUpDao(): FillUpDao
    abstract fun profileDao(): ProfileDao
    abstract fun bikeDetailsDao(): BikeDetailsDao
    abstract fun backupDao(): BackupDao
    abstract fun serviceDao(): ServiceDao
    abstract fun intervalDao(): IntervalDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bike_dashboard.db",
                )
                    .addMigrations(migration1To2(Settings.get(context).legacyTripKm))
                    .addCallback(SeedCallback)
                    .build()
                    .also { instance = it }
            }
    }
}
