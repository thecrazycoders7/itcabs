package com.itcabs.data

import com.itcabs.core.network.dto.LegDto
import com.itcabs.core.network.dto.LegInputDto
import com.itcabs.core.network.dto.PostJobDto
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewJob
import com.itcabs.domain.model.NewLeg

fun LegDto.toDomain(): Leg = Leg(
    id = id,
    jobId = jobId,
    coordinatorId = coordinatorId,
    office = office,
    shift = shift,
    pickup = pickup,
    drop = drop,
    area = area,
    timeWindow = timeWindow,
    vehicleType = vehicleType,
    farePaise = farePaise,
    seats = seats,
    status = LegStatus.valueOf(status),
    claimedBy = claimedBy,
    claimedByName = claimedByName,
    version = version,
)

fun NewLeg.toDto(): LegInputDto = LegInputDto(
    pickup = pickup,
    drop = drop,
    area = area,
    timeWindow = timeWindow,
    vehicleType = vehicleType,
    farePaise = farePaise,
    seats = seats,
)

fun NewJob.toDto(): PostJobDto = PostJobDto(office, shift, legs.map(NewLeg::toDto))
