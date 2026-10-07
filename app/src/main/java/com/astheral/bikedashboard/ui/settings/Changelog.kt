package com.astheral.bikedashboard.ui.settings

import java.time.LocalDate

data class Release(val version: String, val date: LocalDate, val changes: List<String>)

/** Major changes in each version, newest first. Add an entry here with every version you share. */
val CHANGELOG = listOf(
    Release(
        version = "0.1",
        date = LocalDate.of(2026, 10, 7),
        changes = listOf(
            "First version.",
            "Add fill-ups for Singapore, Malaysia or both. Price per litre is worked out for you, and Malaysian " +
                "fill-ups are converted to SGD using the rate you enter (your last rate is filled in).",
            "Dashboard with fuel economy, petrol price, cost per km, cost per day and money saved by pumping " +
                "in Malaysia, for the last fill-up, the last 30 days and lifetime.",
            "Charts: price per litre (Singapore vs Malaysia), fuel economy over time and monthly spend.",
            "History of every fill-up, with edit and delete.",
            "Warnings when something you enter looks wrong, with the option to save anyway.",
            "Import your old fuel spreadsheet, checked against the spreadsheet's own figures.",
            "Back up to a CSV file and restore from it.",
            "Setting for the trip distance to Malaysia used in the savings.",
        ),
    ),
)
