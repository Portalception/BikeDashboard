package com.astheral.bikedashboard.logcard

import com.astheral.bikedashboard.data.BikeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** All values here are made up; they only follow the layout of a real LTA log card. */
class LogCardParserTest {
    /** How the PDF's text comes out of PDFBox: each label on its own line, its value on the next. */
    private val stacked = """
        Vehicle Registration Details
        Vehicle No.
        FAB1234X
        Make/ Model
        HONDA/CBR500R ABS
        Vehicle Scheme
        -
        Current Propellant
        Petrol
        Chassis No.
        MLHPC9999F0001234
        Vehicle Type
        Passenger
        Motorcycle/Autocycle/
        Moped
        Owner's Details
        Owner Name:
        JOHN TAN AH KOW
        Owner ID Type:
        Singapore NRIC
        NRIC/Passport/Company Cert No.:
        S0000000Z
        Registered Address
        BLK 1 SAMPLE ROAD #01-01
        SINGAPORE 000001
        Mailing Address:
        -
        Birth Date
        01 Jan 1990
        Registration Details
        Previous Vehicle No.:
        -
        Effective Date of Ownership:
        15 Jan 2024
        Original Registration Date:
        10 Feb 2018
        Registration Date:
        12 Feb 2018
        No. of Transfers:
        2
        IU Label No.:
        123456789
        OBU Label No.:
        -
        Vehicle Specifications
        Engine No.: Chassis No.:
        PC99E1234567 MLHPC9999F0001234
        Year of Manufacture:
        2017
        Primary Colour:
        Red
        Secondary Colour:
        Black
        Passenger Capacity:
        1
        Engine Capacity / Power Rating :
        471${" "}cc / -
        Maximum Power Output:
        -
        Open Market Value:
        ${'$'}5,100.50
        Additional Registration Fee Rate:
        15.00  %
        Vehicle Lifespan Expiry Date:
        No Lifespan
        COE No.:
        2018020100000123A
        COE Expiry Date:
        11 Feb 2028
        COE Category:
        D - Motorcycle
        Actual QP Paid
        ${'$'}1,234.00
        QP (Regn Cat):
        ${'$'}1,234.00
        Message:
        To renew the COE, the Prevailing Quota Premium payable is that of Category D.
        Printed on 01 Mar 2025 10:00:00
    """.trimIndent()

    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun readsEveryFieldFromStackedLayout() {
        val card = LogCardParser.parse(stacked)
        assertEquals("FAB1234X", card.plate)
        assertEquals("Honda", card.brand)
        assertEquals("CBR500R ABS", card.model)
        assertEquals("MLHPC9999F0001234", card.chassisNumber)
        assertEquals("PC99E1234567", card.engineNumber)
        assertEquals("123456789", card.iuNumber)
        assertEquals(LocalDate.of(2018, 2, 12), card.registrationDate)
        assertEquals(2017, card.manufactureYear)
        assertEquals(471, card.engineCc)
        assertEquals("Red", card.primaryColour)
        assertEquals("Black", card.secondaryColour)
        assertEquals("Red / Black", card.colour)
        assertEquals(5100.50, card.openMarketValue!!, 0.001)
        assertEquals(1234.0, card.coePaid!!, 0.001)
        assertEquals("2018020100000123A", card.coeNumber)
        assertEquals(LocalDate.of(2028, 2, 11), card.coeExpiry)
        assertNull(card.roadTaxExpiry)
        assertTrue(card.unreadable.isEmpty())
        assertTrue(card.issues(today).isEmpty())
    }

    @Test
    fun readsLabelAndValueOnTheSameLine() {
        val text = """
            Vehicle No. FAB1234X
            Make/ Model KTM/390 DUKE
            Chassis No. VBKJGJ409KC000001
            Registration Date: 12 Feb 2018
            Engine No.:
            Chassis No.:
            J409E000001
            Primary Colour: ORANGE
            Secondary Colour: -
            COE Expiry Date: 11 FEB 2028
            Road Tax Expiry Date: 11 Aug 2027
        """.trimIndent()
        val card = LogCardParser.parse(text)
        assertEquals("FAB1234X", card.plate)
        assertEquals("KTM", card.brand)
        assertEquals("390 DUKE", card.model)
        assertEquals("VBKJGJ409KC000001", card.chassisNumber)
        assertEquals(LocalDate.of(2018, 2, 12), card.registrationDate)
        assertEquals("Orange", card.primaryColour)
        assertNull(card.secondaryColour)
        assertEquals(LocalDate.of(2028, 2, 11), card.coeExpiry)
        assertEquals(LocalDate.of(2027, 8, 11), card.roadTaxExpiry)
    }

    @Test
    fun ownerDetailsAreNeverRead() {
        val card = LogCardParser.parse(stacked)
        val everything = card.toString() + card.previewRows()
        assertTrue("JOHN" !in everything.uppercase())
        assertTrue("S0000000Z" !in everything)
        assertTrue("SAMPLE ROAD" !in everything.uppercase())
    }

    @Test
    fun somethingElseReadsAsEmpty() {
        assertTrue(LogCardParser.parse("Invoice\nTotal: $12.00\nThank you").isEmpty)
    }

    @Test
    fun expiredCoeAndFutureRegistrationAreFlagged() {
        val text = stacked.replace("11 Feb 2028", "11 Feb 2025").replace("12 Feb 2018", "12 Feb 2027")
        val issues = LogCardParser.parse(text).issues(today)
        assertEquals(
            listOf(CardDate.REGISTRATION to LogCardIssue.Kind.IN_FUTURE, CardDate.COE_EXPIRY to LogCardIssue.Kind.EXPIRED),
            issues.map { it.date to it.kind },
        )
    }

    @Test
    fun expiringTodayIsNotExpired() {
        val text = stacked.replace("11 Feb 2028", "09 Oct 2026")
        assertTrue(LogCardParser.parse(text).issues(today).isEmpty())
    }

    @Test
    fun unreadableDateIsFlaggedAndCanBeTypedIn() {
        val card = LogCardParser.parse(stacked.replace("11 Feb 2028", "11 Febtember 2028"))
        assertNull(card.coeExpiry)
        assertEquals("11 Febtember 2028", card.unreadable[CardDate.COE_EXPIRY])
        assertEquals(listOf(LogCardIssue.Kind.UNREADABLE), card.issues(today).map { it.kind })

        val fixed = card.withDate(CardDate.COE_EXPIRY, LocalDate.of(2028, 2, 11))
        assertEquals(LocalDate.of(2028, 2, 11), fixed.coeExpiry)
        assertTrue(fixed.issues(today).isEmpty())
    }

    @Test
    fun parsesDateFormats() {
        assertEquals(LocalDate.of(2025, 8, 3), LogCardParser.parseDate("03 Aug 2025"))
        assertEquals(LocalDate.of(2025, 8, 3), LogCardParser.parseDate("3 AUG 2025"))
        assertEquals(LocalDate.of(2025, 8, 3), LogCardParser.parseDate("03/08/2025"))
        assertEquals(LocalDate.of(2025, 8, 3), LogCardParser.parseDate("2025-08-03"))
        assertNull(LogCardParser.parseDate("No Lifespan"))
    }

    @Test
    fun applyFillsCardValuesAndKeepsTheRest() {
        val before = BikeDetails(profileId = 3, plate = "OLD1A", insuranceExpiry = LocalDate.of(2027, 1, 1), notes = "hi", roadTaxExpiry = LocalDate.of(2026, 12, 1))
        val after = LogCardParser.parse(stacked).applyTo(before)
        assertEquals(3L, after.profileId)
        assertEquals("FAB1234X", after.plate)
        assertEquals("Honda", after.brand)
        assertEquals("Red / Black", after.colour)
        assertEquals(LocalDate.of(2028, 2, 11), after.coeExpiry)
        assertEquals("123456789", after.registeredIu)
        // Not on the card: left alone.
        assertEquals(LocalDate.of(2027, 1, 1), after.insuranceExpiry)
        assertEquals(LocalDate.of(2026, 12, 1), after.roadTaxExpiry)
        assertEquals("hi", after.notes)
    }

    @Test
    fun previewListsWhatWillBeFilled() {
        val rows = LogCardParser.parse(stacked).previewRows().toMap()
        assertEquals("FAB1234X", rows["Licence Plate"])
        assertEquals("11 Feb 2028", rows["COE Expiry"])
        assertEquals("471 cc", rows["Engine Capacity"])
        assertEquals("S$1,234.00", rows["COE Paid"])
    }
}
