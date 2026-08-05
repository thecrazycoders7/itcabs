package com.itcabs.domain.model

/** NONE = not submitted; PENDING = awaiting admin verify; VERIFIED = can claim; REJECTED = denied. */
enum class KycStatus { NONE, PENDING, VERIFIED, REJECTED }

data class DriverProfile(
    val kycStatus: KycStatus,
    val vehicleType: String?,
    val vehicleReg: String?,
    val tripsCompleted: Int = 0,
    val noShows: Int = 0,
    val rejectionReason: String? = null,
    val available: Boolean = true,
    val avgRating: Double? = null,
    val ratingCount: Int = 0,
    val phone: String? = null,
    val phoneVerified: Boolean = false,
)

/** A driver's earnings across legs + company jobs. Money in paise. */
data class DriverEarnings(
    val totalEarnedPaise: Long = 0,
    val pendingPaise: Long = 0,
    val tripsCompleted: Int = 0,
    val thisWeekPaise: Long = 0,
    val recent: List<RecentEarning> = emptyList(),
)

/** One completed trip in the earnings history. */
data class RecentEarning(
    val label: String,
    val isCompany: Boolean,
    val amountPaise: Long,
    val paid: Boolean,
    val date: String?,
)

/** One uploaded KYC document + its review state. status: UPLOADED / REUPLOAD_REQUESTED. */
data class KycDoc(
    val docType: String,
    val storagePath: String,
    val status: String = "UPLOADED",
    val rejectReason: String? = null,
)

/** A driver's public profile shown to the coordinator once the driver is on their job. */
data class PublicDriverProfile(
    val id: Long,
    val name: String,
    val phone: String?,
    val email: String?,
    val vehicleType: String?,
    val vehicleReg: String?,
    val kycStatus: KycStatus,
    val tripsCompleted: Int,
    val noShows: Int,
    val photoUrl: String?,
    val avgRating: Double?,
    val ratingCount: Int,
)

/** A driver row in the admin roster (for block/unblock). */
data class AdminDriver(
    val id: Long,
    val name: String,
    val status: String,      // ACTIVE / BLOCKED
    val kycStatus: KycStatus,
    val tripsCompleted: Int,
    val noShows: Int,
)

/** A driver awaiting KYC approval, shown in the admin review queue. */
data class PendingDriver(
    val id: Long,
    val name: String,
    val email: String?,
    val vehicleType: String?,
    val vehicleReg: String?,
    val aadhaarMasked: String?,
    val rcNumberMasked: String?,
)
