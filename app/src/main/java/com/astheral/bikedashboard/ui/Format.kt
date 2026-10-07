package com.astheral.bikedashboard.ui

import com.astheral.bikedashboard.data.Country
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun LocalDate.long(): String = format(DAY)
fun LocalDate.short(): String = format(SHORT_DAY)

private fun f(digits: Int, value: Double) = String.format(Locale.ENGLISH, "%,.${digits}f", value)

fun sgd(value: Double): String = if (value < 0) "−S$${f(2, -value)}" else "S$${f(2, value)}"
fun sgdPerKm(value: Double): String = "S$${f(3, value)}/km"
fun sgdPerLitre(value: Double): String = "S$${f(2, value)}/L"
fun litres(value: Double): String = "${f(2, value)} L"
fun kmPerLitre(value: Double): String = "${f(1, value)} km/L"
fun km(value: Int): String = "${String.format(Locale.ENGLISH, "%,d", value)} km"

fun Country.label(): String = when (this) {
    Country.SG -> "SG"
    Country.MY -> "MY"
    Country.MIXED -> "MY+SG"
}
