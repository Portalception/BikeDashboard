package com.astheral.bikedashboard.ui.background

import kotlin.random.Random

/** One of the real styles, never [previous] (the last launch's pick) unless nothing else is left. */
internal fun pickRandomStyle(previous: BackgroundStyle?, random: Random): BackgroundStyle {
    val pool = BackgroundStyle.randomPool
    val choices = pool.filter { it != previous }.ifEmpty { pool }
    return choices[random.nextInt(choices.size)]
}

/**
 * Random's pick, made once per bike each time the app starts and kept for the rest of that
 * session. The last pick is remembered (see [lastPick]) so the next start shows something else.
 */
object RandomBackground {
    private val picks = HashMap<Long, BackgroundStyle>()
    private var previous: BackgroundStyle? = null
    private var previousRead = false

    /**
     * The style to show for [bikeId]. [lastPick] reads the previous launch's pick (only the first
     * time), and [savePick] stores a new one.
     */
    @Synchronized
    fun resolve(bikeId: Long, lastPick: () -> String?, savePick: (String) -> Unit): BackgroundStyle {
        picks[bikeId]?.let { return it }
        if (!previousRead) {
            previousRead = true
            previous = lastPick()?.let { name -> BackgroundStyle.randomPool.firstOrNull { it.name == name } }
        }
        val pick = pickRandomStyle(previous, Random.Default)
        picks[bikeId] = pick
        savePick(pick.name)
        return pick
    }
}
