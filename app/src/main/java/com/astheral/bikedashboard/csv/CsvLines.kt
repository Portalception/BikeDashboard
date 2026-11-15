package com.astheral.bikedashboard.csv

/**
 * Splits CSV text into rows of fields. Handles quoted fields (which may contain
 * commas, doubled quotes or line breaks), Windows line endings and a leading BOM.
 */
fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = if (text.startsWith('\uFEFF')) 1 else 0

    fun endField() {
        row.add(field.toString())
        field.clear()
    }

    fun endRow() {
        endField()
        rows.add(row)
        row = mutableListOf()
    }

    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            inQuotes -> field.append(c)
            c == ',' -> endField()
            c == '\r' -> {}
            c == '\n' -> endRow()
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}
