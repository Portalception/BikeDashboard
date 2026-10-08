package com.astheral.bikedashboard.ui.service

import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.DefaultCategory
import com.astheral.bikedashboard.data.ServiceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ServiceFormTest {
    private val day = LocalDate.of(2026, 3, 10)

    @Test
    fun everythingButCategoryAndDateIsOptional() {
        val r = ServiceForm(type = DefaultCategory.BIKE_WASH, date = day).toRecord(0, 1)!!
        assertNull(r.odometerKm)
        assertNull(r.cost)
        assertFalse(r.odometerEstimated)
    }

    @Test
    fun readsTheAnswers() {
        val form = ServiceForm(
            type = DefaultCategory.ENGINE_OIL, date = day, description = " Motul ", cost = "45,5",
            currency = Currency.MYR, odometer = "12,580", odometerEstimated = true,
        )
        val r = form.toRecord(7, 2)!!
        assertEquals(12580, r.odometerKm) // a comma in km is thousands
        assertEquals(45.5, r.cost!!, 0.0) // a comma in money is decimals
        assertEquals("Motul", r.description)
        assertTrue(r.odometerEstimated)
        assertEquals(form, ServiceForm.from(r).copy(description = " Motul ", cost = "45,5", odometer = "12,580"))
    }

    @Test
    fun aClearedOdometerIsNotEstimated() {
        assertFalse(ServiceForm(odometer = "", odometerEstimated = true).toRecord(0, 1)!!.odometerEstimated)
    }

    @Test
    fun badNumbersStopOnTheirCard() {
        val form = ServiceForm(cost = "abc", odometer = "-5")
        assertEquals(ServiceStep.COST, form.firstStepWithError())
        assertTrue(form.errorFor(ServiceStep.ODOMETER) != null)
        assertNull(form.toRecord(0, 1))
    }

    @Test
    fun recentDescriptionsNewestFirstWithoutRepeats() {
        fun r(date: String, type: String, d: String) = ServiceRecord(profileId = 1, date = LocalDate.parse(date), type = type, description = d)
        val records = listOf(
            r("2026-01-01", DefaultCategory.ENGINE_OIL, "Motul 10w-40"),
            r("2026-02-01", DefaultCategory.ENGINE_OIL, "Shell"),
            r("2026-03-01", DefaultCategory.ENGINE_OIL, "Motul 10w-40"),
            r("2026-03-02", DefaultCategory.BIKE_WASH, "Mr Moto Spa"),
            r("2026-03-03", DefaultCategory.ENGINE_OIL, ""),
        )
        assertEquals(listOf("Motul 10w-40", "Shell"), recentDescriptions(records, DefaultCategory.ENGINE_OIL))
    }
}
