package com.astheral.bikedashboard.csv

import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.ServiceRecord
import com.astheral.bikedashboard.data.ServiceType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ServiceImport(val records: List<ServiceRecord>, val problems: List<SheetProblem>)

private val SHEET_DATE = DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH)

private val SERVICE_COLUMNS = listOf("Date", "Mileage (Km)", "Type", "Description", "Cost", "Currency")

/** True if [text] is the spreadsheet's "Servicing Record" sheet saved as CSV. */
fun looksLikeServiceSheet(text: String): Boolean {
    val header = parseCsv(text).firstOrNull()?.map { it.trim() } ?: return false
    return SERVICE_COLUMNS.all { name -> header.any { it.equals(name, ignoreCase = true) } }
}

/** "$ 30.00", "30", "1,234.5" → a number; "-" or blank → null. */
private fun money(text: String): Double? {
    val clean = text.replace("$", "").replace(",", "").replace("RM", "", ignoreCase = true).trim()
    if (clean.isEmpty() || clean == "-") return null
    return clean.toDoubleOrNull() ?: throw IllegalArgumentException("Cost \"$text\" isn't a number.")
}

private fun date(text: String): LocalDate = try {
    LocalDate.parse(text, SHEET_DATE)
} catch (e: DateTimeParseException) {
    try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Date \"$text\" isn't like 9-Jan-2025.")
    }
}

/**
 * Reads the "Servicing Record" sheet. Rows without a date are the sheet's empty template rows
 * and are skipped; a row that can't be read is listed in [ServiceImport.problems].
 */
fun readServiceSheet(text: String, profileId: Long): ServiceImport {
    val lines = parseCsv(text)
    val header = lines.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()
    // The sheet repeats "Date" in its summary columns on the right, so the first one wins.
    val column = header.withIndex().reversed().associate { (i, name) -> name to i }
    val records = mutableListOf<ServiceRecord>()
    val problems = mutableListOf<SheetProblem>()
    lines.drop(1).forEachIndexed { index, fields ->
        val cell = { name: String -> column[name.lowercase()]?.let { fields.getOrNull(it) }?.trim().orEmpty() }
        if (cell("Date").isEmpty()) return@forEachIndexed
        val rowNumber = cell("No.").toIntOrNull() ?: cell("No").toIntOrNull() ?: (index + 1)
        try {
            val km = cell("Mileage (Km)").replace(",", "")
            records += ServiceRecord(
                profileId = profileId,
                date = date(cell("Date")),
                odometerKm = km.takeIf { it.isNotEmpty() }?.let {
                    it.toDoubleOrNull()?.toInt() ?: throw IllegalArgumentException("Mileage \"$it\" isn't a number.")
                },
                type = ServiceType.fromSheet(cell("Type")) ?: ServiceType.OTHER,
                description = cell("Description"),
                cost = money(cell("Cost")),
                currency = if (cell("Currency").equals("MYR", ignoreCase = true) || cell("Currency").equals("RM", ignoreCase = true)) {
                    Currency.MYR
                } else {
                    Currency.SGD
                },
            )
        } catch (e: IllegalArgumentException) {
            problems += SheetProblem(rowNumber, e.message ?: "Couldn't read this row.")
        }
    }
    return ServiceImport(records, problems)
}
