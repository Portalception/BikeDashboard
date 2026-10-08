package com.astheral.bikedashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FullBackupTest {
    private val backup = FullBackup(
        exportedAt = "2026-10-08T21:00:00",
        appVersion = "0.3.8",
        settings = SettingsBackup(mapOf("theme_mode" to "s:DARK", "pure_black" to "b:true", "primary_color" to "i:-16777216")),
        ideas = "- a widget\n- \"quotes\" and commas, too",
        categories = DEFAULT_CATEGORIES + ServiceCategory("CHAIN", "Chain clean", 9, hasInterval = true),
        bikes = listOf(
            BikeBackup(
                profile = Profile(id = 3, name = "Yamaha R3", kind = ProfileKind.OWN, tankLitres = 14.0, tripKm = 52.0),
                details = BikeDetails(profileId = 3, plate = "FBK1234X", roadTaxExpiry = LocalDate.of(2027, 8, 4), purchasePrice = 3800.0),
                fillUps = listOf(
                    FillUp(
                        id = 9, profileId = 3, date = LocalDate.of(2025, 3, 2), country = Country.MIXED, odometerKm = 130883,
                        sgLitres = 4.0, sgCostSgd = 10.4, myLitres = 8.0, myCostSgd = 9.6, rmPerSgd = 3.1847, reserveKm = 12.5,
                    ),
                ),
                services = listOf(ServiceRecord(id = 4, profileId = 3, date = LocalDate.of(2026, 1, 2), odometerKm = 150000, type = "CHAIN")),
                intervals = listOf(ServiceInterval(3, "CHAIN", everyKm = 800)),
            ),
        ),
    )

    @Test
    fun roundTripKeepsEverything() {
        val text = BackupJson.encodeToString(FullBackup.serializer(), backup)
        assertTrue(looksLikeFullBackup(text))
        assertEquals(backup, readFullBackup(text))
    }

    @Test
    fun csvIsNotAFullBackup() {
        assertFalse(looksLikeFullBackup("date,country,odometer_km\n2025-03-02,SG,1000"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun damagedBackupSaysSo() {
        readFullBackup("{\"format\":\"bike-dashboard-backup\",\"bikes\":[{\"profile\":")
    }

    @Test
    fun unknownFieldsFromNewerVersionsAreIgnored() {
        val text = BackupJson.encodeToString(FullBackup.serializer(), backup).replaceFirst("{", "{\"somethingNew\": 1,")
        assertEquals(backup, readFullBackup(text))
    }

    @Test
    fun restoresItsOwnCategories() {
        assertEquals(backup.categories, backup.restoredCategories())
    }

    @Test
    fun olderBackupsGetTheBuiltInCategoriesPlusAnyUnknownOnes() {
        // As saved before categories could be changed: no list, and types were fixed names.
        val old = backup.copy(categories = emptyList())
        val restored = old.restoredCategories()
        assertEquals(DEFAULT_CATEGORIES, restored.take(DEFAULT_CATEGORIES.size))
        val chain = restored.last()
        assertEquals("CHAIN", chain.id)
        assertEquals("Chain", chain.name)
        assertTrue(chain.hasInterval) // it has an interval in the backup
        val text = """{"format":"bike-dashboard-backup","bikes":[{"profile":{"id":1,"name":"R3","kind":"OWN","tankLitres":14.0,"tripKm":48.0},
            "services":[{"profileId":1,"date":"2025-01-10","type":"ENGINE_OIL"}]}]}"""
        val read = readFullBackup(text)
        assertEquals("ENGINE_OIL", read.bikes.single().services.single().type)
        assertEquals(DEFAULT_CATEGORIES, read.restoredCategories())
    }
}
