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

    /** The driver's earnings (legs + company jobs): totals, this week, recent trips. */
    suspend fun earnings(): AppResult<com.itcabs.domain.model.DriverEarnings>

    /** Toggle whether this driver receives new-trip pushes. */
    suspend fun setAvailability(available: Boolean): AppResult<Unit>

    /** Submit a Firebase phone-auth ID token; backend verifies + marks the number verified. */
    suspend fun verifyPhone(idToken: String): AppResult<Unit>

    /** Upload one KYC document (jpeg bytes) to private storage and register it for review. */
    suspend fun uploadKycDoc(docType: String, jpeg: ByteArray): AppResult<Unit>

    /** The driver's uploaded documents + per-doc review state. */
    suspend fun myKycDocs(): AppResult<List<com.itcabs.domain.model.KycDoc>>

    /** A driver's public profile (for the coordinator once the driver is on their job). */
    suspend fun publicProfile(driverId: Long): AppResult<com.itcabs.domain.model.PublicDriverProfile>

    // admin (is_admin only; enforced server-side)
    suspend fun pendingDrivers(): AppResult<List<PendingDriver>>
    suspend fun verifyDriver(driverId: Long): AppResult<Unit>
    suspend fun rejectDriver(driverId: Long, reason: String?): AppResult<Unit>

    /** A driver's uploaded documents (for admin review). */
    suspend fun adminDriverDocs(driverId: Long): AppResult<List<com.itcabs.domain.model.KycDoc>>

    /** A short-lived signed URL to view one private document. */
    suspend fun signedDocUrl(storagePath: String): AppResult<String>

    /** Ask a driver to re-upload one document, with a reason. */
    suspend fun requestReupload(driverId: Long, docType: String, reason: String?): AppResult<Unit>
    suspend fun allDrivers(): AppResult<List<com.itcabs.domain.model.AdminDriver>>
    suspend fun blockUser(userId: Long): AppResult<Unit>
    suspend fun unblockUser(userId: Long): AppResult<Unit>

    // coordinator approval (admin)
    suspend fun pendingCoordinators(): AppResult<List<com.itcabs.domain.model.PendingCoordinator>>
    suspend fun approveCoordinator(userId: Long): AppResult<Unit>
    suspend fun rejectCoordinator(userId: Long, reason: String?): AppResult<Unit>
}
