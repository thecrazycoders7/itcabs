package com.itcabs.dispatch

data class LegInput(
    val pickup: String,
    val drop: String,
    val area: String = "",
    val timeWindow: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val seats: Int = 1,
)

data class PostJobInput(
    val office: String,
    val shift: String,
    val legs: List<LegInput>,
)

data class LegDto(
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
    val status: String,
    val claimedBy: Long?,
    val version: Int,
)

data class StatusUpdate(val status: String)
data class RatingInput(val stars: Int, val review: String? = null)
