package com.astheral.bikedashboard.logcard

import java.util.Locale

/**
 * Colour names as the LTA writes them on log cards, turned into theme colours (ARGB). The
 * values are picked to work as an app colour: clear but not neon, and white, black and silver
 * are softened (pure white or black can't be told apart from the background). The theme then
 * adjusts the brightness further so text stays readable.
 */
private val NAMED = mapOf(
    "black" to 0xFF2B2B2B,
    "white" to 0xFFE6E6E6,
    "silver" to 0xFFB0BEC5,
    "grey" to 0xFF8E8E8E,
    "gray" to 0xFF8E8E8E,
    "red" to 0xFFD32F2F,
    "maroon" to 0xFF8E1B2C,
    "blue" to 0xFF1B3F9A,
    "navy" to 0xFF1A2A6C,
    "green" to 0xFF43A047,
    "yellow" to 0xFFFBC02D,
    "orange" to 0xFFFB8C00,
    "gold" to 0xFFC9A227,
    "bronze" to 0xFF9C6B30,
    "brown" to 0xFF6D4C41,
    "beige" to 0xFFD8C3A5,
    "cream" to 0xFFEFE3C8,
    "pink" to 0xFFEC407A,
    "purple" to 0xFF7B1FA2,
    "violet" to 0xFF7E57C2,
    "turquoise" to 0xFF00ACC1,
    "cyan" to 0xFF00B0FF,
    "teal" to 0xFF00897B,
    "champagne" to 0xFFD9C8A0,
    "magenta" to 0xFFC2185B,
)

/** Lighter or darker shades, like "Light Blue" or "Dark Green". */
private val SHADES = mapOf(
    "light blue" to 0xFF64B5F6,
    "dark blue" to 0xFF1A2A6C,
    "light green" to 0xFF81C784,
    "dark green" to 0xFF2E7D32,
    "dark grey" to 0xFF545454,
    "dark gray" to 0xFF545454,
    "light grey" to 0xFFB8B8B8,
    "light gray" to 0xFFB8B8B8,
    "dark red" to 0xFF9A1B1B,
)

/**
 * The theme colour for a log card colour name ("Blue", "LIGHT BLUE", "Metallic Silver" …), or
 * null for a name that isn't a colour ("Multi-colour", "-", "Others").
 */
fun logCardColour(name: String?): Int? {
    val n = name?.lowercase(Locale.ENGLISH)?.replace(Regex("[^a-z ]"), " ")?.replace(Regex("\\s+"), " ")?.trim()
    if (n.isNullOrEmpty()) return null
    SHADES[n]?.let { return it.toInt() }
    NAMED[n]?.let { return it.toInt() }
    // "Metallic Silver", "Pearl White", "Dark Blue Metallic": use the first word that's a colour.
    SHADES.entries.firstOrNull { n.contains(it.key) }?.let { return it.value.toInt() }
    return n.split(" ").firstNotNullOfOrNull { NAMED[it] }?.toInt()
}

/**
 * The primary and accent colours to theme the app with, from the card's primary and secondary
 * colour. With only one colour (or two the same), it's used for both. Null when neither is a colour.
 */
fun logCardTheme(primary: String?, secondary: String?): Pair<Int, Int>? {
    val p = logCardColour(primary)
    val s = logCardColour(secondary)
    return when {
        p != null -> p to (s ?: p)
        s != null -> s to s
        else -> null
    }
}
