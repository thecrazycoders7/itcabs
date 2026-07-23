package com.itcabs.domain.model

/** A coordinator's own performance summary (Insights tab). Money fields are paise. */
data class CoordinatorStats(
    val posted: Int,
    val claimed: Int,
    val completed: Int,
    val cancelled: Int,
    val fillRatePct: Int,
    val totalPaidPaise: Long,
    val outstandingPaise: Long,
    val topDrivers: List<TopDriver>,
)

data class TopDriver(val name: String, val trips: Int)
