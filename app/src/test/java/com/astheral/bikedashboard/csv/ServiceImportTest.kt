package com.astheral.bikedashboard.csv

import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.DEFAULT_CATEGORIES
import com.astheral.bikedashboard.data.DefaultCategory
import com.astheral.bikedashboard.data.ServiceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ServiceImportTest {
    // As Google Sheets saves it, including the summary columns that repeat "Date" on the right.
    private val sheet = """
        No.,Date,Mileage (Km),Type,Description,Cost,Currency,,Summary,ID,Date,Mileage,Days Since
        1,10-Jan-2025,130000,Major Servicing,Complete Overhaul, $ -   ,SGD,,Last Major Servicing,25,7-Aug-2025,138806,419
        20,"8-Jun-2025","135,492",Bike Wash,Mr Moto Spa, $ 89.00 ,MYR,,,,,,
        45,15-Jul-2026,159057,Engine Oil,Motul 10w-45,,SGD,,,,,,
        46,7-Aug-2026,160543,Strange Type,Radiator Fan Replacement,128,SGD,,,,,,
        48,,,-,,,,,,,,,
        49,soon,1,Repair,,,SGD,,,,,,
    """.trimIndent()

    @Test
    fun recognisesTheSheet() {
        assertTrue(looksLikeServiceSheet(sheet))
        assertFalse(looksLikeServiceSheet("date,country,odometer_km\n"))
    }

    @Test
    fun readsRowsAndSkipsTemplateRows() {
        val result = readServiceSheet(sheet, profileId = 7)
        assertEquals(4, result.records.size)
        val first = result.records[0]
        assertEquals(LocalDate.of(2025, 1, 10), first.date) // the first Date column, not the summary's
        assertEquals(130000, first.odometerKm)
        assertEquals(DefaultCategory.MAJOR_SERVICING, first.type)
        assertNull(first.cost) // "$ -" is blank
        assertEquals(7L, first.profileId)

        val wash = result.records[1]
        assertEquals(135492, wash.odometerKm)
        assertEquals(89.0, wash.cost!!, 0.0)
        assertEquals(Currency.MYR, wash.currency)

        assertEquals(listOf(49), result.problems.map { it.rowNumber })
    }

    @Test
    fun unknownTypesBecomeNewCategories() {
        val result = readServiceSheet(sheet, profileId = 7)
        assertEquals("STRANGE_TYPE", result.records[3].type)
        assertEquals(listOf(ServiceCategory("STRANGE_TYPE", "Strange Type", 9)), result.newCategories)
        // A row that can't be read (#49, "Repair" is known anyway) adds nothing.
        assertEquals(1, result.newCategories.size)
    }

    @Test
    fun matchesRenamedCategoriesByName() {
        val renamed = DEFAULT_CATEGORIES.map { if (it.id == DefaultCategory.ENGINE_OIL) it.copy(name = "Oil change") else it }
        val text = """
            Date,Mileage (Km),Type,Description,Cost,Currency
            1-Jan-2026,100,oil change,,,SGD
            2-Jan-2026,200,-,,,SGD
            3-Jan-2026,300,Chain,,,SGD
            4-Jan-2026,400,chain,,,SGD
        """.trimIndent()
        val result = readServiceSheet(text, profileId = 1, categories = renamed)
        assertEquals(listOf(DefaultCategory.ENGINE_OIL, DefaultCategory.OTHER, "CHAIN", "CHAIN"), result.records.map { it.type })
        assertEquals(listOf("Chain"), result.newCategories.map { it.name })
    }
}
