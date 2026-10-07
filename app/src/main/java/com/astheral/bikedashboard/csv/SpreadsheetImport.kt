package com.astheral.bikedashboard.csv

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Figures the spreadsheet worked out itself, kept so the import can be checked against the app's own. */
data class SheetFigures(
    val distanceKm: Int?,
    val days: Long?,
    val kmPerLitre: Double?,
    val costPerKm: Double?,
    val costPerDay: Double?,
    val pricePerLitre: Double?,
)

data class SheetRow(
    /** The "No" column, used when reporting problems. */
    val rowNumber: Int,
    val fillUp: FillUp,
    val sheet: SheetFigures,
)

data class SheetProblem(val rowNumber: Int, val message: String)

data class SheetImport(val rows: List<SheetRow>, val problems: List<SheetProblem>)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH)

private val REQUIRED_COLUMNS = listOf(
    "No", "Date", "Country", "RM Paid", "SGD Conversion", "RM Price", "Mileage (KM)",
    "Bars Left", "Petrol Pumped (L)", "Price", "No Exit", "Petrol Price (Per L)",
    "Distance Travelled (KM)", "Days Passed", "Fuel Economy (KM/L)", "Cost Per Day",
    "Cost Per Km", "Savings", "Estimated Max",
)

/** True if [text] has the spreadsheet's header row, rather than the app's own export format. */
fun looksLikeSpreadsheet(text: String): Boolean {
    val header = parseCsv(text).firstOrNull()?.map { it.trim() } ?: return false
    return REQUIRED_COLUMNS.all { it in header }
}

/**
 * Reads the "R3 Bike Dashboard - Fueling Record" spreadsheet export.
 *
 * Rows without a date are the sheet's empty template rows and are skipped. A row that
 * can't be read is left out and listed in [SheetImport.problems] instead of stopping
 * the whole import.
 */
fun readSpreadsheet(text: String): SheetImport {
    val lines = parseCsv(text)
    val header = lines.firstOrNull()?.map { it.trim() }
        ?: return SheetImport(emptyList(), listOf(SheetProblem(0, "The file is empty.")))
    val missing = REQUIRED_COLUMNS.filter { it !in header }
    if (missing.isNotEmpty()) {
        return SheetImport(emptyList(), listOf(SheetProblem(0, "Missing columns: ${missing.joinToString()}")))
    }
    val column = header.withIndex().associate { (i, name) -> name to i }

    val rows = mutableListOf<SheetRow>()
    val problems = mutableListOf<SheetProblem>()
    lines.drop(1).forEachIndexed { index, fields ->
        val cell = { name: String -> fields.getOrNull(column.getValue(name))?.trim().orEmpty() }
        if (cell("Date").isEmpty()) return@forEachIndexed
        val rowNumber = cell("No").toIntOrNull() ?: (index + 1)
        try {
            rows.add(readRow(rowNumber, cell))
        } catch (e: IllegalArgumentException) {
            problems.add(SheetProblem(rowNumber, e.message ?: "Couldn't read this row."))
        }
    }
    return SheetImport(rows, problems)
}

private fun readRow(rowNumber: Int, cell: (String) -> String): SheetRow {
    val date = try {
        LocalDate.parse(cell("Date"), DATE_FORMAT)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Date \"${cell("Date")}\" isn't like 2-Mar-2025.")
    }
    val country = when (cell("Country").uppercase()) {
        "SG" -> Country.SG
        "MY" -> Country.MY
        "MY/SG", "SG/MY" -> Country.MIXED
        else -> throw IllegalArgumentException("Country \"${cell("Country")}\" isn't SG, MY or MY/SG.")
    }
    val odometer = cell("Mileage (KM)").toIntOrNull()
        ?: throw IllegalArgumentException("Mileage \"${cell("Mileage (KM)")}\" isn't a whole number.")
    val litres = number(cell("Petrol Pumped (L)"))?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("Petrol pumped \"${cell("Petrol Pumped (L)")}\" is missing or zero.")
    val cost = number(cell("Price"))?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("Price \"${cell("Price")}\" is missing or zero.")

    // "$ -" in these columns means nothing was recorded.
    val rmPaid = number(cell("RM Paid"))?.takeIf { it > 0 }
    val sgdConversion = number(cell("SGD Conversion"))?.takeIf { it > 0 }
    val rmCharged = number(cell("RM Price"))?.takeIf { it > 0 }
    val rate = if (rmPaid != null && sgdConversion != null) rmPaid / sgdConversion else null
    // The CSV shows Price rounded to cents; the sheet itself uses RM charged ÷ rate unrounded.
    val myCost = if (country == Country.MY && rmCharged != null && rate != null) rmCharged / rate else cost

    // Positive = fuel-gauge bars left; negative = km ridden on the fuel light.
    val barsOrReserve = number(cell("Bars Left"))
    val barsLeft = barsOrReserve?.takeIf { it >= 0 }?.let {
        require(it == it.roundToInt().toDouble()) { "Bars left \"${cell("Bars Left")}\" isn't a whole number." }
        it.roundToInt()
    }
    val reserveKm = barsOrReserve?.takeIf { it < 0 }?.let { abs(it) }

    val fillUp = FillUp(
        date = date,
        country = country,
        odometerKm = odometer,
        sgLitres = litres.takeIf { country == Country.SG },
        sgCostSgd = cost.takeIf { country == Country.SG },
        myLitres = litres.takeIf { country == Country.MY },
        myCostSgd = myCost.takeIf { country == Country.MY },
        rmPaid = rmPaid.takeIf { country != Country.SG },
        rmPerSgd = rate.takeIf { country != Country.SG },
        rmCharged = rmCharged.takeIf { country != Country.SG },
        unsplitLitres = litres.takeIf { country == Country.MIXED },
        unsplitCostSgd = cost.takeIf { country == Country.MIXED },
        barsLeft = barsLeft,
        reserveKm = reserveKm,
        noSpecialTrip = cell("No Exit").equals("Y", ignoreCase = true),
        estimatedMax = number(cell("Estimated Max")),
        importedSavingsSgd = if (country == Country.SG) null else number(cell("Savings")),
    )
    val sheet = SheetFigures(
        distanceKm = number(cell("Distance Travelled (KM)"))?.roundToInt(),
        days = number(cell("Days Passed"))?.toLong(),
        kmPerLitre = number(cell("Fuel Economy (KM/L)")),
        costPerKm = number(cell("Cost Per Km")),
        costPerDay = number(cell("Cost Per Day")),
        pricePerLitre = number(cell("Petrol Price (Per L)")),
    )
    return SheetRow(rowNumber, fillUp, sheet)
}

/**
 * Reads spreadsheet numbers like "130883", "-31.20", " $ 26.90 ", "$ 1,234.50" and
 * "$ (2.10)" (negative). A lone "$ -" means zero. Blank and "NA" mean no value.
 */
internal fun number(raw: String): Double? {
    var s = raw.trim()
    if (s.isEmpty() || s.equals("NA", ignoreCase = true)) return null
    var negative = false
    if (s.startsWith("-") && s.drop(1).trim().startsWith("$")) { negative = true; s = s.drop(1) }
    s = s.removePrefix("$").replace(",", "").trim()
    if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.drop(1).dropLast(1).trim() }
    if (s.isEmpty()) return null
    if (s == "-") return 0.0
    val value = s.toDoubleOrNull() ?: throw IllegalArgumentException("\"${raw.trim()}\" isn't a number.")
    return if (negative) -value else value
}
