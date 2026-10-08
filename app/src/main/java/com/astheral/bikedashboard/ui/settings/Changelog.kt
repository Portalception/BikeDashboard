package com.astheral.bikedashboard.ui.settings

import java.time.LocalDate

data class Release(val version: String, val date: LocalDate, val changes: List<String>)

/** Major changes in each version, newest first. Add an entry here with every version you share. */
val CHANGELOG = listOf(
    Release(
        version = "0.4.1",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Picking a new app icon, or restoring a backup with one, no longer closes the app. The home screen switches to the new icon once you leave the app.",
            "Service reminders arrive within half an hour of the time you pick, instead of up to 18 hours late.",
            "Tapping a bundle of service reminders opens the Servicing page, like tapping a single one.",
            "Coming-up services that haven't been done yet no longer squash their names into one letter per line.",
            "\"1 fill-up\" instead of \"1 fill-ups\" (and the same for services and records) everywhere.",
            "The restore preview now lists service records and intervals too.",
        ),
    ),
    Release(
        version = "0.4",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Everything from 0.3.1 to 0.3.12 together:",
            "Servicing records for each bike, with service intervals and daily or weekly reminder notifications.",
            "Bike details: plate, chassis and engine numbers, value, and COE, road tax, inspection and insurance dates.",
            "Tap any dashboard card for a detailed overview; tap a number to change its unit.",
            "Week and Month tabs; the whole dashboard slides between periods.",
            "Spend bars coloured by country, and charts that fit their data, dotted when there are only a few fill-ups.",
            "Settings > Personalisation, a new fuel-light app icon with themed versions, and full backups that keep everything, ideas included.",
            "The back gesture slides back instead of zooming.",
        ),
    ),
    Release(
        version = "0.3.12",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Tap any dashboard tile or chart for a detailed overview: the figure for each period side by side, best, worst, average and median fill-ups, a chart and the fill-ups behind it.",
            "To change a tile's unit, tap its number (marked with ⇄); the detail page also has unit buttons.",
            "Line charts fit their y axis to the data instead of starting at zero, are smooth curves when there's enough data, and dotted when there are only a few fill-ups.",
        ),
    ),
    Release(
        version = "0.3.11",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "New app icon: a fuel light on a coloured background. Pick from Monochrome, an amber Fuel light, or one for each colour preset in Settings > Personalisation > App icon.",
            "Service reminder notifications use the fuel light too.",
        ),
    ),
    Release(
        version = "0.3.10",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Service intervals for each bike (Settings > Bike > Service intervals): engine oil, minor and major servicing, fluids and washes, each every so many km or days, with a warning window before it's due. Oil starts at every 5,000 km, like your spreadsheet.",
            "Reminders (Settings > Reminders): once something is coming up you get a notification, then again daily or weekly at the time you pick, until you add a new record.",
            "Coming-up services show on the dashboard and at the top of the Servicing page.",
        ),
    ),
    Release(
        version = "0.3.9",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Servicing (Settings > Bike > Servicing): keep each bike's services, oil changes, repairs, mods and washes, with how many days and km since the last service, oil change and wash, and what you've spent.",
            "Import your spreadsheet's Servicing Record sheet (saved as CSV) with the download button on the Servicing page.",
            "Full backups include servicing records.",
        ),
    ),
    Release(
        version = "0.3.8",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Back up everything to one file (Settings > Backup): every bike with its fill-ups and details, your personalisation and units, and the ideas notebook. Restore it from Import or restore.",
            "Export fill-ups as CSV is still there for spreadsheets.",
        ),
    ),
    Release(
        version = "0.3.7",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Bike details page (Settings > Bike > Bike details): brand, model, plate, chassis and engine numbers, IU, what you paid, COE, OMV, and when the COE, road tax, inspection and insurance run out, with a countdown that turns red in the last 30 days.",
        ),
    ),
    Release(
        version = "0.3.6",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "The dashboard tabs are now Last fill, Week, Month and Lifetime. Week and Month cover the last 7 and 30 days.",
        ),
    ),
    Release(
        version = "0.3.5",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Theme, colours and the progress line have moved to Settings > Personalisation, which also lets you pick the units the dashboard uses.",
        ),
    ),
    Release(
        version = "0.3.4",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Tap a dashboard tile to change its unit: km/L, L/100 km or mpg; cost per km or per 100 km; cost per day, week or month; petrol price in S\$ or RM (at your last rate). Your choice is remembered, and the fuel economy chart follows it.",
        ),
    ),
    Release(
        version = "0.3.3",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Spend bars are coloured by where you pumped: Singapore, Malaysia, or old mixed fill-ups, with a legend. A fill-up or month with both is stacked.",
        ),
    ),
    Release(
        version = "0.3.2",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Switching between Last fill-up, 30 days and Lifetime now slides the whole dashboard, charts included, and you can swipe anywhere below the tabs.",
        ),
    ),
    Release(
        version = "0.3.1",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "The back gesture now slides the page back to the dashboard, instead of shrinking it with a glitchy zoom. Pages opened over the tabs slide down.",
        ),
    ),
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
