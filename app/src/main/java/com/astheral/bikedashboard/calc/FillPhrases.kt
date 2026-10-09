package com.astheral.bikedashboard.calc

import kotlin.random.Random

/** The cheeky line at the top of the page after adding a fill-up, by how the fill-up went. */
object FillPhrases {
    val byMood: Map<SummaryMood, List<String>> = mapOf(
        SummaryMood.SAVED to listOf(
            "Nice one! 🎉",
            "Cha-ching! 💰",
            "The border run paid off. 🇲🇾",
            "Your wallet says thanks. 🙏",
            "Sneaky savings unlocked. 😏",
            "Ringgit magic. ✨",
        ),
        SummaryMood.MALAYSIA_PENDING to listOf(
            "Savings are still in the oven. ⏳",
            "Cheap fuel acquired. Maths pending. 🧮",
            "Malaysia run logged. 🇲🇾",
        ),
        SummaryMood.MALAYSIA_NO_SAVINGS to listOf(
            "Cheap fuel, pricey trip. 🫠",
            "The border run didn't pay this time. 🤷",
            "Malaysia won this round. 🥊",
        ),
        SummaryMood.PARTIAL to listOf(
            "A little top-up. Cute. 🥤",
            "Sipping, not chugging. 🧃",
            "Half measures, full vibes. 😌",
        ),
        SummaryMood.SINGAPORE to listOf(
            "Paying Singapore prices, huh? 💸",
            "Ouch. At least the tank's full. 🩹",
            "Malaysia is right there, you know. 👀",
            "Convenience tax, paid in full. 🧾",
            "Premium prices, premium rider. 💅",
        ),
    )

    /** One line for [mood]; the same [seed] always gives the same one. */
    fun pick(mood: SummaryMood, seed: Long): String {
        val choices = byMood.getValue(mood)
        return choices[Random(seed).nextInt(choices.size)]
    }
}

/** The send-off shown when leaving for the navigation app after a fill-up. */
object SendOffs {
    val byPart: Map<DayPart, List<String>> = mapOf(
        DayPart.MORNING to listOf(
            "Beat the jam. Go go go. 🚦",
            "Coffee's kicking in. So is the engine. ☕",
        ),
        DayPart.AFTERNOON to listOf(
            "Mind the heat. Hydrate, rider. 🥵",
            "Afternoon run? Don't be late. ⏱️",
        ),
        DayPart.EVENING to listOf(
            "Chase that sunset. 🌅",
            "Home's that way. Probably. 🧭",
        ),
        DayPart.LATE_NIGHT to listOf(
            "Lights on, wits sharp. 🌙",
            "Night ride. Watch out for cats. 🐈",
        ),
        DayPart.SMALL_HOURS to listOf(
            "Straight home, night owl. 🦉",
            "Empty roads. Behave anyway. 👮",
        ),
    )

    /** Good at any hour; mixed in with the ones for the time of day. */
    val anyTime: List<String> = listOf(
        "Go get 'em, rider. 🏍️",
        "Ride safe. The tank's happy. ⛽",
        "Full tank, zero excuses. Go. 🔥",
        "Shiny side up, rubber side down. ✨",
        "Off you pop. Mind the potholes. 🕳️",
        "Twist the throttle, not your ankle. 😉",
        "Fuelled up and dangerous. 😎",
        "The road misses you already. 🛣️",
    )

    /** Everything that could be shown at [hour]. */
    fun choicesAt(hour: Int): List<String> = byPart.getValue(DayPart.of(hour)) + anyTime

    /** One send-off for [hour]; the same [seed] always gives the same one. */
    fun pick(hour: Int, seed: Long): String {
        val choices = choicesAt(hour)
        return choices[Random(seed).nextInt(choices.size)]
    }
}
