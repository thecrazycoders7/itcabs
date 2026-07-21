package com.itcabs

import com.google.firebase.firestore.DocumentId

enum class Role { COORDINATOR, DRIVER }

enum class LegStatus { OPEN, CLAIMED, CONFIRMED, COMPLETED }

/**
 * A user profile lives at users/{uid}.
 * Drivers must have verified == true before they can claim a leg or be shown to
 * coordinators. `verified` is flipped by an admin/backend after checking the
 * KYC fields — the app only collects them. `blocked` is the fraud kill-switch;
 * because the doc id is the auth uid (a phone-verified account), a blocked
 * driver can't simply reappear without a new phone number.
 */
data class UserProfile(
    @DocumentId val uid: String = "",
    val role: String = Role.DRIVER.name,
    val name: String = "",
    val phone: String = "",
    // Driver KYC (mandatory before verified). Aadhaar/RC are collected here;
    // in production store only a reference/hash + the document image in Storage.
    val vehicleType: String = "",
    val vehicleReg: String = "",
    val aadhaar: String = "",
    val rcNumber: String = "",
    val photoUrl: String = "",
    val verified: Boolean = false,
    val blocked: Boolean = false,
    val ratingSum: Double = 0.0,
    val ratingCount: Long = 0,
) {
    val ratingAvg: Double get() = if (ratingCount == 0L) 0.0 else ratingSum / ratingCount
    // A driver may claim only once every KYC field is present AND an admin verified it.
    val kycComplete: Boolean
        get() = name.isNotBlank() && vehicleType.isNotBlank() && vehicleReg.isNotBlank() &&
                aadhaar.isNotBlank() && rcNumber.isNotBlank() && photoUrl.isNotBlank()
}

/**
 * A job requirement lives at jobs/{jobId} with one doc per leg in the
 * jobs/{jobId}/legs subcollection. Legs are separate docs so a claim is a
 * single-document transaction (first-claim-wins) and drivers can query open
 * legs across all jobs with a collectionGroup query.
 */
data class Job(
    @DocumentId val id: String = "",
    val coordinatorId: String = "",
    val coordinatorName: String = "",
    val office: String = "",
    val shift: String = "",          // e.g. "Login 09:00" / "Logout 18:30"
    val createdAt: Long = 0,
)

data class Leg(
    @DocumentId val id: String = "",
    val jobId: String = "",
    val coordinatorId: String = "",
    val office: String = "",
    val shift: String = "",
    val pickup: String = "",
    val drop: String = "",
    val area: String = "",           // coarse area tag drivers filter/subscribe by
    val timeWindow: String = "",
    val vehicleType: String = "",
    val fare: Long = 0,              // rupees, fixed per leg
    val seats: Int = 1,
    val status: String = LegStatus.OPEN.name,
    val claimedBy: String = "",
    val claimedByName: String = "",
    val claimedAt: Long = 0,
    val rated: Boolean = false,
)
