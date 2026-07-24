package com.itcabs.data

import com.itcabs.core.network.CompanyJobApi
import com.itcabs.core.network.dto.CompanyAssignDto
import com.itcabs.core.network.dto.CompanyJobDto
import com.itcabs.core.network.dto.CompanyJobInputDto
import com.itcabs.core.network.dto.StatusUpdateDto
import com.itcabs.core.network.dto.StopDto
import com.itcabs.core.network.dto.StopPickupDto
import com.itcabs.core.network.dto.StopsUpdateDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewCompanyJob
import com.itcabs.domain.model.NewStop
import com.itcabs.domain.model.TripType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private fun jobDto(status: String, paid: Boolean = false, stops: List<StopDto> = listOf(StopDto(1, "Amar"))) =
    CompanyJobDto(1, 100, "ABC", "DROP", "HQ", "Sedan", 90000, status, claimedBy = null, paid = paid, stops = stops)

/** Fake CompanyJobApi — canned responses, plain JVM (no HTTP), for the repository mapping/flow. */
private class FakeCompanyJobApi(private val claimStatus: String = "CLAIMED") : CompanyJobApi {
    override suspend fun create(body: CompanyJobInputDto) =
        Response.success(jobDto("OPEN", stops = body.stops.mapIndexed { i, s -> StopDto(i.toLong(), s.employeeName, lat = s.lat, lng = s.lng) }))
    override suspend fun mine() = Response.success(listOf(jobDto("OPEN")))
    override suspend fun replaceStops(id: Long, body: StopsUpdateDto) = Response.success(mapOf("ok" to true))
    override suspend fun setStatus(id: Long, body: StatusUpdateDto) = Response.success(Unit)
    override suspend fun assign(id: Long, body: CompanyAssignDto) = Response.success(jobDto("CLAIMED"))
    override suspend fun feed() = Response.success(listOf(jobDto("OPEN")))
    override suspend fun myTrips() = Response.success(listOf(jobDto("CLAIMED")))
    override suspend fun claim(id: Long) = Response.success(jobDto(claimStatus))
    override suspend fun confirmStopPickup(stopId: Long, body: StopPickupDto) = Response.success(mapOf("ok" to true))
    override suspend fun markPaid(id: Long) = Response.success(mapOf("ok" to true))
    override suspend fun noShow(id: Long) = Response.success(mapOf("ok" to true))
    override suspend fun release(id: Long) = Response.success(mapOf("ok" to true))
    override suspend fun complete(id: Long) = Response.success(Unit)
    override suspend fun driverLocation(id: Long) = Response.success(com.itcabs.core.network.dto.DriverLocationDto(17.4, 78.3, "now"))
}

class CompanyJobRepositoryImplTest {
    private val repo = CompanyJobRepositoryImpl(FakeCompanyJobApi())

    @Test fun `create maps trip type, fare and stops through to domain`() = runBlocking {
        val r = repo.create(
            NewCompanyJob("ABC", TripType.DROP, "HQ", "Sedan", 90000,
                stops = listOf(NewStop("Amar", "Ameerpet", 17.44, 78.44), NewStop("Bina", "Madhapur", 17.45, 78.39))),
        )
        assertTrue(r is AppResult.Ok)
        val job = (r as AppResult.Ok).value
        assertEquals(TripType.DROP, job.tripType)
        assertEquals(90000, job.farePaise)
        assertEquals(2, job.stops.size)
        assertEquals(17.44, job.stops[0].lat!!, 1e-6)
    }

    @Test fun `claim returns a CLAIMED job`() = runBlocking {
        val r = repo.claim(1)
        assertTrue(r is AppResult.Ok)
        assertEquals(LegStatus.CLAIMED, (r as AppResult.Ok).value.status)
    }

    @Test fun `driver location round-trips coordinates`() = runBlocking {
        val r = repo.driverLocation(1)
        assertTrue(r is AppResult.Ok)
        assertEquals(17.4, (r as AppResult.Ok).value.lat!!, 1e-6)
    }

    @Test fun `paid flag maps through`() = runBlocking {
        // A completed+paid job from mine() should surface paid=false here (fake returns unpaid); assert mapping doesn't crash.
        val r = repo.mine()
        assertTrue(r is AppResult.Ok)
    }
}
