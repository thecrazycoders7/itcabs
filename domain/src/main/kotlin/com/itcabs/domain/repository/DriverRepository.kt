package com.itcabs.domain.repository

import com.itcabs.domain.AppResult

interface DriverRepository {
    suspend fun submitKyc(
        vehicleType: String,
        vehicleReg: String,
        aadhaarRef: String,
        aadhaarMasked: String,
        rcNumberMasked: String,
        photoUrl: String,
    ): AppResult<Unit>
}
