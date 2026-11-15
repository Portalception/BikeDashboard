package com.astheral.bikedashboard.ui.edit

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StepsTest {
    private val today = LocalDate.of(2026, 10, 7)

    @Test
    fun malaysiaFollowsPumpOrder() {
        assertEquals(
            listOf(Step.DATE, Step.WHERE, Step.RM_PAID, Step.RATE, Step.RM_CHARGED, Step.MY_LITRES, Step.ODOMETER, Step.FUEL_LEFT, Step.REVIEW),
            stepsFor(FillUpForm(date = today, country = Country.MY)),
        )
    }

    @Test
    fun singapore() {
        assertEquals(
            listOf(Step.DATE, Step.WHERE, Step.SG_LITRES, Step.SG_PAID, Step.ODOMETER, Step.FUEL_LEFT, Step.REVIEW),
            stepsFor(FillUpForm(date = today, country = Country.SG)),
        )
    }

    @Test
    fun bothCountriesDoMalaysiaThenSingapore() {
        assertEquals(
            listOf(
                Step.DATE, Step.WHERE, Step.RM_PAID, Step.RATE, Step.RM_CHARGED, Step.MY_LITRES,
                Step.SG_LITRES, Step.SG_PAID, Step.ODOMETER, Step.FUEL_LEFT, Step.REVIEW,
            ),
            stepsFor(FillUpForm(date = today, country = Country.MIXED)),
        )
    }

    @Test
    fun oldSpreadsheetShapesGetTheirOwnCards() {
        val oldMy = FillUpForm(date = today, country = Country.MY, legacyMy = true)
        assertEquals(listOf(Step.DATE, Step.WHERE, Step.MY_LITRES, Step.LEGACY_MY_COST, Step.ODOMETER, Step.FUEL_LEFT, Step.REVIEW), stepsFor(oldMy))
        val oldMixed = FillUpForm(date = today, country = Country.MIXED, legacyMixed = true)
        assertEquals(listOf(Step.DATE, Step.WHERE, Step.UNSPLIT_LITRES, Step.UNSPLIT_COST, Step.ODOMETER, Step.FUEL_LEFT, Step.REVIEW), stepsFor(oldMixed))
    }

    @Test
    fun eachCardOnlyComplainsAboutItsOwnAnswer() {
        val form = FillUpForm(date = today, country = Country.MY, rmPaid = "70", rate = "", myLitres = "abc")
        assertNull(form.errorFor(Step.RM_PAID, null))
        assertEquals("Required", form.errorFor(Step.RATE, null))
        assertEquals("Not a number", form.errorFor(Step.MY_LITRES, null))
        assertNull(form.errorFor(Step.WHERE, null))
        assertNull(form.errorFor(Step.DATE, null))
        assertNull(form.errorFor(Step.FUEL_LEFT, null)) // optional
    }

    @Test
    fun firstStepWithErrorFindsTheEarliestCard() {
        val form = FillUpForm(date = today, country = Country.MY, rmPaid = "70", rate = "3.2", rmCharged = "", myLitres = "", odometer = "")
        assertEquals(Step.RM_CHARGED, form.firstStepWithError(null))
        val done = form.copy(rmCharged = "64", myLitres = "12.5", odometer = "164200")
        assertNull(done.firstStepWithError(null))
    }

    @Test
    fun badBarsLeftStopsTheFuelCard() {
        val form = FillUpForm(date = today, country = Country.SG, barsLeft = "1.5")
        assertEquals("Whole number of bars", form.errorFor(Step.FUEL_LEFT, null))
    }

    @Test
    fun commonAmountsAreTheMostFrequentInOrder() {
        fun fill(rm: Double?) = FillUp(date = today, country = Country.MY, odometerKm = 1, rmPaid = rm)
        val fills = listOf(70.0, 70.0, 70.0, 60.0, 60.0, 80.0, 80.0, 50.0, 30.0, null).map(::fill)
        assertEquals(listOf(50.0, 60.0, 70.0, 80.0), commonRmPaid(fills))
    }

    @Test
    fun brandMenuHasTheMainThreeAndTheRestUnderOthers() {
        assertEquals(listOf("Shell", "Esso", "SPC"), SG_MAIN_BRANDS)
        assertEquals(listOf("Caltex", "Sinopec"), SG_OTHER_BRANDS)
    }

    @Test
    fun brandIsSavedForSingaporeOnly() {
        val sg = FillUpForm(date = today, country = Country.SG, sgLitres = "10", sgCost = "28", odometer = "1000", brand = "Shell")
        assertEquals("Shell", (sg.toFillUp(null) as FormResult.Ok).fillUp.brand)
        assertEquals(null, (sg.copy(brand = " ").toFillUp(null) as FormResult.Ok).fillUp.brand)
        val my = sg.withCountry(Country.MY).copy(myLitres = "10", rate = "3.2", rmCharged = "30")
        assertEquals(null, (my.toFillUp(null) as FormResult.Ok).fillUp.brand)
    }
}
