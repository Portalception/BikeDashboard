package com.astheral.bikedashboard.csv

import com.astheral.bikedashboard.calc.computeStats
import kotlin.math.abs

data class SheetMismatch(val rowNumber: Int, val figure: String, val sheet: Double?, val app: Double?)

/**
 * Recalculates every row the app's way and lists where the result differs from what
 * the spreadsheet itself worked out. Tolerances allow for the sheet's display rounding.
 */
fun checkAgainstSheet(rows: List<SheetRow>): List<SheetMismatch> {
    // Same order computeStats uses, so the two lists line up.
    val sorted = rows.sortedWith(compareBy({ it.fillUp.date }, { it.fillUp.odometerKm }))
    val stats = computeStats(sorted.map { it.fillUp })
    return sorted.zip(stats).flatMap { (row, s) ->
        val sheet = row.sheet
        listOfNotNull(
            compare(row, "Distance (km)", sheet.distanceKm?.toDouble(), s.distanceKm?.toDouble(), 0.0),
            compare(row, "Days", sheet.days?.toDouble(), s.days?.toDouble(), 0.0),
            compare(row, "Km/L", sheet.kmPerLitre, s.kmPerLitre, 0.005),
            compare(row, "Cost per km", sheet.costPerKm, s.costPerKm, 0.00005),
            compare(row, "Cost per day", sheet.costPerDay, s.costPerDay, 0.0051),
            compare(row, "Price per litre", sheet.pricePerLitre, s.pricePerLitre, 0.005),
        )
    }.sortedBy { it.rowNumber }
}

private fun compare(row: SheetRow, figure: String, sheet: Double?, app: Double?, tolerance: Double): SheetMismatch? {
    val differs = if (sheet == null || app == null) sheet != app else abs(sheet - app) > tolerance
    return if (differs) SheetMismatch(row.rowNumber, figure, sheet, app) else null
}
