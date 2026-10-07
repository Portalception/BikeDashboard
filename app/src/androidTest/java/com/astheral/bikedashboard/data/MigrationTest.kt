package com.astheral.bikedashboard.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrading from version 0.2 (database 1) must keep every fill-up, now on a "My bike" profile. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun version1FillUpsMoveToMyBike() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO fill_ups (id, date, country, odometerKm, myLitres, myCostSgd, rmPaid, rmPerSgd, rmCharged, noSpecialTrip, importedSavingsSgd) " +
                    "VALUES (1, 20364, 'MY', 163903, 6.54, 10.27, 60.0, 3.1847, 32.70, 0, 2.67)",
            )
            execSQL("INSERT INTO fill_ups (id, date, country, odometerKm, sgLitres, sgCostSgd, noSpecialTrip) VALUES (2, 20354, 'SG', 162999, 9.713, 26.44, 0)")
            close()
        }
        // Room checks the upgraded tables match what the app expects.
        helper.runMigrationsAndValidate(dbName, 2, true, migration1To2(tripKm = 52.0)).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java, dbName)
            .addMigrations(migration1To2(52.0))
            .build()
        runBlocking {
            val profile = db.profileDao().getAll().single()
            assertEquals("My bike", profile.name)
            assertEquals(ProfileKind.OWN, profile.kind)
            assertEquals(52.0, profile.tripKm, 0.0)
            assertEquals(DEFAULT_TANK_LITRES, profile.tankLitres, 0.0)
            val fills = db.fillUpDao().getAll(profile.id)
            assertEquals(listOf(162999, 163903), fills.map { it.odometerKm })
            assertEquals(2.67, fills.last().importedSavingsSgd!!, 0.0)
        }
        db.close()
    }
}
