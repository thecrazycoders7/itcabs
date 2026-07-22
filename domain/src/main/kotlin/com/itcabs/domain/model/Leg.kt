package com.itcabs.domain.model

enum class LegStatus { OPEN, CLAIMED, CONFIRMED, COMPLETED, CANCELLED }

/**
 * A single pickup→drop requirement a driver can claim. [farePaise] is money in paise
 * (never a float). [version] backs optimistic concurrency; a claim bumps it.
 */
data class Leg(
    val id: Long,
    val jobId: Long,
    val coordinatorId: Long,
    val office: String,
    val shift: String,
    val pickup: String,
    val drop: String,
    val area: String,
    val timeWindow: String,
    val vehicleType: String,
    val farePaise: Long,
    val seats: Int,
    val status: LegStatus,
    val claimedBy: Long?,
    val claimedByName: String?,
    val version: Int,
)

/** Coordinator input for posting a job: office + shift + one or more legs. */
data class NewJob(val office: String, val shift: String, val legs: List<NewLeg>)

data class NewLeg(
    val pickup: String,
    val drop: String,
    val area: String = "",
    val timeWindow: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val seats: Int = 1,
)
