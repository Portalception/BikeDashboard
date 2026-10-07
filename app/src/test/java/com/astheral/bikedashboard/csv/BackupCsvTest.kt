package com.astheral.bikedashboard.csv

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class BackupCsvTest {
    private val fillUps = listOf(
        FillUp(date = LocalDate.of(2025, 3, 29), country = Country.SG, odometerKm = 132072, sgLitres = 12.487, sgCostSgd = 27.88, reserveKm = 31.2, estimatedMax = -62.95),
        FillUp(
            date = LocalDate.of(2026, 10, 3), country = Country.MY, odometerKm = 163903,
            myLitres = 6.54, myCostSgd = 32.70 / (60.0 / 18.84), rmPaid = 60.0, rmPerSgd = 60.0 / 18.84, rmCharged = 32.70,
            barsLeft = 3, importedSavingsSgd = 2.67,
        ),
        FillUp(date = LocalDate.of(2025, 7, 23), country = Country.MY, odometerKm = 137500, myLitres = 1.09, myCostSgd = 1.06, barsLeft = 0, noSpecialTrip = true),
        FillUp(date = LocalDate.of(2026, 4, 2), country = Country.MIXED, odometerKm = 152451, unsplitLitres = 14.588, unsplitCostSgd = 25.78, rmPaid = 60.0),
        FillUp(date = LocalDate.of(2026, 10, 10), country = Country.MIXED, odometerKm = 164300, sgLitres = 4.0, sgCostSgd = 11.0, myLitres = 10.0, myCostSgd = 15.0),
    )

    private fun withoutIds(list: List<FillUp>) = list.map { it.copy(id = 0) }.sortedWith(compareBy({ it.date }, { it.odometerKm }))

    @Test
    fun exportThenImportGivesBackTheSameFillUps() {
        val text = writeBackup(fillUps)
        val back = readBackup(text)
        assertTrue(back.problems.isEmpty())
        assertEquals(withoutIds(fillUps), back.fillUps)
    }

    @Test
    fun barsAndReserveUseSpreadsheetSignRule() {
        val lines = writeBackup(fillUps).lines()
        assertTrue(lines[1].contains(",-31.2,")) // 31.2 km on reserve
        assertTrue(lines.any { it.startsWith("2026-10-03") && it.contains(",3,,") })
    }

    @Test
    fun recognisesItsOwnFormat() {
        val text = writeBackup(fillUps)
        assertTrue(looksLikeBackup(text))
        assertFalse(looksLikeSpreadsheet(text))
        assertFalse(looksLikeBackup("No,Date,Country\n"))
    }

    @Test
    fun badLineIsReported() {
        val text = writeBackup(fillUps).replace("163903", "abc")
        val back = readBackup(text)
        assertEquals(4, back.fillUps.size)
        assertEquals(1, back.problems.size)
    }

    @Test
    fun emptyBackupHasJustTheHeader() {
        val text = writeBackup(emptyList())
        assertEquals(1, text.lines().count { it.isNotEmpty() })
        assertTrue(readBackup(text).fillUps.isEmpty())
    }

    @Test
    fun realSpreadsheetSurvivesBackupRoundTrip() {
        val file = File("../data/R3 Bike Dashboard - Fueling Record.csv")
        assumeTrue(file.exists())
        val imported = readSpreadsheet(file.readText()).rows.map { it.fillUp }
        assertEquals(withoutIds(imported), readBackup(writeBackup(imported)).fillUps)
    }
}
