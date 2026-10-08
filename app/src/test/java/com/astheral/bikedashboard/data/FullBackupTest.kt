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
}
