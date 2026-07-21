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
}
