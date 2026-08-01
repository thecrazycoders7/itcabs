package com.itcabs.dispatch

// --- inputs ---

data class StopInput(
    val employeeName: String,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val placeId: String? = null,
    val phone: String = "",
)

data class CompanyJobInput(
    val companyName: String,
    val tripType: String,          // PICKUP or DROP
    val office: String = "",
    val officeAddress: String = "",
    val officeLat: Double? = null,
    val officeLng: Double? = null,
    val officePlaceId: String? = null,
    val pickupTime: String = "",   // "HH:mm"
    val dropTime: String = "",
    val vehicleType: String = "",  // SEDAN or SUV
    val vehicleAc: Boolean = true,
    val farePaise: Long,
    val publishAt: String? = null, // ISO-8601; future = scheduled
    val stops: List<StopInput>,
)

/** Replace the full ordered stop list of an OPEN job (covers edit / remove / reorder). */
data class StopsUpdate(val stops: List<StopInput>)

/** Driver confirms pickup at a stop with the employee's OTP. */
data class StopPickupInput(val otp: String? = null)

// --- outputs ---

data class StopDto(
    val id: Long,
    val employeeName: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val placeId: String?,
    val phone: String,
    val stopOrder: Int,
    val pickedUp: Boolean,
    /** Present to the owning coordinator only; null for the driver (they must obtain it). */
    val pickupOtp: String? = null,
)

data class CompanyJobDto(
    val id: Long,
    val coordinatorId: Long,
    val companyName: String,
    val tripType: String,
    val office: String,
    val vehicleType: String,
    val farePaise: Long,
    val officeAddress: String = "",
    val officeLat: Double? = null,
    val officeLng: Double? = null,
    val pickupTime: String = "",
    val dropTime: String = "",
    val vehicleAc: Boolean = true,
    val status: String,
    val claimedBy: Long?,
    val claimedByName: String?,
    val paid: Boolean = false,
    val stops: List<StopDto>,
    val version: Int,
)
