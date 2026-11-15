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
    MATRIX("Matrix"),
    RACETRACK("Racetrack"),
    NIGHT_RIDE("Night Ride"),
    SPEED_LINES("Speed Lines"),
    CHEQUERED_FLAG("Chequered Flag"),
    RAIN("Rain on Visor");

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
    /** The circuit Racetrack draws (hold its tile to pick). */
    val track: RaceTrack = RaceTrack.CLASSIC,
    /** Which way Rain on Visor's drops run (hold its tile to pick). */
    val rain: RainDirection = RainDirection.LEFT,
) {
    /** True when the picture needs the phone's gravity sensor beyond parallax: rain that runs downhill. */
    val followsGravity: Boolean get() = style == BackgroundStyle.RAIN && rain == RainDirection.GRAVITY
}

/**
 * Which way the wind blows the drops in Rain on Visor, as a screen angle in degrees (0 is right,
 * 90 is down), or [GRAVITY] to let them run downhill however the phone is held. In the order
 * they're laid out in the picker: a 3 by 3 compass with Gravity in the middle.
 */
enum class RainDirection(val label: String, val degrees: Float?) {
    UP_LEFT("Up Left", 225f),
    UP("Up", 270f),
    UP_RIGHT("Up Right", 315f),
    // Left and Right lean a little downwards, like wind and gravity together (Left is the original look).
    LEFT("Left", 158f),
    GRAVITY("Gravity", null),
    RIGHT("Right", 22f),
    DOWN_LEFT("Down Left", 135f),
    DOWN("Down", 90f),
    DOWN_RIGHT("Down Right", 45f);

    companion object {
        /** The direction saved under [name], or Left (the original) for nothing saved or an unknown one. */
        fun fromName(name: String?): RainDirection = entries.firstOrNull { it.name == name } ?: LEFT
    }
}

/** The circuits the Racetrack background can show. */
enum class RaceTrack(val label: String) {
    CLASSIC("Classic"),
    SPEEDWAY("Speedway"),
    STREET("Street Circuit"),
    TWISTY("Twisty"),
    FIGURE_EIGHT("Figure Eight"),
    MOUNTAIN("Mountain Pass");

    companion object {
        /** The track saved under [name], or Classic (the original one) for nothing saved or an unknown one. */
        fun fromName(name: String?): RaceTrack = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

const val MIN_MOVEMENT = 0f
const val MAX_MOVEMENT = 2f
const val MIN_SPEED = 0.25f
const val MAX_SPEED = 3f
