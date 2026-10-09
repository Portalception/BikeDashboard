package com.astheral.bikedashboard.calc

import kotlin.random.Random

/** The part of the day a greeting is picked for, by the hour (0–23). */
enum class DayPart {
    MORNING, AFTERNOON, EVENING, LATE_NIGHT, SMALL_HOURS;

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            in 22..23 -> LATE_NIGHT
            else -> SMALL_HOURS
        }
    }
}

/**
 * The short hello at the top of the dashboard. One is picked when the app starts (see [pick])
 * and kept for the whole visit. "{bike}" stands for the bike's name.
 */
object Greetings {
    const val BIKE = "{bike}"

    val byPart: Map<DayPart, List<String>> = mapOf(
        DayPart.MORNING to listOf(
            "Rise and ride.",
            "Morning! Coffee for you, petrol for $BIKE.",
            "Fresh day, fresh tank?",
            "The early bird gets the empty pump.",
            "Good morning. The open road says hi.",
        ),
        DayPart.AFTERNOON to listOf(
            "Afternoon. Perfect weather for a detour.",
            "Lunch break ride, anyone?",
            "Half the day gone. How's the tank?",
            "Sun's up, visor down.",
            "Good afternoon. $BIKE is ready when you are.",
        ),
        DayPart.EVENING to listOf(
            "Evening ride or evening review?",
            "Golden hour suits $BIKE.",
            "Home safe? Let's look at the numbers.",
            "Good evening. Got a fill-up to log?",
            "Cool air, quiet roads, good evening.",
        ),
        DayPart.LATE_NIGHT to listOf(
            "Night ride or night owl?",
            "Late one tonight. Ride safe.",
            "The roads are quiet at this hour.",
            "Still up? $BIKE is having a rest.",
            "One quick look before bed?",
        ),
        DayPart.SMALL_HOURS to listOf(
            "Midnight petrol run?",
            "Even the pumps are asleep.",
            "Counting kilometres instead of sheep?",
            "Kickstand down, time for some sleep.",
            "Burning the midnight oil, not the petrol.",
        ),
    )

    /** Good at any hour; mixed in with the ones for the time of day. */
    val anyTime: List<String> = listOf(
        "Fuelled up and ready?",
        "Keep the shiny side up.",
        "Every kilometre counts.",
        "Ride more, spend less.",
        "Two wheels, one dashboard.",
        "$BIKE says hello.",
    )

    /** Everything that could be shown at [hour]. */
    fun choicesAt(hour: Int): List<String> = byPart.getValue(DayPart.of(hour)) + anyTime

    /** One greeting for [hour]; the same [seed] always gives the same one. */
    fun pick(hour: Int, seed: Long): String {
        val choices = choicesAt(hour)
        return choices[Random(seed).nextInt(choices.size)]
    }

    /** [greeting] with the bike's name in place of "{bike}" ("your bike" without one). */
    fun fill(greeting: String, bikeName: String?): String {
        val name = bikeName?.trim()?.takeIf { it.isNotEmpty() } ?: "your bike"
        return greeting.replace(BIKE, name).replaceFirstChar { it.uppercaseChar() }
    }
}
