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
    /** Live driver-reported progress: EN_ROUTE, ARRIVED, STARTED, or null (not moving yet). */
    val tripStage: String? = null,
    /** True once the coordinator has settled (paid) this completed leg. */
    val paid: Boolean = false,
    /** Km from the driver to this leg's area (feed only); null when location/area unknown. */
    val distanceKm: Double? = null,
    val claimedByTrips: Int? = null,
    val claimedByNoShows: Int? = null,
    val passengerName: String = "",
    val passengerPhone: String = "",
    /** Pickup code — coordinator-only (relayed to the passenger); the driver enters it to start. */
    val pickupOtp: String? = null,
    val version: Int,
)

/** A pickable service area (name + centroid) from the backend gazetteer. */
data class Area(val name: String, val lat: Double, val lng: Double)

/** Coordinator input for posting a job: office + shift + one or more legs. [publishAt] schedules it. */
data class NewJob(val office: String, val shift: String, val legs: List<NewLeg>, val publishAt: String? = null)

data class NewLeg(
    val pickup: String,
    val drop: String,
    val area: String = "",
    val timeWindow: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val seats: Int = 1,
    val passengerName: String = "",
    val passengerPhone: String = "",
)

/** A verified driver a coordinator can hand-assign a trip to. */
data class VerifiedDriver(val id: Long, val name: String, val tripsCompleted: Int, val noShows: Int)

/** A saved route the coordinator can re-post with one tap (optionally recurring). */
data class JobTemplate(
    val id: Long,
    val name: String,
    val office: String,
    val shift: String,
    val vehicleType: String,
    val legs: List<NewLeg>,
    val recurring: Boolean,
)
