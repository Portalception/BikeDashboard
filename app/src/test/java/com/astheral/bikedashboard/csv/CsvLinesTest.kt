package com.astheral.bikedashboard.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvLinesTest {
    @Test
    fun leadingByteOrderMarkIsSkipped() {
        // Excel and Google Sheets often save CSVs with one; it mustn't end up in the first header.
        val rows = parseCsv(Char(0xFEFF) + "date,country\n2026-10-03,SG\n")
        assertEquals(listOf("date", "country"), rows[0])
        assertEquals(listOf("2026-10-03", "SG"), rows[1])
    }
}
