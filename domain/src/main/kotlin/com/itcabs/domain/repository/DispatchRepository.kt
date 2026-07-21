package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewJob

/**
 * The dispatch workflow against the backend. The claim is server-authoritative and
 * ONLINE-ONLY (ADR-0009): [claim] returns Err(409) when another driver won the race —
 * that's the core first-claim-wins invariant surfaced to the UI.
 */
interface DispatchRepository {
    // coordinator
    suspend fun postJob(job: NewJob): AppResult<List<Leg>>
    suspend fun myLegs(): AppResult<List<Leg>>
    suspend fun setStatus(legId: Long, status: LegStatus): AppResult<Unit>
    suspend fun rate(legId: Long, stars: Int, review: String?): AppResult<Unit>

    // driver
    suspend fun feed(area: String?, vehicleType: String?): AppResult<List<Leg>>
    /** Err(409) => leg already taken. */
    suspend fun claim(legId: Long): AppResult<Leg>
    suspend fun myClaims(): AppResult<List<Leg>>
}
