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
    tripStage = tripStage,
    paid = paid,
    distanceKm = distanceKm,
    claimedByTrips = claimedByTrips,
    claimedByNoShows = claimedByNoShows,
    passengerName = passengerName,
    passengerPhone = passengerPhone,
    pickupOtp = pickupOtp,
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
    passengerName = passengerName,
    passengerPhone = passengerPhone,
)

fun NewJob.toDto(): PostJobDto = PostJobDto(office, shift, legs.map(NewLeg::toDto), publishAt)

fun com.itcabs.core.network.dto.LegInputDto.toDomain(): NewLeg = NewLeg(
    pickup = pickup, drop = drop, area = area, timeWindow = timeWindow,
    vehicleType = vehicleType, farePaise = farePaise, seats = seats,
    passengerName = passengerName, passengerPhone = passengerPhone,
)

fun com.itcabs.core.network.dto.TemplateDto.toDomain(): com.itcabs.domain.model.JobTemplate =
    com.itcabs.domain.model.JobTemplate(
        id = id, name = name, office = office, shift = shift, vehicleType = vehicleType,
        legs = legs.map { it.toDomain() }, recurring = recurring,
    )
