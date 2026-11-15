package com.astheral.bikedashboard.ui.settings

import java.time.LocalDate

data class Release(val version: String, val date: LocalDate, val changes: List<String>) {
    /** A minor version like 0.7, which sums up the patches before it; patches are 0.6.13 and so on. */
    val isMinor: Boolean get() = version.count { it == '.' } <= 1
}

/** Major changes in each version, newest first, as short phrases. Add an entry with every version you share. */
val CHANGELOG = listOf(
    Release(
        version = "1.0.1",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Much smaller app: 4 MB instead of 12 MB",
            "Fixed a hidden character in the CSV reader",
        ),
    ),
    Release(
        version = "1.0",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Version 1.0! Everything from 0.8.1 to 0.8.4:",
            "Singapore fill-ups ask the litres first, then what you paid",
            "Optional Brand dropdown: Shell, Esso, SPC, or Others",
            "Brand shows in History and in backups",
            "Clearer line when it's been longer than usual since a fill-up",
            "Chart axes use round numbers",
        ),
    ),
    Release(
        version = "0.8.4",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Brand is now a dropdown under Full Tank?, not its own page",
            "Shell, Esso and SPC first; Caltex and Sinopec under Others",
        ),
    ),
    Release(
        version = "0.8.3",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Clearer line when it's been longer than usual since a fill-up",
            "Chart axes use round numbers",
            "Fixed a hidden character in the CSV reader",
        ),
    ),
    Release(
        version = "0.8.2",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Pick which brand you used in Singapore (optional)",
            "Brand shows in History and in backups",
        ),
    ),
    Release(
        version = "0.8.1",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Singapore fill-ups ask the litres first, then what you paid",
        ),
    ),
    Release(
        version = "0.8",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Everything from 0.7.1 to 0.7.5:",
            "Charts for KM Daily, costs and savings",
            "Arrange and hide dashboard charts",
            "Cards compare with the previous period",
            "Simpler, animated summary after a fill-up",
            "Swap Primary and Accent colours with one tap",
            "Hold Racetrack for five more tracks, plus a polished look",
            "Hold Rain on Visor to pick its direction or follow gravity",
        ),
    ),
    Release(
        version = "0.7.5",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Hold Rain on Visor to pick which way the drops run",
            "Gravity option: drops run downhill as you tilt the phone",
        ),
    ),
    Release(
        version = "0.7.4",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Hold the Racetrack background for more tracks",
            "Speedway, Street Circuit, Twisty, Figure Eight and Mountain Pass",
            "Polished Racetrack: run-off, kerbs, grid boxes and bikes",
        ),
    ),
    Release(
        version = "0.7.3",
        date = LocalDate.of(2026, 11, 15),
        changes = listOf(
            "Swap Primary and Accent colours with one tap",
        ),
    ),
    Release(
        version = "0.7.2",
        date = LocalDate.of(2026, 10, 10),
        changes = listOf(
            "Simpler, animated summary after a fill-up",
            "Pick the summary's detail lines in Personalisation",
            "Cheeky send-off before going back to Waze",
            "Cost per KM now spelled with capital KM",
        ),
    ),
    Release(
        version = "0.7.1",
        date = LocalDate.of(2026, 10, 10),
        changes = listOf(
            "Charts for KM Daily, costs and savings",
            "Arrange and hide dashboard charts",
            "Cards compare with the previous period",
            "Period Comparisons switch in Personalisation",
        ),
    ),
    Release(
        version = "0.7",
        date = LocalDate.of(2026, 10, 10),
        changes = listOf(
            "Everything from 0.6.1 to 0.6.13:",
            "Animated backgrounds, with their own page",
            "Random background, parallax tilt and speed sliders",
            "Racing backgrounds like Racetrack and Night Ride",
            "Greeting splash screen when the app opens",
            "New app icons: Classic and Speedo, plus hidden Legacy",
            "Fewer colour presets and a simpler colour picker",
            "Reworked Chain, Tyre Tread and Race Stripe lines",
            "Archive bikes you no longer ride",
            "KM Daily card; arrange and hide dashboard cards",
            "Partial fill-ups, with the date asked first",
            "Changelog on its own page",
        ),
    ),
    Release(
        version = "0.6.13",
        date = LocalDate.of(2026, 10, 10),
        changes = listOf(
            "Partial fill-ups, counted at your next full tank",
            "Adding a fill-up asks the date first",
            "Where you filled up is Singapore or Malaysia",
        ),
    ),
    Release(
        version = "0.6.12",
        date = LocalDate.of(2026, 10, 10),
        changes = listOf(
            "KM Daily card: distance ridden per day",
            "Hold a dashboard card to arrange or hide cards",
            "Savings card renamed Petrol Savings",
        ),
    ),
    Release(
        version = "0.6.11",
        date = LocalDate.of(2026, 10, 10),
        changes = listOf(
            "Archive bikes you no longer ride",
        ),
    ),
    Release(
        version = "0.6.10",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Cheekier greetings with emoji, late night until 1am",
            "Fuel Light app icon removed",
        ),
    ),
    Release(
        version = "0.6.9",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Colour picker shows just the basic colours",
            "Better Chain, Tyre Tread and Race Stripe lines",
        ),
    ),
    Release(
        version = "0.6.8",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Shaded pump is the new Classic icon",
            "Old icon hidden as Legacy; Minimal removed",
            "Fewer colour presets",
        ),
    ),
    Release(
        version = "0.6.7",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "App icons reworked; fuel gauge icon is now Speedo",
        ),
    ),
    Release(
        version = "0.6.6",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "New app icons in monochrome or yellow",
            "Brand-colour icons removed",
        ),
    ),
    Release(
        version = "0.6.5",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Five racing backgrounds, like Racetrack and Rain on Visor",
        ),
    ),
    Release(
        version = "0.6.4",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Background page with a live preview",
            "Random background, a new one each launch",
            "Parallax effect when you tilt the phone",
            "Movement and speed sliders, saved per bike",
        ),
    ),
    Release(
        version = "0.6.3",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Greeting shows on a short splash screen",
        ),
    ),
    Release(
        version = "0.6.2",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Riding-themed greeting for the time of day",
        ),
    ),
    Release(
        version = "0.6.1",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Animated backgrounds in your bike's colours",
        ),
    ),
    Release(
        version = "0.6",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Everything from 0.5.1 to 0.5.9:",
            "Date step with Today and Yesterday buttons",
            "Summary after each fill-up",
            "Standard, Race Stripe and On the Road progress bars",
            "Each bike keeps its own theme",
            "Swipe between tabs",
            "Erase All in the ideas notebook",
            "Diagnostic logs for crashes",
            "Fill bike details from your LTA log card",
            "Consistent Title Case",
        ),
    ),
    Release(
        version = "0.5.9",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Consistent Title Case everywhere",
        ),
    ),
    Release(
        version = "0.5.8",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Fill bike details from your LTA log card PDF",
            "Expired dates marked Expired",
        ),
    ),
    Release(
        version = "0.5.7",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Crashes saved as shareable diagnostic logs",
        ),
    ),
    Release(
        version = "0.5.6",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Erase All button in the ideas notebook",
        ),
    ),
    Release(
        version = "0.5.5",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Swipe between Dashboard, History and Settings",
        ),
    ),
    Release(
        version = "0.5.4",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Each bike keeps its own theme",
            "Racing animation when switching bikes",
        ),
    ),
    Release(
        version = "0.5.3",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Standard progress bar by default",
            "New Race Stripe and On the Road styles",
        ),
    ),
    Release(
        version = "0.5.2",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Summary after adding a fill-up",
            "Button back to Waze or Maps",
        ),
    ),
    Release(
        version = "0.5.1",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Fill-up date step with Today and Yesterday",
        ),
    ),
    Release(
        version = "0.5",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Everything from 0.4.1 to 0.4.10:",
            "Any custom colour, or a hex code",
            "One trip distance for all bikes",
            "Your own service categories and intervals",
            "Add a service one question at a time",
            "Tidier bike details with expiry reminders",
            "Reminders on the days and time you pick",
        ),
    ),
    Release(
        version = "0.4.10",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Colour presets show just the brand",
        ),
    ),
    Release(
        version = "0.4.9",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Reminders on chosen days, at any time",
        ),
    ),
    Release(
        version = "0.4.8",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Add a service one question at a time",
            "Optional odometer, estimated from fill-ups",
        ),
    ),
    Release(
        version = "0.4.7",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "One card each for COE, insurance, road tax, inspection",
            "Optional expiry reminders",
        ),
    ),
    Release(
        version = "0.4.6",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Tidier bike details; value fields hidden",
        ),
    ),
    Release(
        version = "0.4.5",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Add, rename or remove service categories",
        ),
    ),
    Release(
        version = "0.4.4",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Edit the service interval items",
            "Service categories kept in backups",
        ),
    ),
    Release(
        version = "0.4.3",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "One savings trip distance for all bikes",
        ),
    ),
    Release(
        version = "0.4.2",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Any custom colour, or a hex code",
            "Picked colours used exactly",
        ),
    ),
    Release(
        version = "0.4.1",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Changing the app icon no longer closes the app",
            "Reminders arrive on time",
            "Small fixes to servicing and restore",
        ),
    ),
    Release(
        version = "0.4",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Everything from 0.3.1 to 0.3.12:",
            "Servicing records, intervals and reminders",
            "Bike details and paperwork dates",
            "Detail page for every dashboard card",
            "Week and Month tabs",
            "Spend bars coloured by country",
            "Personalisation page and full backups",
            "Back gesture slides back",
        ),
    ),
    Release(
        version = "0.3.12",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Tap a dashboard card for a detailed overview",
            "Tap a number to change its unit",
            "Charts fit their data",
        ),
    ),
    Release(
        version = "0.3.11",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "New fuel light app icon, in several colours",
        ),
    ),
    Release(
        version = "0.3.10",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Service intervals for each bike",
            "Reminder notifications when a service is due",
            "Coming-up services on the dashboard",
        ),
    ),
    Release(
        version = "0.3.9",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Servicing records for each bike",
            "Import your servicing sheet",
        ),
    ),
    Release(
        version = "0.3.8",
        date = LocalDate.of(2026, 10, 9),
        changes = listOf(
            "Back up everything to one file",
        ),
    ),
    Release(
        version = "0.3.7",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Bike details page with expiry countdowns",
        ),
    ),
    Release(
        version = "0.3.6",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Tabs: Last Fill, Week, Month, Lifetime",
        ),
    ),
    Release(
        version = "0.3.5",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "New Personalisation page, with units",
        ),
    ),
    Release(
        version = "0.3.4",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Tap a tile to change its unit",
        ),
    ),
    Release(
        version = "0.3.3",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Spend bars coloured by country",
        ),
    ),
    Release(
        version = "0.3.2",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Whole dashboard slides between periods",
        ),
    ),
    Release(
        version = "0.3.1",
        date = LocalDate.of(2026, 10, 8),
        changes = listOf(
            "Smoother back gesture",
        ),
    ),
    Release(
        version = "0.3",
        date = LocalDate.of(2026, 10, 7),
        changes = listOf(
            "Bike profiles: one per bike or rental",
            "Setup page for each new bike",
            "Period tabs change every card",
            "More numbers count up",
            "Motorcycle-themed progress lines",
            "Monochrome default and brand colour presets",
            "Backups and imports per bike",
        ),
    ),
    Release(
        version = "0.2.1",
        date = LocalDate.of(2026, 10, 7),
        changes = listOf(
            "Choose the progress line shape",
        ),
    ),
    Release(
        version = "0.2",
        date = LocalDate.of(2026, 10, 7),
        changes = listOf(
            "New look: rounded cards and a floating bottom bar",
            "Add a fill-up one question at a time",
            "Quick amount buttons and live hints",
            "Mini bar charts and counting numbers",
            "History grouped by month",
            "Themes, pure black and custom colours",
            "Sliding animations",
        ),
    ),
    Release(
        version = "0.1",
        date = LocalDate.of(2026, 10, 7),
        changes = listOf(
            "First version",
            "Fill-ups in Singapore and Malaysia",
            "Dashboard of economy, cost and savings",
            "Price, economy and spend charts",
            "History with edit and delete",
            "Warnings for odd values",
            "Import your old spreadsheet",
            "CSV backup and restore",
        ),
    ),
)
