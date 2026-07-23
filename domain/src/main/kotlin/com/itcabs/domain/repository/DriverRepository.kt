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

    /** The signed-in driver's own KYC status + vehicle. */
    suspend fun myProfile(): AppResult<DriverProfile>

    // admin (is_admin only; enforced server-side)
    suspend fun pendingDrivers(): AppResult<List<PendingDriver>>
    suspend fun verifyDriver(driverId: Long): AppResult<Unit>
    suspend fun rejectDriver(driverId: Long): AppResult<Unit>
}
