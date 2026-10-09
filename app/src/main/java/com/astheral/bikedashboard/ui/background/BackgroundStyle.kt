package com.astheral.bikedashboard.ui.background

/** The moving picture drawn behind every screen, picked in Personalisation. */
enum class BackgroundStyle(val label: String) {
    NONE("None"),
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
    }
}
