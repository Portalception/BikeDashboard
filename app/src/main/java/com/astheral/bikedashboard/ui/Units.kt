package com.astheral.bikedashboard.ui

import java.util.Locale

private fun f(digits: Int, value: Double) = String.format(Locale.ENGLISH, "%,.${digits}f", value)

/** Litres in a US gallon and km in a mile, for mpg. */
private const val LITRES_PER_GALLON = 3.785411784
private const val KM_PER_MILE = 1.609344

/** Average days in a month. */
private const val DAYS_PER_MONTH = 30.4375

/** A way of showing one kind of number. Tapping a dashboard tile moves on to the next unit. */
interface DisplayUnit {
    val label: String

    /** From the app's base unit (km/L, S$/km, S$/day, S$/L, km/day) to this one. */
    fun convert(base: Double): Double
    fun format(value: Double): String
}

enum class EconomyUnit(override val label: String) : DisplayUnit {
    KM_PER_L("km/L"), L_PER_100KM("L/100 km"), MPG("mpg (US)");

    override fun convert(base: Double) = when (this) {
        KM_PER_L -> base
        L_PER_100KM -> if (base > 0) 100.0 / base else 0.0
        MPG -> base * LITRES_PER_GALLON / KM_PER_MILE
    }

    override fun format(value: Double) = when (this) {
        KM_PER_L -> "${f(1, value)} km/L"
        L_PER_100KM -> "${f(2, value)} L/100km"
        MPG -> "${f(1, value)} mpg"
    }
}

enum class DistanceCostUnit(override val label: String) : DisplayUnit {
    PER_KM("S$ per km"), PER_100KM("S$ per 100 km");

    override fun convert(base: Double) = if (this == PER_KM) base else base * 100

    override fun format(value: Double) = if (this == PER_KM) "S$${f(3, value)}/km" else "S$${f(2, value)}/100km"
}

enum class TimeCostUnit(override val label: String) : DisplayUnit {
    PER_DAY("S$ per Day"), PER_WEEK("S$ per Week"), PER_MONTH("S$ per Month");

    override fun convert(base: Double) = when (this) {
        PER_DAY -> base
        PER_WEEK -> base * 7
        PER_MONTH -> base * DAYS_PER_MONTH
    }

    override fun format(value: Double) = when (this) {
        PER_DAY -> "${sgd(value)}/d"
        PER_WEEK -> "${sgd(value)}/wk"
        PER_MONTH -> "${sgd(value)}/mo"
    }
}

/** How far the bike is ridden over time: km per day, week or month. */
enum class DistanceTimeUnit(override val label: String) : DisplayUnit {
    PER_DAY("km per Day"), PER_WEEK("km per Week"), PER_MONTH("km per Month");

    override fun convert(base: Double) = when (this) {
        PER_DAY -> base
        PER_WEEK -> base * 7
        PER_MONTH -> base * DAYS_PER_MONTH
    }

    override fun format(value: Double) = when (this) {
        PER_DAY -> "${f(1, value)} km/day"
        PER_WEEK -> "${f(0, value)} km/wk"
        PER_MONTH -> "${f(0, value)} km/mo"
    }
}

/**
 * Petrol price in SGD, or in ringgit at the last exchange rate you entered. Without a rate
 * (no Malaysian fill-ups yet) prices stay in SGD.
 */
enum class PriceUnit(val label: String) {
    SGD_PER_L("S$ per Litre"), RM_PER_L("RM per Litre");

    fun at(rmPerSgd: Double?): DisplayUnit = object : DisplayUnit {
        private val rm = this@PriceUnit == RM_PER_L && rmPerSgd != null
        override val label = if (rm) RM_PER_L.label else SGD_PER_L.label
        override fun convert(base: Double) = if (rm) base * rmPerSgd!! else base
        override fun format(value: Double) = if (rm) "RM${f(2, value)}/L" else "S$${f(2, value)}/L"
    }
}

/** Plain SGD amounts, which have no other unit. */
object SgdUnit : DisplayUnit {
    override val label = "S$"
    override fun convert(base: Double) = base
    override fun format(value: Double) = sgd(value)
}

/** The unit picked for each number on the dashboard. Saved in Settings and in backups. */
data class Units(
    val economy: EconomyUnit = EconomyUnit.KM_PER_L,
    val distanceCost: DistanceCostUnit = DistanceCostUnit.PER_KM,
    val timeCost: TimeCostUnit = TimeCostUnit.PER_DAY,
    val price: PriceUnit = PriceUnit.SGD_PER_L,
    val distanceTime: DistanceTimeUnit = DistanceTimeUnit.PER_DAY,
)

/** The next unit in the list, wrapping round; [allowed] skips units that can't be shown right now. */
inline fun <reified E : Enum<E>> E.next(allowed: (E) -> Boolean = { true }): E {
    val all = enumValues<E>()
    for (step in 1..all.size) {
        val candidate = all[(ordinal + step) % all.size]
        if (allowed(candidate)) return candidate
    }
    return this
}
