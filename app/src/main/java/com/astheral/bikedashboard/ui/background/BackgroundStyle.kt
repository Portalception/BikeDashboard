package com.astheral.bikedashboard.ui.background

/** The moving picture drawn behind every screen, picked on the Background page. */
enum class BackgroundStyle(val label: String) {
    NONE("None"),
    RANDOM("Random"),
    CIRCLES("Circles"),
    RINGS("Rings"),
    GRID("Grid"),
    MESH("Mesh"),
    PARTICLES("Particles"),
    SHAPES("Shapes"),
    SNOW("Snow"),
    SPACE("Space"),
    MATRIX("Matrix");

    companion object {
        /** The style saved under [name], or Circles (the app's original look) for nothing saved or an unknown one. */
        fun fromName(name: String?): BackgroundStyle = entries.firstOrNull { it.name == name } ?: CIRCLES

        /** The real pictures Random chooses between: everything but None and Random itself. */
        val randomPool: List<BackgroundStyle> get() = entries.filter { it != NONE && it != RANDOM }
    }
}

/** Everything about the background that's set on the Background page, with Random already resolved. */
data class BackgroundLook(
    val style: BackgroundStyle = BackgroundStyle.CIRCLES,
    /** Shift the picture a little as the phone tilts. */
    val parallax: Boolean = true,
    /** How far things move: 0 nearly still, 1 normal, 2 twice as far. */
    val movement: Float = 1f,
    /** How fast it runs: 1 normal. */
    val speed: Float = 1f,
)

const val MIN_MOVEMENT = 0f
const val MAX_MOVEMENT = 2f
const val MIN_SPEED = 0.25f
const val MAX_SPEED = 3f
