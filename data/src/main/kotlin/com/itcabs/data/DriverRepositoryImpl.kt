package com.itcabs.data

import com.itcabs.core.network.DriverApi
import com.itcabs.core.network.dto.KycInputDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.DriverProfile
import com.itcabs.domain.model.KycStatus
import com.itcabs.domain.model.PendingDriver
import com.itcabs.domain.repository.DriverRepository

class DriverRepositoryImpl(private val api: DriverApi) : DriverRepository {
    override suspend fun submitKyc(
        vehicleType: String,
        vehicleReg: String,
        aadhaarRef: String,
        aadhaarMasked: String,
        rcNumberMasked: String,
        photoUrl: String,
    ): AppResult<Unit> = api.submitKyc(
        KycInputDto(vehicleType, vehicleReg, aadhaarRef, aadhaarMasked, rcNumberMasked, photoUrl)
    ).asResult { }

    override suspend fun myProfile(): AppResult<DriverProfile> = api.me().asResult { dto ->
        DriverProfile(
            kycStatus = runCatching { KycStatus.valueOf(dto.kycStatus) }.getOrDefault(KycStatus.NONE),
            vehicleType = dto.vehicleType,
            vehicleReg = dto.vehicleReg,
        )
    }

    override suspend fun pendingDrivers(): AppResult<List<PendingDriver>> =
        api.pendingDrivers().asResult { list ->
            list.map {
                PendingDriver(it.id, it.name ?: "", it.email, it.vehicleType, it.vehicleReg, it.aadhaarMasked, it.rcNumberMasked)
            }
        }

    override suspend fun verifyDriver(driverId: Long): AppResult<Unit> =
        api.verifyDriver(driverId).asResult { }

    override suspend fun rejectDriver(driverId: Long): AppResult<Unit> =
        api.rejectDriver(driverId).asResult { }
}
