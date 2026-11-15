package com.astheral.bikedashboard.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** Version 6 turns the fixed kinds of work into categories; existing records and intervals keep theirs. */
    @Test
    fun version5RecordsKeepTheirKindAsACategory() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO profiles (id, name, kind, tankLitres, tripKm) VALUES (1, 'My bike', 'OWN', 14.0, 48.0)")
            execSQL("INSERT INTO service_records (id, profileId, date, odometerKm, type, description, cost, currency) VALUES (1, 1, 20364, 159057, 'ENGINE_OIL', 'Motul', 45.0, 'SGD')")
            execSQL("INSERT INTO service_intervals (profileId, type, enabled, everyKm) VALUES (1, 'BIKE_WASH', 1, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 6, true).apply {
            query("SELECT COUNT(*) FROM service_categories").use { it.moveToFirst(); assertEquals(DEFAULT_CATEGORIES.size, it.getInt(0)) }
            query("SELECT type FROM service_records").use { it.moveToFirst(); assertEquals("ENGINE_OIL", it.getString(0)) }
            query("SELECT name, hasInterval FROM service_categories WHERE id = 'ENGINE_OIL'").use {
                it.moveToFirst()
                assertEquals("Engine oil", it.getString(0))
                assertEquals(1, it.getInt(1))
            }
            query("SELECT type FROM service_intervals").use { it.moveToFirst(); assertEquals("BIKE_WASH", it.getString(0)) }
            close()
        }
    }

    /** Version 7 adds the "odometer was estimated" mark; records from before were all read off the bike. */
    @Test
    fun version6RecordsAreNotEstimated() {
        helper.createDatabase(dbName, 6).apply {
            execSQL("INSERT INTO service_records (id, profileId, date, odometerKm, type, description, cost, currency) VALUES (1, 1, 20364, 159057, 'ENGINE_OIL', '', NULL, 'SGD')")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 7, true).apply {
            query("SELECT odometerEstimated FROM service_records").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            close()
        }
    }

    /** Version 9 adds archiving; every bike from before is still in use. */
    @Test
    fun version8BikesAreNotArchived() {
        helper.createDatabase(dbName, 8).apply {
            execSQL("INSERT INTO profiles (id, name, kind, tankLitres, tripKm) VALUES (1, 'R3', 'OWN', 14.0, 48.0)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 9, true).apply {
            query("SELECT name, archived FROM profiles").use {
                it.moveToFirst()
                assertEquals("R3", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
            close()
        }
    }

    /** Version 10 adds partial fill-ups; every fill-up from before was a full tank. */
    @Test
    fun version9FillUpsAreFullTanks() {
        helper.createDatabase(dbName, 9).apply {
            execSQL("INSERT INTO fill_ups (id, profileId, date, country, odometerKm, sgLitres, sgCostSgd, noSpecialTrip) VALUES (1, 1, 20354, 'SG', 162999, 9.713, 26.44, 0)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 10, true).apply {
            query("SELECT odometerKm, fullTank FROM fill_ups").use {
                it.moveToFirst()
                assertEquals(162999, it.getInt(0))
                assertEquals(1, it.getInt(1))
            }
            close()
        }
    }

    /** Version 12 adds the Singapore price before discount; fill-ups from before have none. */
    @Test
    fun version11FillUpsHaveNoPriceBeforeDiscount() {
        helper.createDatabase(dbName, 11).apply {
            execSQL("INSERT INTO fill_ups (id, profileId, date, country, odometerKm, sgLitres, sgCostSgd, noSpecialTrip, fullTank, brand) VALUES (1, 1, 20354, 'SG', 162999, 9.713, 26.44, 0, 1, 'Shell')")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 12, true).apply {
            query("SELECT brand, sgBeforeDiscountSgd FROM fill_ups").use {
                it.moveToFirst()
                assertEquals("Shell", it.getString(0))
                assertEquals(true, it.isNull(1))
            }
            close()
        }
    }

    /** Version 11 adds the petrol brand; fill-ups from before have none. */
    @Test
    fun version10FillUpsHaveNoBrand() {
        helper.createDatabase(dbName, 10).apply {
            execSQL("INSERT INTO fill_ups (id, profileId, date, country, odometerKm, sgLitres, sgCostSgd, noSpecialTrip, fullTank) VALUES (1, 1, 20354, 'SG', 162999, 9.713, 26.44, 0, 1)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 11, true).apply {
            query("SELECT odometerKm, brand FROM fill_ups").use {
                it.moveToFirst()
                assertEquals(162999, it.getInt(0))
                assertEquals(true, it.isNull(1))
            }
            close()
        }
    }

    /** Version 8 adds the log card fields; existing details keep everything and start without them. */
    @Test
    fun version7DetailsKeepTheirValues() {
        helper.createDatabase(dbName, 7).apply {
            execSQL("INSERT INTO bike_details (profileId, plate) VALUES (1, 'FAB1234X')")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 8, true).apply {
            query("SELECT plate, registrationDate, engineCc, colour FROM bike_details").use {
                it.moveToFirst()
                assertEquals("FAB1234X", it.getString(0))
                assertTrue(it.isNull(1) && it.isNull(2) && it.isNull(3))
            }
            close()
        }
    }

    @Test
    fun version2GainsAnEmptyDetailsTable() {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO profiles (id, name, kind, tankLitres, tripKm) VALUES (1, 'My bike', 'OWN', 14.0, 48.0)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 3, true).apply {
            query("SELECT COUNT(*) FROM bike_details").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            close()
        }
    }
}
