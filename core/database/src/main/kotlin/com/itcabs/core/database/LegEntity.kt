package com.itcabs.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus

@Entity(tableName = "legs")
data class LegEntity(
    @PrimaryKey val id: Long,
    val jobId: Long,
    val coordinatorId: Long,
    val office: String,
    val shift: String,
    val pickup: String,
    val dropPoint: String,
    val area: String,
    val timeWindow: String,
    val vehicleType: String,
    val farePaise: Long,
    val seats: Int,
    val status: String,
    val claimedBy: Long?,
    val claimedByName: String?,
    val version: Int,
)

fun LegEntity.toDomain() = Leg(
    id = id,
    jobId = jobId,
    coordinatorId = coordinatorId,
    office = office,
    shift = shift,
    pickup = pickup,
    drop = dropPoint,
    area = area,
    timeWindow = timeWindow,
    vehicleType = vehicleType,
    farePaise = farePaise,
    seats = seats,
    status = LegStatus.valueOf(status),
    claimedBy = claimedBy,
    claimedByName = claimedByName,
    version = version
)

fun Leg.toEntity() = LegEntity(
    id = id,
    jobId = jobId,
    coordinatorId = coordinatorId,
    office = office,
    shift = shift,
    pickup = pickup,
    dropPoint = drop,
    area = area,
    timeWindow = timeWindow,
    vehicleType = vehicleType,
    farePaise = farePaise,
    seats = seats,
    status = status.name,
    claimedBy = claimedBy,
    claimedByName = claimedByName,
    version = version
)
