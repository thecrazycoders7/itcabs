package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.DriverProfile

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
}
