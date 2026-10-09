package com.astheral.bikedashboard.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RandomBackgroundTest {
    @Test
    fun neverPicksNoneRandomOrTheLastPick() {
        val random = Random(7)
        for (previous in BackgroundStyle.entries + listOf(null)) {
            repeat(200) {
                val pick = pickRandomStyle(previous, random)
                assertNotEquals(BackgroundStyle.NONE, pick)
                assertNotEquals(BackgroundStyle.RANDOM, pick)
                if (previous != null) assertNotEquals(previous, pick)
            }
        }
    }

    @Test
    fun everyRealStyleCanComeUp() {
        val random = Random(1)
        val seen = (1..2000).map { pickRandomStyle(BackgroundStyle.CIRCLES, random) }.toSet()
        assertEquals(BackgroundStyle.randomPool.toSet() - BackgroundStyle.CIRCLES, seen)
    }

    @Test
    fun randomIsSavedByName() {
        assertEquals(BackgroundStyle.RANDOM, BackgroundStyle.fromName("RANDOM"))
        assertTrue(BackgroundStyle.RANDOM !in BackgroundStyle.randomPool)
    }
}
