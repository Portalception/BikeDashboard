package com.astheral.bikedashboard.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FillUpDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: FillUpDao

    private val sgFill = FillUp(
        date = LocalDate.of(2026, 9, 23), country = Country.SG, odometerKm = 162999,
        sgLitres = 9.713, sgCostSgd = 26.44, barsLeft = 1,
    )
    private val myFill = FillUp(
        date = LocalDate.of(2026, 10, 3), country = Country.MY, odometerKm = 163903,
        myLitres = 6.54, myCostSgd = 10.27, rmPaid = 60.0, rmPerSgd = 3.1847, rmCharged = 32.70,
    )
    private val olderMyFill = FillUp(
        date = LocalDate.of(2026, 9, 16), country = Country.MY, odometerKm = 162732,
        myLitres = 13.74, myCostSgd = 19.32, rmPaid = 70.0, rmPerSgd = 3.2007, rmCharged = 61.83,
        reserveKm = 51.0, estimatedMax = -55.84, importedSavingsSgd = 11.51,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        dao = db.fillUpDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun savedFillUpComesBackUnchanged() = runBlocking {
        val id = dao.insert(olderMyFill)
        assertEquals(olderMyFill.copy(id = id), dao.getById(id))
    }

    @Test
    fun listIsOldestFirst() = runBlocking {
        dao.insertAll(listOf(myFill, sgFill, olderMyFill))
        val dates = dao.observeAll().first().map { it.date }
        assertEquals(listOf(olderMyFill.date, sgFill.date, myFill.date), dates)
    }

    @Test
    fun latestRateSkipsFillUpsWithoutOne() = runBlocking {
        assertNull(dao.latestRmPerSgd())
        dao.insertAll(listOf(olderMyFill, myFill, sgFill))
        assertEquals(3.1847, dao.latestRmPerSgd()!!, 0.0)
    }

    @Test
    fun replaceAllSwapsOutEverything() = runBlocking {
        dao.insertAll(listOf(sgFill, myFill))
        dao.replaceAll(listOf(olderMyFill))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(olderMyFill.odometerKm, all.single().odometerKm)
    }

    @Test
    fun updateAndDelete() = runBlocking {
        val id = dao.insert(sgFill)
        dao.update(sgFill.copy(id = id, sgCostSgd = 26.50))
        assertEquals(26.50, dao.getById(id)!!.sgCostSgd!!, 0.0)
        dao.delete(dao.getById(id)!!)
        assertNull(dao.getById(id))
    }
}
