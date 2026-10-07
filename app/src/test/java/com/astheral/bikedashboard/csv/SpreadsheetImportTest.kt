package com.astheral.bikedashboard.csv

import com.astheral.bikedashboard.calc.computeStats
import com.astheral.bikedashboard.data.Country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

class SpreadsheetImportTest {

    private val header =
        "No,Date,Country, RM Paid , SGD Conversion ,RM Price,Mileage (KM),Bars Left,Petrol Pumped (L), Price ," +
            "No Exit, Petrol Price (Per L) ,Distance Travelled (KM),Days Passed,KM/Day,Fuel Economy (KM/L)," +
            "Cost Per Day, Cost Per Km , Price Difference , Savings , Estimated Max "

    // Rows 1, 6, 27, 81, 117 and two empty template rows, copied from the spreadsheet
    private val sample = listOf(
        header,
        "1,2-Mar-2025,SG, \$ -   , \$ -   , \$ -   ,130883,,11.9, \$ 26.90 ,, \$ 2.26 ,NA,NA,NA,NA,NA, NA , \$ -   , \$ -   ,",
        "6,29-Mar-2025,SG, \$ -   , \$ -   , \$ -   ,132072,-31.20,12.487, \$ 27.88 ,, \$ 2.23 ,276,12,23.00,22.10,2.323333333, \$ 0.1010 , \$ -   , \$ -   ,-62.95",
        "27,23-Jul-2025,MY, \$ -   , \$ -   , \$ -   ,137500,6,1.09, \$ 1.06 ,Y, \$ 0.97 ,34,1,34.00,31.19,1.06, \$ 0.0312 , \$ 1.60 , \$ 1.60 ,",
        "81,2-Apr-2026,MY/SG, \$ 60.00 , \$ 19.14 , \$ 60.00 ,152451,-23.10,14.588, \$ 25.78 ,, \$ 1.77 ,361,4,90.25,24.75,6.445, \$ 0.0714 , \$ 9.83 , \$ 6.40 ,-19.31",
        "117,3-Oct-2026,MY, \$ 60.00 , \$ 18.84 , \$ 32.70 ,163903,3.00,6.54, \$ 10.27 ,, \$ 1.57 ,163,1,163.00,24.92,10.2678, \$ 0.0630 , \$ 5.70 , \$ 2.67 ,",
        "118,,MY, \$ -   , \$ -   , \$ -   ,,,,  ,,  ,,,,,,  ,  ,  ,",
        "119,,MY, \$ -   , \$ -   , \$ -   ,,,,  ,,  ,,,,,,  ,  ,  ,",
    ).joinToString("\r\n")

    @Test
    fun skipsEmptyTemplateRows() {
        val result = readSpreadsheet(sample)
        assertEquals(listOf(1, 6, 27, 81, 117), result.rows.map { it.rowNumber })
        assertTrue(result.problems.isEmpty())
    }

    @Test
    fun readsSingaporeRow() {
        val row1 = readSpreadsheet(sample).rows[0].fillUp
        assertEquals(LocalDate.of(2025, 3, 2), row1.date)
        assertEquals(Country.SG, row1.country)
        assertEquals(130883, row1.odometerKm)
        assertEquals(11.9, row1.sgLitres!!, 0.0)
        assertEquals(26.90, row1.sgCostSgd!!, 0.0)
        assertNull(row1.myLitres)
        assertNull(row1.rmPerSgd)
        assertNull(row1.barsLeft)
        assertNull(row1.reserveKm)
        assertNull(row1.importedSavingsSgd)
    }

    @Test
    fun negativeBarsLeftBecomesKmOnReserve() {
        val row6 = readSpreadsheet(sample).rows[1].fillUp
        assertNull(row6.barsLeft)
        assertEquals(31.2, row6.reserveKm!!, 1e-9)
        assertEquals(-62.95, row6.estimatedMax!!, 0.0)
    }

    @Test
    fun noExitBecomesNoSpecialTrip() {
        val row27 = readSpreadsheet(sample).rows[2].fillUp
        assertTrue(row27.noSpecialTrip)
        assertEquals(6, row27.barsLeft)
        assertEquals(1.60, row27.importedSavingsSgd!!, 0.0)
        assertFalse(readSpreadsheet(sample).rows[4].fillUp.noSpecialTrip)
    }

    @Test
    fun oldMixedRowKeepsOneTotal() {
        val row81 = readSpreadsheet(sample).rows[3].fillUp
        assertEquals(Country.MIXED, row81.country)
        assertEquals(14.588, row81.unsplitLitres!!, 0.0)
        assertEquals(25.78, row81.unsplitCostSgd!!, 0.0)
        assertNull(row81.sgLitres)
        assertNull(row81.myLitres)
        assertEquals(60.0 / 19.14, row81.rmPerSgd!!, 1e-9)
    }

    @Test
    fun malaysiaRowWorksOutRateFromRmPaidAndSgdConversion() {
        val row117 = readSpreadsheet(sample).rows[4].fillUp
        assertEquals(6.54, row117.myLitres!!, 0.0)
        assertEquals(32.70 / (60.0 / 18.84), row117.myCostSgd!!, 1e-9) // unrounded, ≈ 10.27
        assertEquals(60.0, row117.rmPaid!!, 0.0)
        assertEquals(60.0 / 18.84, row117.rmPerSgd!!, 1e-9)
        assertEquals(32.70, row117.rmCharged!!, 0.0)
        assertEquals(3, row117.barsLeft)
        assertEquals(2.67, row117.importedSavingsSgd!!, 0.0)
    }

    @Test
    fun keepsSheetFiguresForChecking() {
        val row6 = readSpreadsheet(sample).rows[1].sheet
        assertEquals(276, row6.distanceKm)
        assertEquals(12L, row6.days)
        assertEquals(22.10, row6.kmPerLitre!!, 0.0)
        assertNull(readSpreadsheet(sample).rows[0].sheet.distanceKm) // "NA"
    }

    @Test
    fun badRowIsReportedAndOthersStillImport() {
        val broken = sample.replace("163903,3.00", "16x903,3.00")
        val result = readSpreadsheet(broken)
        assertEquals(4, result.rows.size)
        assertEquals(117, result.problems.single().rowNumber)
    }

    @Test
    fun missingColumnsAreReported() {
        val result = readSpreadsheet("No,Date,Country\n1,2-Mar-2025,SG")
        assertTrue(result.rows.isEmpty())
        assertTrue(result.problems.single().message.startsWith("Missing columns"))
        assertFalse(looksLikeSpreadsheet("No,Date,Country\n1,2-Mar-2025,SG"))
        assertTrue(looksLikeSpreadsheet(sample))
    }

    @Test
    fun readsSpreadsheetNumberFormats() {
        assertEquals(26.90, number(" \$ 26.90 ")!!, 0.0)
        assertEquals(0.0, number(" \$ -   ")!!, 0.0)
        assertEquals(1234.5, number("\$ 1,234.50")!!, 0.0)
        assertEquals(-2.10, number("\$ (2.10)")!!, 0.0)
        assertEquals(-31.2, number("-31.20")!!, 0.0)
        assertNull(number("NA"))
        assertNull(number("  "))
    }

    @Test
    fun handlesQuotedFields() {
        assertEquals(listOf(listOf("a", "1,234", "say \"hi\""), listOf("b")), parseCsv("a,\"1,234\",\"say \"\"hi\"\"\"\r\nb\r\n"))
    }

    // --- The real spreadsheet (skipped if data/ isn't on this computer) ---

    private val realFile = File("../data/R3 Bike Dashboard - Fueling Record.csv")

    private fun real(): SheetImport {
        assumeTrue("Spreadsheet not found at ${realFile.absolutePath}", realFile.exists())
        return readSpreadsheet(realFile.readText())
    }

    @Test
    fun realSpreadsheetHas117FillUps() {
        val result = real()
        assertTrue(result.problems.toString(), result.problems.isEmpty())
        assertEquals(117, result.rows.size)
        assertEquals((1..117).toList(), result.rows.map { it.rowNumber })
        assertEquals(LocalDate.of(2025, 3, 2), result.rows.first().fillUp.date)
        assertEquals(LocalDate.of(2026, 10, 3), result.rows.last().fillUp.date)
    }

    @Test
    fun realSpreadsheetCountries() {
        val byCountry = real().rows.groupingBy { it.fillUp.country }.eachCount()
        assertEquals(17, byCountry[Country.MIXED])
        assertEquals(117, byCountry.values.sum())
    }

    @Test
    fun appCalculationsMatchRealSpreadsheet() {
        val rows = real().rows
        val stats = computeStats(rows.map { it.fillUp })
        val mismatches = rows.zip(stats).flatMap { (row, s) ->
            val sheet = row.sheet
            listOfNotNull(
                "distance".takeIf { sheet.distanceKm != s.distanceKm },
                "days".takeIf { sheet.days != s.days },
                "km/L".takeIf { differs(sheet.kmPerLitre, s.kmPerLitre, 0.005) },
                "cost/km".takeIf { differs(sheet.costPerKm, s.costPerKm, 0.00005) },
                "cost/day".takeIf { differs(sheet.costPerDay, s.costPerDay, 0.0051) },
                "price/L".takeIf { differs(sheet.pricePerLitre, s.pricePerLitre, 0.005) },
            ).map { "row ${row.rowNumber}: $it" }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    private fun differs(sheet: Double?, app: Double?, tolerance: Double) =
        if (sheet == null || app == null) sheet != app else abs(sheet - app) > tolerance
}
