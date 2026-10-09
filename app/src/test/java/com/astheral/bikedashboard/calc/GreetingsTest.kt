package com.astheral.bikedashboard.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingsTest {
    @Test
    fun hoursFallInTheRightPartOfTheDay() {
        assertEquals(DayPart.SMALL_HOURS, DayPart.of(0))
        assertEquals(DayPart.SMALL_HOURS, DayPart.of(4))
        assertEquals(DayPart.MORNING, DayPart.of(5))
        assertEquals(DayPart.MORNING, DayPart.of(11))
        assertEquals(DayPart.AFTERNOON, DayPart.of(12))
        assertEquals(DayPart.AFTERNOON, DayPart.of(16))
        assertEquals(DayPart.EVENING, DayPart.of(17))
        assertEquals(DayPart.EVENING, DayPart.of(21))
        assertEquals(DayPart.LATE_NIGHT, DayPart.of(22))
        assertEquals(DayPart.LATE_NIGHT, DayPart.of(23))
    }

    @Test
    fun sameSeedSameGreeting() {
        for (seed in 0L..50L) assertEquals(Greetings.pick(8, seed), Greetings.pick(8, seed))
    }

    @Test
    fun pickComesFromThatTimeOfDayOrAnyTime() {
        for (hour in 0..23) {
            val allowed = Greetings.byPart.getValue(DayPart.of(hour)) + Greetings.anyTime
            for (seed in 0L..40L) assertTrue(Greetings.pick(hour, seed) in allowed)
        }
    }

    @Test
    fun differentSeedsGiveVariety() {
        val seen = (0L..200L).map { Greetings.pick(9, it) }.toSet()
        assertEquals(Greetings.choicesAt(9).toSet(), seen)
    }

    @Test
    fun bikeNameIsFilledIn() {
        assertEquals("Golden hour suits R3.", Greetings.fill("Golden hour suits {bike}.", "R3"))
        assertEquals("Your bike says hello.", Greetings.fill("{bike} says hello.", null))
        assertEquals("Your bike says hello.", Greetings.fill("{bike} says hello.", "  "))
        assertEquals("Rise and ride.", Greetings.fill("Rise and ride.", "R3"))
    }

    @Test
    fun phrasesAreShortPlainText() {
        val all = Greetings.byPart.values.flatten() + Greetings.anyTime
        assertTrue(all.size in 20..35)
        assertEquals(all.size, all.toSet().size)
        all.forEach { phrase ->
            assertTrue(phrase, phrase.length <= 48)
            assertTrue(phrase, phrase.all { it.code < 128 })
        }
    }
}
