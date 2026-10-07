package com.astheral.bikedashboard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
}

@Database(entities = [FillUp::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fillUpDao(): FillUpDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bike_dashboard.db",
                ).build().also { instance = it }
            }
    }
}
