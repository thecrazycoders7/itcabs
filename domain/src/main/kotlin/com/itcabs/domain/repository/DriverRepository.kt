package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.DriverProfile
import com.itcabs.domain.model.PendingDriver

interface DriverRepository {
    suspend fun submitKyc(
        vehicleType: String,
        vehicleReg: String,
        aadhaarRef: String,
        aadhaarMasked: String,
        rcNumberMasked: String,
        photoUrl: String,
    ): AppResult<Unit>

    /** The signed-in driver's own KYC status + vehicle + reliability + rating. */
    suspend fun myProfile(): AppResult<DriverProfile>

    /** Toggle whether this driver receives new-trip pushes. */
    suspend fun setAvailability(available: Boolean): AppResult<Unit>

    /** A driver's public profile (for the coordinator once the driver is on their job). */
    suspend fun publicProfile(driverId: Long): AppResult<com.itcabs.domain.model.PublicDriverProfile>

    // admin (is_admin only; enforced server-side)
    suspend fun pendingDrivers(): AppResult<List<PendingDriver>>
    suspend fun verifyDriver(driverId: Long): AppResult<Unit>
    suspend fun rejectDriver(driverId: Long, reason: String?): AppResult<Unit>
    suspend fun allDrivers(): AppResult<List<com.itcabs.domain.model.AdminDriver>>
    suspend fun blockUser(userId: Long): AppResult<Unit>
    suspend fun unblockUser(userId: Long): AppResult<Unit>
}
