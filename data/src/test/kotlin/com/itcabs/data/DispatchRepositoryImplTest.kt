package com.itcabs.data

import com.itcabs.core.database.LegDao
import com.itcabs.core.database.LegEntity
import com.itcabs.core.network.DispatchApi
import com.itcabs.core.network.dto.LegDto
import com.itcabs.core.network.dto.PostJobDto
import com.itcabs.core.network.dto.RatingDto
import com.itcabs.core.network.dto.StatusUpdateDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.LegStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Realtime isn't exercised by these tests (they never collect legEvents), so a dummy that never
// connects is enough to satisfy the constructor.
private fun dummyRealtime() =
    com.itcabs.core.network.RealtimeClient("http://localhost/", { null }, okhttp3.OkHttpClient())

private fun leg(id: Long, status: String, claimedBy: Long? = null) = LegDto(
    id = id, jobId = 1, coordinatorId = 1, office = "O", shift = "S",
    pickup = "P", drop = "D", area = "A", timeWindow = "T", vehicleType = "Sedan",
    farePaise = 42000, seats = 4, status = status, claimedBy = claimedBy, version = 0,
)

/** Fake DispatchApi driven by canned responses — runs on plain JVM, no HTTP. */
private class FakeDispatchApi(
    private val claimResponse: Response<LegDto> = Response.success(leg(1, "CLAIMED", claimedBy = 9)),
    private val feedResponse: Response<List<LegDto>> = Response.success(listOf(leg(1, "OPEN"))),
) : DispatchApi {
    override suspend fun postJob(body: PostJobDto) = Response.success(listOf(leg(1, "OPEN")))
    override suspend fun myLegs() = feedResponse
    override suspend fun setStatus(id: Long, body: StatusUpdateDto) = Response.success(Unit)
    override suspend fun rate(id: Long, body: RatingDto) = Response.success(Unit)
    override suspend fun feed(area: String?, vehicleType: String?) = feedResponse
    override suspend fun claim(id: Long) = claimResponse
    override suspend fun myClaims() = feedResponse
}

/** No-op LegDao — these tests exercise API mapping + the 409 path, not the local cache. */
private class FakeLegDao : LegDao {
    override fun getLegsFlow(): Flow<List<LegEntity>> = flowOf(emptyList())
    override fun getMyLegsFlow(userId: Long): Flow<List<LegEntity>> = flowOf(emptyList())
    override fun getMyClaimsFlow(userId: Long): Flow<List<LegEntity>> = flowOf(emptyList())
    override suspend fun insertLegs(legs: List<LegEntity>) = Unit
    override suspend fun clearAll() = Unit
    override suspend fun clearMyLegs(userId: Long) = Unit
}

class DispatchRepositoryImplTest {

    @Test
    fun claim_won_maps_leg_to_domain() = runTest {
        val repo = DispatchRepositoryImpl(FakeDispatchApi(), FakeLegDao(), dummyRealtime())

        val ok = assertIs<AppResult.Ok<*>>(repo.claim(1))
        val claimed = ok.value as com.itcabs.domain.model.Leg
        assertEquals(LegStatus.CLAIMED, claimed.status)
        assertEquals(9, claimed.claimedBy)
    }

    @Test
    fun claim_lost_is_409() = runTest {
        // The core first-claim-wins invariant surfaced to the client: another driver won.
        val conflict: Response<LegDto> = Response.error(
            409,
            """{"error":"leg already taken"}""".toResponseBody("application/json".toMediaType()),
        )
        val repo = DispatchRepositoryImpl(FakeDispatchApi(claimResponse = conflict), FakeLegDao(), dummyRealtime())

        val err = assertIs<AppResult.Err>(repo.claim(1))
        assertEquals(409, err.code)
    }

    @Test
    fun feed_maps_list_and_status() = runTest {
        val repo = DispatchRepositoryImpl(FakeDispatchApi(), FakeLegDao(), dummyRealtime())

        val ok = assertIs<AppResult.Ok<*>>(repo.feed(area = null, vehicleType = null))
        @Suppress("UNCHECKED_CAST")
        val legs = ok.value as List<com.itcabs.domain.model.Leg>
        assertEquals(1, legs.size)
        assertEquals(LegStatus.OPEN, legs.first().status)
    }
}
