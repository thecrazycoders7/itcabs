package com.itcabs.core.network.dto

import kotlinx.serialization.Serializable

// Wire shapes for /api/v1 dispatch endpoints — mirror the backend DTOs exactly.

@Serializable
data class LegInputDto(
    val pickup: String,
    val drop: String,
    val area: String = "",
    val timeWindow: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val seats: Int = 1,
)

@Serializable
data class PostJobDto(
    val office: String,
    val shift: String,
    val legs: List<LegInputDto>,
)

@Serializable
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
    val claimedBy: Long? = null,
    val version: Int,
)

@Serializable
data class StatusUpdateDto(val status: String)

@Serializable
data class RatingDto(val stars: Int, val review: String? = null)
