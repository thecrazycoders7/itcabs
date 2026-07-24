package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.CompanyJob
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewCompanyJob
import com.itcabs.domain.model.NewStop

/** Multi-stop corporate jobs (company + ordered employee stops, one driver). */
interface CompanyJobRepository {
    // coordinator
    suspend fun create(job: NewCompanyJob): AppResult<CompanyJob>
    suspend fun mine(): AppResult<List<CompanyJob>>
    suspend fun replaceStops(jobId: Long, stops: List<NewStop>): AppResult<Unit>
    /** Full edit while OPEN (job fields + stops). */
    suspend fun edit(jobId: Long, job: NewCompanyJob): AppResult<CompanyJob>
    suspend fun setStatus(jobId: Long, status: LegStatus): AppResult<Unit>

    suspend fun markPaid(jobId: Long): AppResult<Unit>
    suspend fun markNoShow(jobId: Long): AppResult<Unit>
    /** The claimed driver's latest location for the company route map. */
    suspend fun driverLocation(jobId: Long): AppResult<com.itcabs.domain.model.DriverLocation>

    // driver
    suspend fun feed(): AppResult<List<CompanyJob>>
    suspend fun myTrips(): AppResult<List<CompanyJob>>
    suspend fun claim(jobId: Long): AppResult<CompanyJob>
    suspend fun confirmStopPickup(stopId: Long, otp: String?): AppResult<Unit>
    suspend fun releaseTrip(jobId: Long): AppResult<Unit>
    suspend fun driverComplete(jobId: Long): AppResult<Unit>
}
