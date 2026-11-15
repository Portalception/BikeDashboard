package com.astheral.bikedashboard.csv

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.abs

/**
 * The app's own CSV format, used for backups. It holds everything the app stores,
 * at full precision, so importing a backup gives back exactly what was exported.
 *
 * "bars_left" follows the spreadsheet's rule: a positive number is fuel-gauge bars,
 * a negative one is km ridden on the fuel light. "full_tank" is "N" for a partial fill; backups
 * from before partial fills existed don't have the column, and their fill-ups are full tanks.
 * "brand" is the petrol brand, blank for none (older backups don't have it). "sg_before_discount_sgd"
 * is the Singapore pump price before a discount, blank when not recorded.
 */
private val REQUIRED_COLUMNS = listOf(
    "date", "country", "odometer_km",
    "sg_litres", "sg_cost_sgd",
    "my_litres", "my_cost_sgd", "rm_paid", "rm_per_sgd", "rm_charged",
    "unsplit_litres", "unsplit_cost_sgd",
    "bars_left", "no_special_trip", "estimated_max", "imported_savings_sgd",
)
private const val FULL_TANK = "full_tank"
private const val BRAND = "brand"
private const val SG_BEFORE_DISCOUNT = "sg_before_discount_sgd"
private val COLUMNS = REQUIRED_COLUMNS + FULL_TANK + BRAND + SG_BEFORE_DISCOUNT

fun writeBackup(fillUps: List<FillUp>): String = buildString {
    appendLine(COLUMNS.joinToString(","))
    fillUps.sortedWith(compareBy({ it.date }, { it.odometerKm })).forEach { f ->
        val barsOrReserve = f.barsLeft?.toString() ?: f.reserveKm?.let { num(-it) }
        appendLine(
            listOf(
                f.date.toString(), f.country.name, f.odometerKm.toString(),
                num(f.sgLitres), num(f.sgCostSgd),
                num(f.myLitres), num(f.myCostSgd), num(f.rmPaid), num(f.rmPerSgd), num(f.rmCharged),
                num(f.unsplitLitres), num(f.unsplitCostSgd),
                barsOrReserve.orEmpty(), if (f.noSpecialTrip) "Y" else "", num(f.estimatedMax), num(f.importedSavingsSgd),
                if (f.fullTank) "Y" else "N",
                f.brand.orEmpty().replace(",", " ").replace("\"", ""),
                num(f.sgBeforeDiscountSgd),
            ).joinToString(","),
        )
    }
}

private fun num(value: Double?): String = when {
    value == null -> ""
    value == Math.rint(value) && abs(value) < 1e15 -> value.toLong().toString()
    else -> value.toString()
}

/** True if [text] is one of the app's own backups. */
fun looksLikeBackup(text: String): Boolean =
    parseCsv(text).firstOrNull()?.map { it.trim() }?.containsAll(REQUIRED_COLUMNS) == true

data class BackupImport(val fillUps: List<FillUp>, val problems: List<SheetProblem>)

fun readBackup(text: String): BackupImport {
    val lines = parseCsv(text)
    val header = lines.firstOrNull()?.map { it.trim() }.orEmpty()
    val column = header.withIndex().associate { (i, name) -> name to i }
    val fillUps = mutableListOf<FillUp>()
    val problems = mutableListOf<SheetProblem>()

    lines.drop(1).forEachIndexed { index, fields ->
        if (fields.all { it.isBlank() }) return@forEachIndexed
        val line = index + 2 // as numbered in a spreadsheet, header is line 1
        val cell = { name: String -> column[name]?.let { fields.getOrNull(it) }?.trim().orEmpty() }
        val decimal = { name: String ->
            cell(name).takeIf { it.isNotEmpty() }?.let {
                it.toDoubleOrNull() ?: throw IllegalArgumentException("$name \"$it\" isn't a number.")
            }
        }
        try {
            val barsOrReserve = decimal("bars_left")
            fillUps += FillUp(
                date = try {
                    LocalDate.parse(cell("date"))
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("Date \"${cell("date")}\" isn't like 2026-10-03.")
                },
                country = Country.entries.firstOrNull { it.name == cell("country") }
                    ?: throw IllegalArgumentException("Country \"${cell("country")}\" isn't SG, MY or MIXED."),
                odometerKm = cell("odometer_km").toIntOrNull()
                    ?: throw IllegalArgumentException("Odometer \"${cell("odometer_km")}\" isn't a whole number."),
                sgLitres = decimal("sg_litres"),
                sgCostSgd = decimal("sg_cost_sgd"),
                myLitres = decimal("my_litres"),
                myCostSgd = decimal("my_cost_sgd"),
                rmPaid = decimal("rm_paid"),
                rmPerSgd = decimal("rm_per_sgd"),
                rmCharged = decimal("rm_charged"),
                unsplitLitres = decimal("unsplit_litres"),
                unsplitCostSgd = decimal("unsplit_cost_sgd"),
                barsLeft = barsOrReserve?.takeIf { it >= 0 }?.toInt(),
                reserveKm = barsOrReserve?.takeIf { it < 0 }?.let { -it },
                noSpecialTrip = cell("no_special_trip").equals("Y", ignoreCase = true),
                estimatedMax = decimal("estimated_max"),
                importedSavingsSgd = decimal("imported_savings_sgd"),
                fullTank = !cell(FULL_TANK).equals("N", ignoreCase = true),
                brand = cell(BRAND).takeIf { it.isNotEmpty() },
                sgBeforeDiscountSgd = decimal(SG_BEFORE_DISCOUNT),
            )
        } catch (e: IllegalArgumentException) {
            problems += SheetProblem(line, e.message ?: "Couldn't read this line.")
        }
    }
    return BackupImport(fillUps, problems)
}
