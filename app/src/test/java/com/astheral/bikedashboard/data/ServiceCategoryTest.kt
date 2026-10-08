package com.astheral.bikedashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCategoryTest {
    @Test
    fun idsComeFromTheNameAndNeverClash() {
        assertEquals("CHAIN_CLEAN", newCategoryId(" Chain clean! ", emptySet()))
        assertEquals("CHAIN_CLEAN_2", newCategoryId("Chain clean", setOf("CHAIN_CLEAN")))
        assertEquals("CHAIN_CLEAN_3", newCategoryId("chain-clean", setOf("CHAIN_CLEAN", "CHAIN_CLEAN_2")))
        assertEquals("CATEGORY", newCategoryId("!!", emptySet()))
    }

    @Test
    fun newCategoriesGoLast() {
        val c = newCategory("Tyres", DEFAULT_CATEGORIES)
        assertEquals("TYRES", c.id)
        assertEquals(DEFAULT_CATEGORIES.size, c.sortOrder)
    }

    @Test
    fun findsByNameOrId() {
        assertEquals(DefaultCategory.ENGINE_OIL, DEFAULT_CATEGORIES.findByName(" engine OIL ")?.id)
        assertEquals(DefaultCategory.BIKE_WASH, DEFAULT_CATEGORIES.findByName("bike_wash")?.id)
        assertNull(DEFAULT_CATEGORIES.findByName("Tyres"))
    }

    @Test
    fun missingOnesAreAddedWithOtherAlwaysThere() {
        val all = withMissingCategories(listOf(ServiceCategory("TYRES", "Tyres", 0)), listOf("TYRES", "ENGINE_OIL", "CHAIN_CLEAN"))
        assertEquals(listOf("TYRES", "ENGINE_OIL", "CHAIN_CLEAN", "OTHER"), all.map { it.id })
        assertEquals("Engine oil", all[1].name) // a built-in keeps its own name
        assertTrue(all[1].hasInterval)
        assertEquals("Chain clean", all[2].name)
        assertEquals(listOf(0, 1, 2, 3), all.map { it.sortOrder })
    }

    @Test
    fun namesForMissingIds() {
        assertEquals("Chain clean", DEFAULT_CATEGORIES.nameOf("CHAIN_CLEAN"))
        assertEquals("Engine oil", DEFAULT_CATEGORIES.nameOf("ENGINE_OIL"))
    }
}
