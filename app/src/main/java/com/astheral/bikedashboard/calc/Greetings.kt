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
            in 22..23, 0 -> LATE_NIGHT
            else -> SMALL_HOURS
        }
    }
}

/**
 * The short hello on the splash screen when the app opens. One is picked when the app starts (see [pick])
 * and kept for the whole visit. "{bike}" stands for the bike's name.
 */
object Greetings {
    const val BIKE = "{bike}"

    val byPart: Map<DayPart, List<String>> = mapOf(
        DayPart.MORNING to listOf(
            "Rise and ride. ☀️",
            "Coffee for you, 95 for $BIKE. ☕",
            "Morning! Your tank's emptier than your inbox.",
            "The early bird gets the empty pump. 🐦",
            "Up before the traffic? Legend. 🏍️",
        ),
        DayPart.AFTERNOON to listOf(
            "Lunch break? More like lunch ride. 🍜",
            "Perfect weather for a \"quick\" 200 km detour.",
            "Sun's out, visor down. 😎",
            "Half the day gone. Half the tank too?",
            "$BIKE is bored. Just saying.",
        ),
        DayPart.EVENING to listOf(
            "Golden hour suits $BIKE. 🌅",
            "Home in one piece? Let's see the damage. 💸",
            "Sunset ride or spreadsheet night? Why not both.",
            "Another day, another tank. ⛽",
            "Cool air, empty roads, zero excuses.",
        ),
        DayPart.LATE_NIGHT to listOf(
            "Night ride or bad decisions? 🌙",
            "Petrol station hot dog time? 🌭",
            "The roads are empty. Suspicious. 👀",
            "Still up? $BIKE went to bed hours ago. 😴",
            "Just checking fuel prices at this hour? Sure. 🦉",
        ),
        DayPart.SMALL_HOURS to listOf(
            "Even the pumps are asleep. 💤",
            "Counting kilometres instead of sheep? 🐑",
            "3am thoughts: is premium really worth it?",
            "Nothing good happens after 2am. Except fuel stats.",
            "Burning the midnight oil, not the petrol. 🕯️",
        ),
    )

    /** Good at any hour; mixed in with the ones for the time of day. */
    val anyTime: List<String> = listOf(
        "Keep the shiny side up. ✨",
        "Ride more, spend less. 💰",
        "Fuel prices are a crime. You're the witness. 🚨",
        "Four wheels move the body. Two move the soul. 🏍️",
        "$BIKE says hello. Probably wants petrol. ⛽",
        "Malaysia's calling. So is the cheap petrol. 🇲🇾",
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
