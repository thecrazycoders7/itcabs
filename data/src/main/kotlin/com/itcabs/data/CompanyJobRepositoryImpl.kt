package com.itcabs.data

import com.itcabs.core.network.CompanyJobApi
import com.itcabs.core.network.dto.CompanyAssignDto
import com.itcabs.core.network.dto.CompanyJobDto
import com.itcabs.core.network.dto.CompanyJobInputDto
import com.itcabs.core.network.dto.StatusUpdateDto
import com.itcabs.core.network.dto.StopDto
import com.itcabs.core.network.dto.StopInputDto
import com.itcabs.core.network.dto.StopPickupDto
import com.itcabs.core.network.dto.StopsUpdateDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.CompanyJob
import com.itcabs.domain.model.JobStop
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewCompanyJob
import com.itcabs.domain.model.NewStop
import com.itcabs.domain.model.TripType
import com.itcabs.domain.repository.CompanyJobRepository

class CompanyJobRepositoryImpl(private val api: CompanyJobApi) : CompanyJobRepository {

    override suspend fun create(job: NewCompanyJob): AppResult<CompanyJob> =
        api.create(job.toDto()).asResult { it.toDomain() }

    override suspend fun mine(): AppResult<List<CompanyJob>> =
        api.mine().asResult { list -> list.map { it.toDomain() } }

    override suspend fun replaceStops(jobId: Long, stops: List<NewStop>): AppResult<Unit> =
        api.replaceStops(jobId, StopsUpdateDto(stops.map { it.toDto() })).asResult { }

    override suspend fun setStatus(jobId: Long, status: LegStatus): AppResult<Unit> =
        api.setStatus(jobId, StatusUpdateDto(status.name)).asResult { }

    override suspend fun edit(jobId: Long, job: NewCompanyJob): AppResult<CompanyJob> =
        api.edit(jobId, job.toDto()).asResult { it.toDomain() }

    override suspend fun feed(): AppResult<List<CompanyJob>> =
        api.feed().asResult { list -> list.map { it.toDomain() } }

    override suspend fun myTrips(): AppResult<List<CompanyJob>> =
        api.myTrips().asResult { list -> list.map { it.toDomain() } }

    override suspend fun claim(jobId: Long): AppResult<CompanyJob> =
        api.claim(jobId).asResult { it.toDomain() }

    override suspend fun confirmStopPickup(stopId: Long, otp: String?): AppResult<Unit> =
        api.confirmStopPickup(stopId, StopPickupDto(otp)).asResult { }

    override suspend fun markPaid(jobId: Long): AppResult<Unit> = api.markPaid(jobId).asResult { }
    override suspend fun markNoShow(jobId: Long): AppResult<Unit> = api.noShow(jobId).asResult { }
    override suspend fun releaseTrip(jobId: Long): AppResult<Unit> = api.release(jobId).asResult { }
    override suspend fun driverComplete(jobId: Long): AppResult<Unit> = api.complete(jobId).asResult { }
    override suspend fun driverLocation(jobId: Long): AppResult<com.itcabs.domain.model.DriverLocation> =
        api.driverLocation(jobId).asResult { com.itcabs.domain.model.DriverLocation(it.lat, it.lng, it.updatedAt) }
}

private fun NewStop.toDto() = StopInputDto(employeeName, address, lat, lng, placeId, phone)

private fun NewCompanyJob.toDto() = CompanyJobInputDto(
    companyName = companyName, tripType = tripType.name, office = office,
    officeAddress = officeAddress, officeLat = officeLat, officeLng = officeLng, officePlaceId = officePlaceId,
    pickupTime = pickupTime, dropTime = dropTime, vehicleType = vehicleType, vehicleAc = vehicleAc,
    farePaise = farePaise, publishAt = publishAt, stops = stops.map { it.toDto() },
)

private fun StopDto.toDomain() = JobStop(id, employeeName, address, lat, lng, placeId, phone, stopOrder, pickedUp, pickupOtp)

private fun CompanyJobDto.toDomain() = CompanyJob(
    id = id, coordinatorId = coordinatorId, companyName = companyName,
    tripType = runCatching { TripType.valueOf(tripType) }.getOrDefault(TripType.PICKUP),
    office = office, officeAddress = officeAddress, officeLat = officeLat, officeLng = officeLng,
    pickupTime = pickupTime, dropTime = dropTime, vehicleType = vehicleType, vehicleAc = vehicleAc,
    farePaise = farePaise,
    status = runCatching { LegStatus.valueOf(status) }.getOrDefault(LegStatus.OPEN),
    claimedBy = claimedBy, claimedByName = claimedByName, paid = paid,
    stops = stops.map { it.toDomain() }, version = version,
)
