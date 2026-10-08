package com.astheral.bikedashboard.csv

import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.ServiceType
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
        assertEquals(ServiceType.MAJOR_SERVICING, first.type)
        assertNull(first.cost) // "$ -" is blank
        assertEquals(7L, first.profileId)

        val wash = result.records[1]
        assertEquals(135492, wash.odometerKm)
        assertEquals(89.0, wash.cost!!, 0.0)
        assertEquals(Currency.MYR, wash.currency)

        assertEquals(ServiceType.OTHER, result.records[3].type)
        assertEquals(listOf(49), result.problems.map { it.rowNumber })
    }
}
