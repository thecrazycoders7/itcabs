package com.itcabs.data

import com.itcabs.core.network.DriverApi
import com.itcabs.core.network.dto.AvailabilityDto
import com.itcabs.core.network.dto.KycInputDto
import com.itcabs.core.network.dto.RejectInputDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.DriverProfile
import com.itcabs.domain.model.KycStatus
import com.itcabs.domain.model.PendingDriver
import com.itcabs.domain.repository.DriverRepository

class DriverRepositoryImpl(
    private val api: DriverApi,
    private val storage: SupabaseStorage,
) : DriverRepository {
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
            tripsCompleted = dto.tripsCompleted,
            noShows = dto.noShows,
            rejectionReason = dto.rejectionReason,
            available = dto.available,
            avgRating = dto.avgRating,
            ratingCount = dto.ratingCount,
            phone = dto.phone,
            phoneVerified = dto.phoneVerified,
        )
    }

    override suspend fun setAvailability(available: Boolean): AppResult<Unit> =
        api.setAvailability(AvailabilityDto(available)).asResult { }

    override suspend fun earnings(): AppResult<com.itcabs.domain.model.DriverEarnings> =
        api.earnings().asResult { d ->
            com.itcabs.domain.model.DriverEarnings(
                totalEarnedPaise = d.totalEarnedPaise,
                pendingPaise = d.pendingPaise,
                tripsCompleted = d.tripsCompleted,
                thisWeekPaise = d.thisWeekPaise,
                recent = d.recent.map {
                    com.itcabs.domain.model.RecentEarning(
                        label = it.label, isCompany = it.kind == "COMPANY",
                        amountPaise = it.amountPaise, paid = it.paid, date = it.date,
                    )
                },
            )
        }

    override suspend fun verifyPhone(idToken: String): AppResult<Unit> =
        api.verifyPhone(com.itcabs.core.network.dto.PhoneVerifyDto(idToken)).asResult { }

    override suspend fun uploadKycDoc(docType: String, jpeg: ByteArray): AppResult<Unit> =
        when (val up = storage.upload("$docType.jpg", jpeg)) {
            is AppResult.Ok -> api.registerKycDoc(
                com.itcabs.core.network.dto.KycDocInputDto(docType, up.value)
            ).asResult { }
            is AppResult.Err -> up
        }

    override suspend fun uploadDriverPhoto(jpeg: ByteArray): AppResult<String> = storage.uploadPublicPhoto(jpeg)

    override suspend fun myKycDocs(): AppResult<List<com.itcabs.domain.model.KycDoc>> =
        api.myKycDocs().asResult { list ->
            list.map { com.itcabs.domain.model.KycDoc(it.docType, it.storagePath, it.status, it.rejectReason) }
        }

    override suspend fun publicProfile(driverId: Long): AppResult<com.itcabs.domain.model.PublicDriverProfile> =
        api.publicProfile(driverId).asResult { d ->
            com.itcabs.domain.model.PublicDriverProfile(
                id = d.id, name = d.name ?: "", phone = d.phone, email = d.email,
                vehicleType = d.vehicleType, vehicleReg = d.vehicleReg,
                kycStatus = runCatching { KycStatus.valueOf(d.kycStatus ?: "NONE") }.getOrDefault(KycStatus.NONE),
                tripsCompleted = d.tripsCompleted, noShows = d.noShows, photoUrl = d.photoUrl,
                avgRating = d.avgRating, ratingCount = d.ratingCount,
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

    override suspend fun rejectDriver(driverId: Long, reason: String?): AppResult<Unit> =
        api.rejectDriver(driverId, RejectInputDto(reason)).asResult { }

    override suspend fun allDrivers(): AppResult<List<com.itcabs.domain.model.AdminDriver>> =
        api.allDrivers().asResult { list ->
            list.map {
                com.itcabs.domain.model.AdminDriver(
                    it.id, it.name ?: "", it.status,
                    runCatching { KycStatus.valueOf(it.kycStatus ?: "NONE") }.getOrDefault(KycStatus.NONE),
                    it.tripsCompleted, it.noShows,
                )
            }
        }

    override suspend fun blockUser(userId: Long): AppResult<Unit> = api.blockUser(userId).asResult { }
    override suspend fun unblockUser(userId: Long): AppResult<Unit> = api.unblockUser(userId).asResult { }

    override suspend fun adminDriverDocs(driverId: Long): AppResult<List<com.itcabs.domain.model.KycDoc>> =
        api.driverDocuments(driverId).asResult { list ->
            list.map { com.itcabs.domain.model.KycDoc(it.docType, it.storagePath, it.status, it.rejectReason) }
        }

    override suspend fun signedDocUrl(storagePath: String): AppResult<String> = storage.signedUrl(storagePath)

    override suspend fun requestReupload(driverId: Long, docType: String, reason: String?): AppResult<Unit> =
        api.requestReupload(driverId, docType, RejectInputDto(reason)).asResult { }

    override suspend fun pendingCoordinators(): AppResult<List<com.itcabs.domain.model.PendingCoordinator>> =
        api.pendingCoordinators().asResult { list ->
            list.map { com.itcabs.domain.model.PendingCoordinator(it.id, it.name ?: "", it.email) }
        }

    override suspend fun approveCoordinator(userId: Long): AppResult<Unit> =
        api.approveCoordinator(userId).asResult { }

    override suspend fun rejectCoordinator(userId: Long, reason: String?): AppResult<Unit> =
        api.rejectCoordinator(userId, RejectInputDto(reason)).asResult { }
}
