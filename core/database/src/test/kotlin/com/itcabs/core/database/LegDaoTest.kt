package com.itcabs.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegDaoTest {

    private lateinit var db: ItCabsDatabase
    private lateinit var dao: LegDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ItCabsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.legDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadLegs() = runBlocking {
        val legs = listOf(
            LegEntity(
                id = 1, jobId = 10, coordinatorId = 100, office = "O", shift = "S",
                pickup = "P", dropPoint = "D", area = "A", timeWindow = "T",
                vehicleType = "V", farePaise = 1000, seats = 1, status = "OPEN",
                claimedBy = null, claimedByName = null, version = 1
            )
        )
        dao.insertLegs(legs)

        val flow = dao.getLegsFlow().first()
        assertEquals(1, flow.size)
        assertEquals("P", flow[0].pickup)
    }

    @Test
    fun getMyLegsFlow() = runBlocking {
        val legs = listOf(
            LegEntity(
                id = 1, jobId = 10, coordinatorId = 100, office = "O", shift = "S",
                pickup = "P1", dropPoint = "D1", area = "A", timeWindow = "T",
                vehicleType = "V", farePaise = 1000, seats = 1, status = "OPEN",
                claimedBy = null, claimedByName = null, version = 1
            ),
            LegEntity(
                id = 2, jobId = 11, coordinatorId = 101, office = "O", shift = "S",
                pickup = "P2", dropPoint = "D2", area = "A", timeWindow = "T",
                vehicleType = "V", farePaise = 1000, seats = 1, status = "OPEN",
                claimedBy = null, claimedByName = null, version = 1
            )
        )
        dao.insertLegs(legs)

        val flow = dao.getMyLegsFlow(100).first()
        assertEquals(1, flow.size)
        assertEquals("P1", flow[0].pickup)
    }
}
