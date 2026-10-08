package com.astheral.bikedashboard.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        dao = db.categoryDao()
        runBlocking { dao.insertAll(DEFAULT_CATEGORIES) }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun removingACategoryMovesItsRecordsToOther() = runBlocking {
        val profileId = db.profileDao().insert(Profile(name = "R3"))
        db.serviceDao().insert(ServiceRecord(profileId = profileId, date = LocalDate.of(2026, 1, 2), type = DefaultCategory.REPAIR))
        db.serviceDao().insert(ServiceRecord(profileId = profileId, date = LocalDate.of(2026, 2, 2), type = DefaultCategory.REPAIR))
        db.intervalDao().save(ServiceInterval(profileId, DefaultCategory.REPAIR, everyDays = 90))
        assertEquals(2, dao.recordCount(DefaultCategory.REPAIR))

        dao.delete(DefaultCategory.REPAIR)

        assertEquals(listOf(DefaultCategory.OTHER, DefaultCategory.OTHER), db.serviceDao().getAll(profileId).map { it.type })
        assertTrue(db.intervalDao().getAll(profileId).isEmpty())
        assertTrue(dao.getAll().none { it.id == DefaultCategory.REPAIR })
    }

    @Test
    fun otherCantBeRemoved() = runBlocking {
        dao.delete(DefaultCategory.OTHER)
        assertTrue(dao.getAll().any { it.id == DefaultCategory.OTHER })
    }
}
