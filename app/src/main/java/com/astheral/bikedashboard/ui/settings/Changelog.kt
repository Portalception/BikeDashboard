package com.astheral.bikedashboard.ui.settings

import java.time.LocalDate

data class Release(val version: String, val date: LocalDate, val changes: List<String>)

/** Major changes in each version, newest first. Add an entry here with every version you share. */
val CHANGELOG = listOf(
    Release(
        version = "0.3",
        date = LocalDate.of(2026, 10, 7),
        changes = listOf(
            "Bike profiles: keep separate fill-ups for each of your bikes or a rental. Tap the bike's name at " +
                "the top of any page to switch, add or edit one. Each bike has its own tank size and trip distance.",
            "A setup page, one question per card, for each new bike. Your existing fill-ups are now on \"My bike\"; " +
                "rename it in Settings > Bike.",
            "The Last fill-up / 30 days / Lifetime tabs now change every card on the dashboard, charts included.",
            "Numbers count up on History, the import preview and the final check of a new fill-up too.",
            "Progress lines inspired by motorcycles and racing: roller chain (the new default), kerb, chequered " +
                "flag, tyre tread and rev counter, plus the wave.",
            "Monochrome is the new default look, with colour presets from Yamaha, Honda, Kawasaki, Suzuki, Ducati, " +
                "BMW, KTM, Aprilia, MV Agusta and Triumph's flagship sports bikes.",
            "Backups and imports are for the bike you're looking at.",
        ),
    ),
    Release(
        version = "0.2.1",
        date = LocalDate.of(2026, 10, 7),
        changes = listOf(
            "Choose the shape of the progress line in Settings: wave, zigzag, heartbeat, bike chain, dots or straight.",
        ),
    ),
    Release(
        version = "0.2",
        date = LocalDate.of(2026, 10, 7),
        changes = listOf(
            "New look: rounded cards with thin outlines, faint circles in the background, and a floating " +
                "bottom bar with a + button for adding a fill-up.",
            "Adding a fill-up now asks one question per card, sliding to the next: where, how much you paid, " +
                "the rate, what the pump charged, litres, odometer and fuel left, then a final check.",
            "Quick buttons for the amounts you usually pay, and live hints like the SGD amount and price per litre.",
            "Editing a fill-up opens on the final check; tap any answer to change it.",
            "Dashboard tiles show small bar charts of your recent fill-ups, and the numbers count up.",
            "Swipe between Last fill-up, 30 days and Lifetime.",
            "A wavy bar shows how far you are into your usual time between fill-ups.",
            "History shows each fill-up as a card, grouped by month with that month's spend.",
            "Themes: System, Light or Dark; pure black for AMOLED screens; pick your own primary and accent " +
                "colours or a preset; or use your wallpaper's colours (Android 12+).",
            "Sliding animations between screens, and when fill-ups are added or deleted.",
            "The trip distance setting is now under \"Savings offset\".",
        ),
    ),
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
