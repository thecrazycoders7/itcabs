package com.itcabs.data

import com.itcabs.core.database.LegDao
import com.itcabs.core.database.toDomain
import com.itcabs.core.database.toEntity
import com.itcabs.core.network.DispatchApi
import com.itcabs.core.network.RealtimeClient
import com.itcabs.core.network.dto.LegDto
import com.itcabs.core.network.dto.RatingDto
import com.itcabs.core.network.dto.StageUpdateDto
import com.itcabs.core.network.dto.StatusUpdateDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.CoordinatorStats
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewJob
import com.itcabs.domain.model.TopDriver
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

    override suspend fun markNoShow(legId: Long): AppResult<Unit> =
        api.noShow(legId).asResult { }

    override suspend fun markPaid(legId: Long): AppResult<Unit> =
        api.markPaid(legId).asResult { }

    override suspend fun rate(legId: Long, stars: Int, review: String?): AppResult<Unit> =
        api.rate(legId, RatingDto(stars, review)).asResult { }

    override suspend fun feed(area: String?, vehicleType: String?): AppResult<List<Leg>> =
        api.feed(area, vehicleType).asResult { dtos ->
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

    override suspend fun setStage(legId: Long, stage: String): AppResult<Unit> =
        api.setStage(legId, StageUpdateDto(stage)).asResult { }

    override suspend fun myClaims(): AppResult<List<Leg>> =
        api.myClaims().asResult { dtos ->
            val legs = dtos.map(LegDto::toDomain)
            dao.insertLegs(legs.map { it.toEntity() })
            legs
        }
}
