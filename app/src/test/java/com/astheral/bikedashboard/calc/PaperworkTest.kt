package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.BikeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaperworkTest {
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun dueFromTheLeadTimeOnwards() {
        assertFalse(expiryDue(today.plusDays(31), 30, today))
        assertTrue(expiryDue(today.plusDays(30), 30, today))
        assertTrue(expiryDue(today, 30, today))
        assertTrue(expiryDue(today.minusDays(200), 30, today)) // still not renewed
        assertFalse(expiryDue(null, 30, today))
    }

    @Test
    fun onlyItemsSwitchedOnAndWithADate() {
        val details = BikeDetails(
            profileId = 1,
            coeExpiry = today.plusDays(10),
            roadTaxExpiry = today.minusDays(2),
            insuranceExpiry = today.plusDays(100),
        )
        val due = legalDue(details, setOf(LegalItem.COE, LegalItem.ROAD_TAX, LegalItem.INSURANCE, LegalItem.INSPECTION), 30, today)
        assertEquals(listOf(LegalItem.COE, LegalItem.ROAD_TAX), due.map { it.item })
        assertEquals(10L, due[0].daysLeft)
        assertTrue(due[1].expired)

        assertEquals(listOf(LegalItem.ROAD_TAX), legalDue(details, setOf(LegalItem.ROAD_TAX), 30, today).map { it.item })
        assertTrue(legalDue(details, emptySet(), 30, today).isEmpty())
    }

    @Test
    fun summaries() {
        assertEquals("Expires in 12 days", LegalDue(LegalItem.COE, today, 12).summary())
        assertEquals("Expires tomorrow", LegalDue(LegalItem.COE, today, 1).summary())
        assertEquals("Expires today", LegalDue(LegalItem.COE, today, 0).summary())
        assertEquals("Expired yesterday", LegalDue(LegalItem.COE, today, -1).summary())
        assertEquals("Expired 3 days ago", LegalDue(LegalItem.COE, today, -3).summary())
    }

    @Test
    fun editsNumberAndExpiryTogether() {
        val d = LegalItem.INSURANCE.with(BikeDetails(profileId = 1), "POL-1", today)
        assertEquals("POL-1", LegalItem.INSURANCE.number(d))
        assertEquals(today, LegalItem.INSURANCE.expiry(d))
        val r = LegalItem.ROAD_TAX.with(d, "ignored", today.plusYears(1))
        assertEquals(today.plusYears(1), r.roadTaxExpiry)
        assertEquals("POL-1", r.insurancePolicy)
    }

    @Test
    fun expiredOnlyOnceTheDayHasPassed() {
        assertFalse(isExpired(null, today))
        assertFalse(isExpired(today, today))
        assertFalse(isExpired(today.plusDays(1), today))
        assertTrue(isExpired(today.minusDays(1), today))
    }
}
