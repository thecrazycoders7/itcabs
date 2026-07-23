package com.itcabs.identity

import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*

/** KYC input. Note: NO raw Aadhaar — only a provider token + masked display (ADR-0006). */
data class KycInput(
    val vehicleType: String,
    val vehicleReg: String,
    val aadhaarRef: String,
    val aadhaarMasked: String,
    val rcNumberMasked: String,
    val photoUrl: String,
)

@RestController
@RequestMapping("/api/v1")
class DriverController(private val db: NamedParameterJdbcTemplate) {

    /** Driver submits KYC. Status starts PENDING; a human/admin verifies (below). */
    @PostMapping("/driver/kyc")
    fun submitKyc(req: HttpServletRequest, @RequestBody body: KycInput): Map<String, Any> {
        val uid = requireUserId(req)
        db.update(
            """INSERT INTO driver_profiles(user_id, vehicle_type, vehicle_reg, aadhaar_ref,
                                           aadhaar_masked, rc_number_masked, photo_url, kyc_status)
               VALUES (:u,:vt,:vr,:ar,:am,:rc,:ph,'PENDING')
               ON CONFLICT (user_id) DO UPDATE SET
                 vehicle_type=:vt, vehicle_reg=:vr, aadhaar_ref=:ar, aadhaar_masked=:am,
                 rc_number_masked=:rc, photo_url=:ph, kyc_status='PENDING'""",
            MapSqlParameterSource().addValue("u", uid).addValue("vt", body.vehicleType)
                .addValue("vr", body.vehicleReg).addValue("ar", body.aadhaarRef)
                .addValue("am", body.aadhaarMasked).addValue("rc", body.rcNumberMasked)
                .addValue("ph", body.photoUrl),
        )
        return mapOf("kycStatus" to "PENDING")
    }

    /** The authenticated driver's own KYC status + vehicle. kycStatus is NONE if no profile yet. */
    @GetMapping("/driver/me")
    fun myProfile(req: HttpServletRequest): Map<String, Any?> {
        val uid = requireUserId(req)
        val row = db.queryForList(
            "SELECT vehicle_type, vehicle_reg, kyc_status, trips_completed, no_shows FROM driver_profiles WHERE user_id = :u",
            MapSqlParameterSource("u", uid),
        ).firstOrNull()
        return mapOf(
            "kycStatus" to (row?.get("kyc_status") ?: "NONE"),
            "vehicleType" to row?.get("vehicle_type"),
            "vehicleReg" to row?.get("vehicle_reg"),
            "tripsCompleted" to (row?.get("trips_completed") ?: 0),
            "noShows" to (row?.get("no_shows") ?: 0),
        )
    }

    /** Admin: drivers waiting on KYC approval — the review queue for the in-app Admin tab. */
    @GetMapping("/admin/drivers/pending")
    fun pendingDrivers(req: HttpServletRequest): List<Map<String, Any?>> {
        requireAdmin(req, db)
        return db.queryForList(
            """SELECT u.id, u.name, u.email, p.vehicle_type, p.vehicle_reg,
                      p.aadhaar_masked, p.rc_number_masked
                 FROM driver_profiles p JOIN users u ON u.id = p.user_id
                WHERE p.kyc_status = 'PENDING'
                ORDER BY u.id""",
            MapSqlParameterSource(),
        )
    }

    // Admin-only (is_admin flag). Stands in for the admin-console verification action.
    @PostMapping("/admin/drivers/{id}/verify")
    fun verify(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        val admin = requireAdmin(req, db)
        db.update(
            "UPDATE driver_profiles SET kyc_status='VERIFIED', verified_at=now(), verified_by=:a WHERE user_id=:id",
            MapSqlParameterSource().addValue("a", admin).addValue("id", id),
        )
        return mapOf("kycStatus" to "VERIFIED")
    }

    /** Admin: reject a KYC submission (bad/unclear docs). Driver can resubmit — the app shows Complete KYC again. */
    @PostMapping("/admin/drivers/{id}/reject")
    fun reject(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        requireAdmin(req, db)
        db.update(
            "UPDATE driver_profiles SET kyc_status='REJECTED' WHERE user_id=:id",
            MapSqlParameterSource("id", id),
        )
        return mapOf("kycStatus" to "REJECTED")
    }

    /** Admin: block a user (trust & safety, M4). Blocked users can't sign in or re-register. */
    @PostMapping("/admin/users/{id}/block")
    fun block(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> = setBlocked(req, id, true)

    @PostMapping("/admin/users/{id}/unblock")
    fun unblock(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> = setBlocked(req, id, false)

    private fun setBlocked(req: HttpServletRequest, id: Long, blocked: Boolean): Map<String, Any> {
        requireAdmin(req, db)
        val status = if (blocked) "BLOCKED" else "ACTIVE"
        db.update(
            "UPDATE users SET status=:s WHERE id=:id",
            MapSqlParameterSource().addValue("s", status).addValue("id", id),
        )
        if (blocked) {
            // Revoke refresh sessions so a blocked user can't renew; access token still expires in ≤15m.
            db.update(
                "UPDATE device_sessions SET revoked_at=now() WHERE user_id=:id AND revoked_at IS NULL",
                MapSqlParameterSource("id", id),
            )
            // Ban this user's devices so they can't re-register under a new number (ban evasion).
            db.update(
                """INSERT INTO blocked_devices(device_id)
                   SELECT DISTINCT device FROM device_sessions WHERE user_id=:id AND device IS NOT NULL
                   ON CONFLICT (device_id) DO NOTHING""",
                MapSqlParameterSource("id", id),
            )
        } else {
            // Unblock: free this user's devices. ponytail: a device shared with another still-blocked
            // user gets freed too — acceptable until device→user tracking needs to be exact.
            db.update(
                "DELETE FROM blocked_devices WHERE device_id IN (SELECT device FROM device_sessions WHERE user_id=:id)",
                MapSqlParameterSource("id", id),
            )
        }
        return mapOf("status" to status)
    }
}
