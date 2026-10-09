package com.astheral.bikedashboard.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FillPhrasesTest {
    private fun assertShortAndUnique(all: List<String>) {
        assertEquals(all.size, all.toSet().size)
        all.forEach { phrase -> assertTrue(phrase, phrase.codePointCount(0, phrase.length) <= 56) }
    }

    @Test
    fun everyMoodHasPhrases() {
        SummaryMood.entries.forEach { assertTrue(it.name, FillPhrases.byMood.getValue(it).size >= 3) }
        assertShortAndUnique(FillPhrases.byMood.values.flatten())
    }

    @Test
    fun flavourPickIsStableAndFromItsMood() {
        for (mood in SummaryMood.entries) {
            for (seed in 0L..40L) {
                assertEquals(FillPhrases.pick(mood, seed), FillPhrases.pick(mood, seed))
                assertTrue(FillPhrases.pick(mood, seed) in FillPhrases.byMood.getValue(mood))
            }
        }
        val seen = (0L..200L).map { FillPhrases.pick(SummaryMood.SAVED, it) }.toSet()
        assertEquals(FillPhrases.byMood.getValue(SummaryMood.SAVED).toSet(), seen)
    }

    @Test
    fun sendOffComesFromThatTimeOfDayOrAnyTime() {
        for (hour in 0..23) {
            val allowed = SendOffs.byPart.getValue(DayPart.of(hour)) + SendOffs.anyTime
            for (seed in 0L..40L) assertTrue(SendOffs.pick(hour, seed) in allowed)
        }
        val seen = (0L..300L).map { SendOffs.pick(23, it) }.toSet()
        assertEquals(SendOffs.choicesAt(23).toSet(), seen)
    }

    @Test
    fun sendOffsAreShortUniqueAndCoverTheDay() {
        DayPart.entries.forEach { assertTrue(it.name, SendOffs.byPart.getValue(it).isNotEmpty()) }
        assertShortAndUnique(SendOffs.byPart.values.flatten() + SendOffs.anyTime)
    }
}
