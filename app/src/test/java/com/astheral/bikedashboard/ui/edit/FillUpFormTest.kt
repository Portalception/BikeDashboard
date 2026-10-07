package com.astheral.bikedashboard.ui.edit

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FillUpFormTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun ok(form: FillUpForm, base: FillUp? = null) = (form.toFillUp(base) as FormResult.Ok).fillUp
    private fun errors(form: FillUpForm) = (form.toFillUp(null) as FormResult.Invalid).errors

    private val previous = FillUp(
        date = LocalDate.of(2026, 10, 3), country = Country.MY, odometerKm = 163903,
        myLitres = 6.54, myCostSgd = 10.27, rmPaid = 60.0, rmPerSgd = 3.1847, rmCharged = 32.70,
    )

    private val myForm = FillUpForm(
        date = today, country = Country.MY, odometer = "164200",
        myLitres = "12.5", rmPaid = "70", rate = "3.2", rmCharged = "64.00",
    )

    @Test
    fun malaysiaCostIsPumpChargeDividedByRate() {
        val fill = ok(myForm)
        assertEquals(20.0, fill.myCostSgd!!, 1e-9)
        assertEquals(70.0, fill.rmPaid!!, 0.0)
        assertEquals(3.2, fill.rmPerSgd!!, 0.0)
        assertEquals(64.0, fill.rmCharged!!, 0.0)
        assertNull(fill.sgLitres)
    }

    @Test
    fun singaporeFillUp() {
        val fill = ok(FillUpForm(date = today, country = Country.SG, odometer = "164200", sgLitres = "12", sgCost = "30.50"))
        assertEquals(12.0, fill.sgLitres!!, 0.0)
        assertEquals(30.5, fill.sgCostSgd!!, 0.0)
        assertNull(fill.myLitres)
        assertNull(fill.rmPerSgd)
    }

    @Test
    fun mixedFillUpHasBothParts() {
        val form = myForm.copy(country = Country.MIXED, sgLitres = "3", sgCost = "8.10", noSpecialTrip = true)
        val fill = ok(form)
        assertEquals(3.0, fill.sgLitres!!, 0.0)
        assertEquals(12.5, fill.myLitres!!, 0.0)
        assertEquals(20.0, fill.myCostSgd!!, 1e-9)
        assertTrue(fill.noSpecialTrip)
    }

    @Test
    fun rmPaidIsOptional() {
        assertNull(ok(myForm.copy(rmPaid = "")).rmPaid)
    }

    @Test
    fun commaDecimalsWork() {
        assertEquals(12.5, ok(myForm.copy(myLitres = "12,5")).myLitres!!, 0.0)
    }

    @Test
    fun missingAndBadFieldsAreReported() {
        val e = errors(myForm.copy(odometer = "", myLitres = "abc", rate = "0"))
        assertEquals("Required", e[Field.ODOMETER])
        assertEquals("Not a number", e[Field.MY_LITRES])
        assertEquals("Must be more than 0", e[Field.RATE])
        assertFalse(Field.RM_CHARGED in e)
    }

    @Test
    fun onlyFieldsForChosenCountryAreRequired() {
        val e = errors(FillUpForm(date = today, country = Country.SG, odometer = "1"))
        assertEquals(setOf(Field.SG_LITRES, Field.SG_COST), e.keys)
    }

    @Test
    fun barsAndReserve() {
        val fill = ok(myForm.copy(barsLeft = "2"))
        assertEquals(2, fill.barsLeft)
        assertNull(fill.reserveKm)
        assertEquals(31.2, ok(myForm.copy(reserveKm = "31.2")).reserveKm!!, 0.0)
        assertEquals("Whole number of bars", errors(myForm.copy(barsLeft = "1.5"))[Field.BARS_LEFT])
    }

    @Test
    fun editingKeepsIdAndHiddenSpreadsheetValues() {
        val base = previous.copy(id = 42, estimatedMax = -20.0, importedSavingsSgd = 2.67)
        val fill = ok(FillUpForm.from(base).copy(barsLeft = "3"), base)
        assertEquals(42L, fill.id)
        assertEquals(-20.0, fill.estimatedMax!!, 0.0)
        assertEquals(2.67, fill.importedSavingsSgd!!, 0.0)
        assertEquals(3, fill.barsLeft)
    }

    @Test
    fun formRoundTripsAFillUp() {
        val base = previous.copy(id = 7, reserveKm = 12.5)
        assertEquals(base, ok(FillUpForm.from(base), base).copy(myCostSgd = base.myCostSgd))
    }

    @Test
    fun oldMalaysiaFillUpWithoutRmEditsSgdCost() {
        val old = FillUp(id = 3, date = LocalDate.of(2025, 3, 11), country = Country.MY, odometerKm = 131572, myLitres = 12.302, myCostSgd = 15.95)
        val form = FillUpForm.from(old)
        assertTrue(form.legacyMy)
        assertEquals("15.95", form.legacyMyCost)
        val fill = ok(form.copy(legacyMyCost = "16.00"), old)
        assertEquals(16.0, fill.myCostSgd!!, 0.0)
        assertNull(fill.rmPerSgd)
    }

    @Test
    fun oldMixedFillUpKeepsOneTotalAndItsRmRecord() {
        val old = FillUp(
            id = 9, date = LocalDate.of(2026, 4, 2), country = Country.MIXED, odometerKm = 152451,
            unsplitLitres = 14.588, unsplitCostSgd = 25.78, rmPaid = 60.0, rmPerSgd = 3.1348, rmCharged = 60.0,
        )
        val form = FillUpForm.from(old)
        assertTrue(form.legacyMixed)
        assertFalse(form.hasSgPart)
        val fill = ok(form, old)
        assertEquals(old, fill)
    }

    @Test
    fun changingCountryOfOldFillUpMakesItNormal() {
        val old = FillUp(id = 3, date = LocalDate.of(2025, 3, 11), country = Country.MY, odometerKm = 131572, myLitres = 12.302, myCostSgd = 15.95)
        val form = FillUpForm.from(old).withCountry(Country.MIXED)
        assertFalse(form.legacyMy)
        assertTrue(form.hasSgPart)
    }

    @Test
    fun rateShownWithFourDecimals() {
        assertEquals("3.1847", FillUpForm.rateText(60.0 / 18.84))
    }

    // --- Warnings ---

    private fun warnings(fill: FillUp) = warningsFor(fill, listOf(previous), today)

    @Test
    fun normalFillUpHasNoWarnings() {
        assertEquals(emptyList<String>(), warnings(ok(myForm)))
    }

    @Test
    fun odometerLowerThanPrevious() {
        assertTrue(warnings(ok(myForm.copy(odometer = "163900"))).any { "isn't higher" in it })
    }

    @Test
    fun odometerHigherThanNextWhenAddingOlderFillUp() {
        val older = ok(myForm.copy(odometer = "170000")).copy(date = LocalDate.of(2026, 10, 1))
        assertTrue(warnings(older).any { "higher than the next" in it })
    }

    @Test
    fun sameDayFillUpCountsAsPrevious() {
        val earlierToday = previous.copy(date = today, odometerKm = 164250)
        val w = warningsFor(ok(myForm.copy(odometer = "164000")), listOf(previous, earlierToday), today)
        assertTrue(w.any { "164250 km on 7 Oct 2026" in it })
    }

    @Test
    fun tooManyLitres() {
        assertTrue(warnings(ok(myForm.copy(myLitres = "16"))).any { "14.0 L tank holds" in it })
    }

    @Test
    fun tankSizeComesFromTheBike() {
        val fill = ok(myForm.copy(myLitres = "16"))
        assertTrue(warningsFor(fill, listOf(previous), today, tankLitres = 17.0).none { "tank" in it })
        assertTrue(warningsFor(fill, listOf(previous), today, tankLitres = 12.0).any { "12.0 L tank holds" in it })
        // A full tank can take a little extra when squeezed.
        assertTrue(warningsFor(ok(myForm.copy(myLitres = "14.4")), listOf(previous), today, tankLitres = 14.0).none { "tank" in it })
    }

    @Test
    fun futureDate() {
        assertTrue(warnings(ok(myForm.copy(date = today.plusDays(1)))).any { "future" in it })
    }

    @Test
    fun pumpChargedMoreThanPaid() {
        assertTrue(warnings(ok(myForm.copy(rmPaid = "50"))).any { "charged more" in it })
    }

    @Test
    fun oddRate() {
        assertTrue(warnings(ok(myForm.copy(rate = "31.8", rmCharged = "640"))).any { "rate" in it })
    }

    @Test
    fun longDistanceAndOddEconomy() {
        val w = warnings(ok(myForm.copy(odometer = "164600")))
        assertTrue(w.any { "more than a tank usually lasts" in it })
        assertTrue(w.any { "km/L" in it })
    }

    @Test
    fun bothBarsAndReserve() {
        assertTrue(warnings(ok(myForm.copy(barsLeft = "1", reserveKm = "5"))).any { "Both" in it })
    }

    @Test
    fun duplicate() {
        assertTrue(warningsFor(previous, listOf(previous.copy(id = 99)), today).any { "already" in it })
    }
}
