package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.CoordinatorStats
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewJob
import kotlinx.coroutines.flow.Flow

/**
 * The dispatch workflow against the backend. The claim is server-authoritative and
 * ONLINE-ONLY (ADR-0009): [claim] returns Err(409) when another driver won the race —
 * that's the core first-claim-wins invariant surfaced to the UI.
 */
interface DispatchRepository {
    /** Realtime signal (ADR-0008): emits when any leg changed server-side; collect and re-fetch. */
    fun legEvents(): Flow<Unit>

    // coordinator
    fun getMyLegsFlow(userId: Long): Flow<List<Leg>>
    suspend fun postJob(job: NewJob): AppResult<List<Leg>>
    /** Repost an existing job's route as a fresh OPEN job (M6). */
    suspend fun repostJob(jobId: Long): AppResult<List<Leg>>
    suspend fun myLegs(): AppResult<List<Leg>>
    /** The coordinator's own performance summary for the Insights tab. [days] null = all time. */
    suspend fun coordinatorStats(days: Int?): AppResult<CoordinatorStats>
    suspend fun setStatus(legId: Long, status: LegStatus): AppResult<Unit>
    /** Report a claimed driver as a no-show: dings their reliability and reopens the leg. */
    suspend fun markNoShow(legId: Long): AppResult<Unit>
    /** Coordinator marks a completed leg settled (cash paid to driver). */
    suspend fun markPaid(legId: Long): AppResult<Unit>
    suspend fun rate(legId: Long, stars: Int, review: String?): AppResult<Unit>

    // driver
    fun getFeedFlow(): Flow<List<Leg>>
    suspend fun feed(area: String?, vehicleType: String?): AppResult<List<Leg>>

    /** Err(409) => leg already taken. */
    suspend fun claim(legId: Long): AppResult<Leg>

    /** Driver reports live progress: EN_ROUTE, ARRIVED or STARTED. */
    suspend fun setStage(legId: Long, stage: String): AppResult<Unit>

    fun getMyClaimsFlow(userId: Long): Flow<List<Leg>>
    suspend fun myClaims(): AppResult<List<Leg>>
}
