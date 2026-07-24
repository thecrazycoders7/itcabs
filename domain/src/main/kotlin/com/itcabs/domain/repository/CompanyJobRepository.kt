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
    suspend fun setStatus(jobId: Long, status: LegStatus): AppResult<Unit>
    suspend fun assign(jobId: Long, driverId: Long): AppResult<CompanyJob>

    // driver
    suspend fun feed(): AppResult<List<CompanyJob>>
    suspend fun myTrips(): AppResult<List<CompanyJob>>
    suspend fun claim(jobId: Long): AppResult<CompanyJob>
    suspend fun confirmStopPickup(stopId: Long, otp: String?): AppResult<Unit>
}
