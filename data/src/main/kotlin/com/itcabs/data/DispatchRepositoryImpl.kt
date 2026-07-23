package com.itcabs.data

import com.itcabs.core.database.LegDao
import com.itcabs.core.database.toDomain
import com.itcabs.core.database.toEntity
import com.itcabs.core.network.DispatchApi
import com.itcabs.core.network.RealtimeClient
import com.itcabs.core.network.dto.AssignDto
import com.itcabs.core.network.dto.EditLegDto
import com.itcabs.core.network.dto.LegDto
import com.itcabs.core.network.dto.RatingDto
import com.itcabs.core.network.dto.StageUpdateDto
import com.itcabs.core.network.dto.StatusUpdateDto
import com.itcabs.core.network.dto.TemplateInputDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Area
import com.itcabs.domain.model.CoordinatorStats
import com.itcabs.domain.model.JobTemplate
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewJob
import com.itcabs.domain.model.NewLeg
import com.itcabs.domain.model.TopDriver
import com.itcabs.domain.model.VerifiedDriver
import com.itcabs.domain.repository.DispatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class DispatchRepositoryImpl(
    private val api: DispatchApi,
    private val dao: LegDao,
    private val realtime: RealtimeClient,
) : DispatchRepository {

    override fun legEvents(): Flow<Unit> =
        realtime.events.onStart { realtime.ensureConnected() }

    override fun getMyLegsFlow(userId: Long): Flow<List<Leg>> =
        dao.getMyLegsFlow(userId).map { entities -> entities.map { it.toDomain() } }

    override fun getFeedFlow(): Flow<List<Leg>> =
        dao.getLegsFlow().map { entities -> entities.map { it.toDomain() } }

    override fun getMyClaimsFlow(userId: Long): Flow<List<Leg>> =
        dao.getMyClaimsFlow(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun postJob(job: NewJob): AppResult<List<Leg>> =
        api.postJob(job.toDto()).asResult { dtos ->
            val legs = dtos.map(LegDto::toDomain)
            dao.insertLegs(legs.map { it.toEntity() })
            legs
        }

    override suspend fun repostJob(jobId: Long): AppResult<List<Leg>> =
        api.repost(jobId).asResult { dtos ->
            val legs = dtos.map(LegDto::toDomain)
            dao.insertLegs(legs.map { it.toEntity() })
            legs
        }

    override suspend fun myLegs(): AppResult<List<Leg>> =
        api.myLegs().asResult { dtos ->
            val legs = dtos.map(LegDto::toDomain)
            dao.insertLegs(legs.map { it.toEntity() })
            legs
        }

    override suspend fun coordinatorStats(days: Int?): AppResult<CoordinatorStats> =
        api.coordinatorStats(days).asResult { dto ->
            CoordinatorStats(
                posted = dto.posted, claimed = dto.claimed, completed = dto.completed,
                cancelled = dto.cancelled, fillRatePct = dto.fillRatePct,
                totalPaidPaise = dto.totalPaidPaise, outstandingPaise = dto.outstandingPaise,
                topDrivers = dto.topDrivers.map { TopDriver(it.name, it.trips) },
            )
        }

    override suspend fun setStatus(legId: Long, status: LegStatus): AppResult<Unit> =
        api.setStatus(legId, StatusUpdateDto(status.name)).asResult { }

    override suspend fun editLeg(legId: Long, edit: NewLeg): AppResult<Leg> =
        api.editLeg(
            legId,
            EditLegDto(
                pickup = edit.pickup, drop = edit.drop, area = edit.area, timeWindow = edit.timeWindow,
                vehicleType = edit.vehicleType, farePaise = edit.farePaise, seats = edit.seats,
                passengerName = edit.passengerName, passengerPhone = edit.passengerPhone,
            ),
        ).asResult { dto -> dto.toDomain().also { dao.insertLegs(listOf(it.toEntity())) } }

    override suspend fun verifiedDrivers(): AppResult<List<VerifiedDriver>> =
        api.verifiedDrivers().asResult { list -> list.map { VerifiedDriver(it.id, it.name, it.tripsCompleted, it.noShows) } }

    override suspend fun assign(legId: Long, driverId: Long): AppResult<Leg> =
        api.assign(legId, AssignDto(driverId)).asResult { dto -> dto.toDomain().also { dao.insertLegs(listOf(it.toEntity())) } }

    override suspend fun templates(): AppResult<List<JobTemplate>> =
        api.templates().asResult { list -> list.map { it.toDomain() } }

    override suspend fun saveTemplate(name: String, job: NewJob, vehicleType: String, recurring: Boolean): AppResult<JobTemplate> =
        api.saveTemplate(
            TemplateInputDto(name, job.office, job.shift, vehicleType, job.legs.map { it.toDto() }, recurring),
        ).asResult { it.toDomain() }

    override suspend fun deleteTemplate(id: Long): AppResult<Unit> =
        api.deleteTemplate(id).asResult { }

    override suspend fun postTemplate(id: Long): AppResult<List<Leg>> =
        api.postTemplate(id).asResult { dtos ->
            val legs = dtos.map(LegDto::toDomain)
            dao.insertLegs(legs.map { it.toEntity() })
            legs
        }

    override suspend fun markNoShow(legId: Long): AppResult<Unit> =
        api.noShow(legId).asResult { }

    override suspend fun markPaid(legId: Long): AppResult<Unit> =
        api.markPaid(legId).asResult { }

    override suspend fun rate(legId: Long, stars: Int, review: String?): AppResult<Unit> =
        api.rate(legId, RatingDto(stars, review)).asResult { }

    override suspend fun areas(): AppResult<List<Area>> =
        api.areas().asResult { dtos -> dtos.map { Area(it.name, it.lat, it.lng) } }

    override suspend fun feed(area: String?, vehicleType: String?, lat: Double?, lng: Double?): AppResult<List<Leg>> =
        api.feed(area, vehicleType, lat, lng).asResult { dtos ->
            val legs = dtos.map(LegDto::toDomain)
            // SSoT: only clear and replace if it's a "fresh" full feed request.
            // For now, just insert/update to keep it simple.
            dao.insertLegs(legs.map { it.toEntity() })
            legs
        }

    override suspend fun claim(legId: Long): AppResult<Leg> =
        api.claim(legId).asResult { dto ->
            val leg = dto.toDomain()
            dao.insertLegs(listOf(leg.toEntity()))
            leg
        }

    override suspend fun setStage(legId: Long, stage: String, otp: String?): AppResult<Unit> =
        api.setStage(legId, StageUpdateDto(stage, otp)).asResult { }

    override suspend fun myClaims(): AppResult<List<Leg>> =
        api.myClaims().asResult { dtos ->
            val legs = dtos.map(LegDto::toDomain)
            dao.insertLegs(legs.map { it.toEntity() })
            legs
        }
}
