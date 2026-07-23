package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Area
import com.itcabs.domain.model.CoordinatorStats
import com.itcabs.domain.model.JobTemplate
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewJob
import com.itcabs.domain.model.NewLeg
import com.itcabs.domain.model.VerifiedDriver
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
    /** Edit an OPEN leg in place (fare/time/route/passenger). */
    suspend fun editLeg(legId: Long, edit: NewLeg): AppResult<Leg>
    /** Verified drivers the coordinator can hand-assign a trip to. */
    suspend fun verifiedDrivers(): AppResult<List<VerifiedDriver>>
    /** Directly assign an OPEN leg to a specific verified driver. */
    suspend fun assign(legId: Long, driverId: Long): AppResult<Leg>
    /** Report a claimed driver as a no-show: dings their reliability and reopens the leg. */
    suspend fun markNoShow(legId: Long): AppResult<Unit>
    /** Coordinator marks a completed leg settled (cash paid to driver). */
    suspend fun markPaid(legId: Long): AppResult<Unit>
    suspend fun rate(legId: Long, stars: Int, review: String?): AppResult<Unit>

    // templates (saved routes)
    suspend fun templates(): AppResult<List<JobTemplate>>
    suspend fun saveTemplate(name: String, job: NewJob, vehicleType: String, recurring: Boolean): AppResult<JobTemplate>
    suspend fun deleteTemplate(id: Long): AppResult<Unit>
    suspend fun postTemplate(id: Long): AppResult<List<Leg>>

    // driver
    fun getFeedFlow(): Flow<List<Leg>>
    /** Open legs; pass driver coords for nearest-first ordering + per-leg distance. */
    suspend fun feed(area: String?, vehicleType: String?, lat: Double? = null, lng: Double? = null): AppResult<List<Leg>>
    /** The pickable service areas (backend gazetteer). */
    suspend fun areas(): AppResult<List<Area>>

    /** Err(409) => leg already taken. */
    suspend fun claim(legId: Long): AppResult<Leg>

    /** Driver reports live progress; STARTED requires the passenger's pickup [otp]. */
    suspend fun setStage(legId: Long, stage: String, otp: String? = null): AppResult<Unit>

    fun getMyClaimsFlow(userId: Long): Flow<List<Leg>>
    suspend fun myClaims(): AppResult<List<Leg>>
}
