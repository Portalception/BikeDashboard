package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.calc.DashCard.COST_PER_DAY
import com.astheral.bikedashboard.calc.DashCard.COST_PER_KM
import com.astheral.bikedashboard.calc.DashCard.ECONOMY
import com.astheral.bikedashboard.calc.DashCard.KM_PER_DAY
import com.astheral.bikedashboard.calc.DashCard.PRICE
import com.astheral.bikedashboard.calc.DashCard.SAVINGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCardsTest {

    @Test
    fun defaultOrderShowsEverything() {
        val d = CardLayout()
        assertEquals(listOf(ECONOMY, KM_PER_DAY, COST_PER_DAY, COST_PER_KM, PRICE, SAVINGS), d.visible)
        assertTrue(d.isDefault)
        assertEquals("ECONOMY,KM_PER_DAY,COST_PER_DAY,COST_PER_KM,PRICE,SAVINGS", d.format())
    }

    @Test
    fun nothingSavedMeansDefault() {
        assertEquals(CardLayout(), CardLayout.parse(null))
        assertEquals(CardLayout(), CardLayout.parse(""))
        assertEquals(CardLayout(), CardLayout.parse("   "))
    }

    @Test
    fun formatAndParseRoundTrip() {
        val layout = CardLayout(listOf(SAVINGS, PRICE, ECONOMY, KM_PER_DAY, COST_PER_KM, COST_PER_DAY), setOf(PRICE, COST_PER_KM))
        assertEquals("SAVINGS,!PRICE,ECONOMY,KM_PER_DAY,!COST_PER_KM,COST_PER_DAY", layout.format())
        assertEquals(layout, CardLayout.parse(layout.format()))
    }

    @Test
    fun unknownAndRepeatedNamesAreSkippedAndMissingOnesAppended() {
        val layout = CardLayout.parse("PRICE, FUTURE_CARD,!ECONOMY,PRICE,!GONE")
        assertEquals(listOf(PRICE, ECONOMY, KM_PER_DAY, COST_PER_DAY, COST_PER_KM, SAVINGS), layout.order)
        assertEquals(setOf(ECONOMY), layout.hidden)
    }

    @Test
    fun everythingHiddenShowsEverything() {
        val all = DashCard.entries.joinToString(",") { "!${it.name}" }
        assertEquals(CardLayout(), CardLayout.parse(all))
    }

    @Test
    fun moveReorders() {
        val d = CardLayout()
        assertEquals(listOf(KM_PER_DAY, COST_PER_DAY, ECONOMY, COST_PER_KM, PRICE, SAVINGS), d.move(ECONOMY, 2).order)
        assertEquals(listOf(SAVINGS, ECONOMY, KM_PER_DAY, COST_PER_DAY, COST_PER_KM, PRICE), d.move(SAVINGS, 0).order)
        assertEquals(listOf(KM_PER_DAY, COST_PER_DAY, COST_PER_KM, PRICE, SAVINGS, ECONOMY), d.move(ECONOMY, 99).order)
        assertEquals(d, d.move(PRICE, 4))
    }

    @Test
    fun lastVisibleCardCannotBeHidden() {
        var l = CardLayout()
        DashCard.entries.drop(1).forEach { l = l.setShown(it, false) }
        assertEquals(listOf(ECONOMY), l.visible)
        assertFalse(l.canHide(ECONOMY))
        assertEquals(l, l.setShown(ECONOMY, false))
        assertTrue(l.canHide(PRICE))
        assertEquals(listOf(ECONOMY, PRICE), l.setShown(PRICE, true).visible)
    }
}
