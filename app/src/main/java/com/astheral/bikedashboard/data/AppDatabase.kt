package com.astheral.bikedashboard.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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

/** Version 3 adds each bike's paperwork (plate, chassis number, expiry dates …) in its own table. */
@Database(
    entities = [FillUp::class, Profile::class, BikeDetails::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fillUpDao(): FillUpDao
    abstract fun profileDao(): ProfileDao
    abstract fun bikeDetailsDao(): BikeDetailsDao
    abstract fun backupDao(): BackupDao

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
                    .build()
                    .also { instance = it }
            }
    }
}
