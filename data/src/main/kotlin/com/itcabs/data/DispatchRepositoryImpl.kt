package com.itcabs.data

import com.itcabs.core.network.DispatchApi
import com.itcabs.core.network.dto.LegDto
import com.itcabs.core.network.dto.RatingDto
import com.itcabs.core.network.dto.StatusUpdateDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewJob
import com.itcabs.domain.repository.DispatchRepository

class DispatchRepositoryImpl(private val api: DispatchApi) : DispatchRepository {

    override suspend fun postJob(job: NewJob): AppResult<List<Leg>> =
        api.postJob(job.toDto()).asResult { it.map(LegDto::toDomain) }

    override suspend fun myLegs(): AppResult<List<Leg>> =
        api.myLegs().asResult { it.map(LegDto::toDomain) }

    override suspend fun setStatus(legId: Long, status: LegStatus): AppResult<Unit> =
        api.setStatus(legId, StatusUpdateDto(status.name)).asResult { }

    override suspend fun rate(legId: Long, stars: Int, review: String?): AppResult<Unit> =
        api.rate(legId, RatingDto(stars, review)).asResult { }

    override suspend fun feed(area: String?, vehicleType: String?): AppResult<List<Leg>> =
        api.feed(area, vehicleType).asResult { it.map(LegDto::toDomain) }

    override suspend fun claim(legId: Long): AppResult<Leg> =
        api.claim(legId).asResult { it.toDomain() }

    override suspend fun myClaims(): AppResult<List<Leg>> =
        api.myClaims().asResult { it.map(LegDto::toDomain) }
}
