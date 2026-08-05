package com.itcabs.identity

import com.itcabs.push.PushService
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

/** Admin reject payload: an optional reason shown to the driver so they know what to fix. */
data class RejectInput(val reason: String? = null)

@RestController
@RequestMapping("/api/v1")
class DriverController(private val db: NamedParameterJdbcTemplate, private val push: PushService) {

    /** Driver submits KYC. Status starts PENDING; a human/admin verifies (below). */
    @PostMapping("/driver/kyc")
    fun submitKyc(req: HttpServletRequest, @RequestBody body: KycInput): Map<String, Any> {
        val uid = requireUserId(req)
        // Phone must be verified first (spec: even Google-auth drivers verify a mobile number).
        val phoneVerified = db.queryForObject(
            "SELECT phone_verified FROM users WHERE id = :u", MapSqlParameterSource("u", uid), Boolean::class.java,
        ) ?: false
        if (!phoneVerified) throw com.itcabs.shared.badRequest("verify your phone number before submitting KYC")
        // Block edits while a submission is already awaiting review (no duplicate/racing submissions).
        val pending = db.queryForList(
            "SELECT kyc_status FROM driver_profiles WHERE user_id = :u", MapSqlParameterSource("u", uid),
        ).firstOrNull()?.get("kyc_status")
        if (pending == "PENDING") throw com.itcabs.shared.badRequest("your KYC is already under review")
        // All required documents must be uploaded before review.
        val have = db.queryForList(
            "SELECT doc_type FROM kyc_documents WHERE user_id = :u", MapSqlParameterSource("u", uid),
        ).map { it["doc_type"] as String }.toSet()
        val missing = REQUIRED_KYC_DOCS.filter { it !in have }
        if (missing.isNotEmpty()) throw com.itcabs.shared.badRequest(
            "upload all documents first (missing: ${missing.joinToString(", ")})",
        )
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

    /** The authenticated driver's own KYC status + vehicle + reliability + rating. kycStatus NONE if no profile. */
    @GetMapping("/driver/me")
    fun myProfile(req: HttpServletRequest): Map<String, Any?> {
        val uid = requireUserId(req)
        val row = db.queryForList(
            "SELECT vehicle_type, vehicle_reg, kyc_status, trips_completed, no_shows, rejection_reason, available FROM driver_profiles WHERE user_id = :u",
            MapSqlParameterSource("u", uid),
        ).firstOrNull()
        val user = db.queryForList("SELECT phone, phone_verified FROM users WHERE id = :u", MapSqlParameterSource("u", uid)).first()
        val rating = db.queryForList(
            "SELECT avg(stars)::float AS avg, count(*) AS n FROM ratings WHERE ratee_id = :u",
            MapSqlParameterSource("u", uid),
        ).first()
        return mapOf(
            "phone" to user["phone"],
            "phoneVerified" to (user["phone_verified"] ?: false),
            "kycStatus" to (row?.get("kyc_status") ?: "NONE"),
            "vehicleType" to row?.get("vehicle_type"),
            "vehicleReg" to row?.get("vehicle_reg"),
            "tripsCompleted" to (row?.get("trips_completed") ?: 0),
            "noShows" to (row?.get("no_shows") ?: 0),
            "rejectionReason" to row?.get("rejection_reason"),
            "available" to (row?.get("available") ?: true),
            "avgRating" to rating["avg"],           // null until first rating
            "ratingCount" to (rating["n"] as Number).toInt(),
        )
    }

    /**
     * The driver's earnings across BOTH single legs and company jobs: total settled, still-owed,
     * this-week, trip count, and the last 20 completed trips. Money everywhere is paise (never float).
     */
    @GetMapping("/driver/earnings")
    fun earnings(req: HttpServletRequest): Map<String, Any?> {
        val uid = requireUserId(req)
        val p = MapSqlParameterSource("u", uid)
        val agg = db.queryForList(
            """WITH t AS (
                   SELECT fare_paise, paid_at FROM legs WHERE claimed_by=:u AND status='COMPLETED'
                   UNION ALL
                   SELECT fare_paise, paid_at FROM company_jobs WHERE claimed_by=:u AND status='COMPLETED'
               )
               SELECT coalesce(sum(fare_paise) FILTER (WHERE paid_at IS NOT NULL),0)                       AS earned,
                      coalesce(sum(fare_paise) FILTER (WHERE paid_at IS NULL),0)                           AS pending,
                      count(*)                                                                             AS trips,
                      coalesce(sum(fare_paise) FILTER (WHERE paid_at >= now() - interval '7 days'),0)      AS week
                 FROM t""",
            p,
        ).first()
        val recent = db.queryForList(
            """SELECT label, kind, fare_paise, (paid_at IS NOT NULL) AS paid, coalesce(paid_at, created_at) AS ts
                 FROM (
                   SELECT pickup AS label, 'LEG' AS kind, fare_paise, paid_at, created_at
                     FROM legs WHERE claimed_by=:u AND status='COMPLETED'
                   UNION ALL
                   SELECT company_name AS label, 'COMPANY' AS kind, fare_paise, paid_at, created_at
                     FROM company_jobs WHERE claimed_by=:u AND status='COMPLETED'
                 ) x ORDER BY ts DESC LIMIT 20""",
            p,
        ).map {
            mapOf(
                "label" to (it["label"] ?: ""),
                "kind" to it["kind"],
                "amountPaise" to (it["fare_paise"] as Number).toLong(),
                "paid" to (it["paid"] as Boolean),
                "date" to it["ts"].toString(),
            )
        }
        return mapOf(
            "totalEarnedPaise" to (agg["earned"] as Number).toLong(),
            "pendingPaise" to (agg["pending"] as Number).toLong(),
            "tripsCompleted" to (agg["trips"] as Number).toInt(),
            "thisWeekPaise" to (agg["week"] as Number).toLong(),
            "recent" to recent,
        )
    }

    /** A driver's public profile — shown to a coordinator once a driver takes/gets their job. */
    @GetMapping("/drivers/{id}/profile")
    fun publicProfile(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any?> {
        requireUserId(req) // any authenticated user
        val u = db.queryForList("SELECT name, phone, email FROM users WHERE id = :id", MapSqlParameterSource("id", id))
            .firstOrNull() ?: throw com.itcabs.shared.badRequest("no such driver")
        val p = db.queryForList(
            "SELECT vehicle_type, vehicle_reg, kyc_status, trips_completed, no_shows, photo_url FROM driver_profiles WHERE user_id = :id",
            MapSqlParameterSource("id", id),
        ).firstOrNull()
        val rating = db.queryForList(
            "SELECT avg(stars)::float AS avg, count(*) AS n FROM ratings WHERE ratee_id = :id",
            MapSqlParameterSource("id", id),
        ).first()
        return mapOf(
            "id" to id,
            "name" to u["name"],
            "phone" to u["phone"],
            "email" to u["email"],
            "vehicleType" to p?.get("vehicle_type"),
            "vehicleReg" to p?.get("vehicle_reg"),
            "kycStatus" to (p?.get("kyc_status") ?: "NONE"),
            "tripsCompleted" to (p?.get("trips_completed") ?: 0),
            "noShows" to (p?.get("no_shows") ?: 0),
            "photoUrl" to p?.get("photo_url"),
            "avgRating" to rating["avg"],
            "ratingCount" to (rating["n"] as Number).toInt(),
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

    /** Admin: all drivers with status — the roster for block/unblock in the Admin tab. */
    @GetMapping("/admin/drivers")
    fun allDrivers(req: HttpServletRequest): List<Map<String, Any?>> {
        requireAdmin(req, db)
        return db.queryForList(
            """SELECT u.id, u.name, u.status, p.kyc_status, p.trips_completed, p.no_shows
                 FROM users u JOIN driver_profiles p ON p.user_id = u.id
                WHERE u.role='DRIVER' ORDER BY u.status, u.name""",
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

    /** Admin: reject a KYC submission (bad/unclear docs), record the reason, and notify the driver. */
    @PostMapping("/admin/drivers/{id}/reject")
    fun reject(req: HttpServletRequest, @PathVariable id: Long, @RequestBody(required = false) body: RejectInput?): Map<String, Any> {
        requireAdmin(req, db)
        val reason = body?.reason?.takeIf { it.isNotBlank() }
        db.update(
            "UPDATE driver_profiles SET kyc_status='REJECTED', rejection_reason=:r WHERE user_id=:id",
            MapSqlParameterSource().addValue("r", reason).addValue("id", id),
        )
        push.notifyUser(
            id, "KYC not approved",
            reason?.let { "Please fix and resubmit: $it" } ?: "Please review your documents and resubmit.",
        )
        return mapOf("kycStatus" to "REJECTED")
    }

    /** Admin: a driver's uploaded documents (paths + review state) so the console can sign + view them. */
    @GetMapping("/admin/drivers/{id}/documents")
    fun driverDocuments(req: HttpServletRequest, @PathVariable id: Long): List<Map<String, Any?>> {
        requireAdmin(req, db)
        return db.queryForList(
            "SELECT doc_type, storage_path, status, reject_reason FROM kyc_documents WHERE user_id = :id ORDER BY doc_type",
            MapSqlParameterSource("id", id),
        )
    }

    /** Admin: ask the driver to re-upload ONE document (keeps the rest of the KYC in review). */
    @PostMapping("/admin/drivers/{id}/documents/{docType}/reupload")
    fun requestReupload(
        req: HttpServletRequest, @PathVariable id: Long, @PathVariable docType: String,
        @RequestBody(required = false) body: RejectInput?,
    ): Map<String, Any> {
        requireAdmin(req, db)
        val type = docType.uppercase()
        val reason = body?.reason?.takeIf { it.isNotBlank() }
        val n = db.update(
            "UPDATE kyc_documents SET status='REUPLOAD_REQUESTED', reject_reason=:r WHERE user_id=:id AND doc_type=:t",
            MapSqlParameterSource().addValue("r", reason).addValue("id", id).addValue("t", type),
        )
        if (n == 0) throw com.itcabs.shared.badRequest("no such document for this driver")
        push.notifyUser(
            id, "Re-upload needed",
            reason?.let { "$type: $it" } ?: "Please re-upload your $type document.",
        )
        return mapOf("status" to "REUPLOAD_REQUESTED")
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

    /** Admin: coordinators awaiting approval — the review queue for the Admin tab. */
    @GetMapping("/admin/coordinators/pending")
    fun pendingCoordinators(req: HttpServletRequest): List<Map<String, Any?>> {
        requireAdmin(req, db)
        return db.queryForList(
            """SELECT id, name, email FROM users
                WHERE role='COORDINATOR' AND coordinator_status='PENDING' AND status<>'BLOCKED'
                ORDER BY id""",
            MapSqlParameterSource(),
        )
    }

    /** Admin: approve a coordinator so they can start posting trips. */
    @PostMapping("/admin/coordinators/{id}/approve")
    fun approveCoordinator(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        requireAdmin(req, db)
        db.update(
            "UPDATE users SET coordinator_status='APPROVED' WHERE id=:id AND role='COORDINATOR'",
            MapSqlParameterSource("id", id),
        )
        push.notifyUser(id, "You're approved", "Your coordinator account is active — you can post trips now.")
        return mapOf("coordinatorStatus" to "APPROVED")
    }

    /** Admin: reject a coordinator request, with an optional reason. */
    @PostMapping("/admin/coordinators/{id}/reject")
    fun rejectCoordinator(req: HttpServletRequest, @PathVariable id: Long, @RequestBody(required = false) body: RejectInput?): Map<String, Any> {
        requireAdmin(req, db)
        db.update(
            "UPDATE users SET coordinator_status='REJECTED' WHERE id=:id AND role='COORDINATOR'",
            MapSqlParameterSource("id", id),
        )
        push.notifyUser(id, "Account not approved", body?.reason?.takeIf { it.isNotBlank() } ?: "Your coordinator request was not approved.")
        return mapOf("coordinatorStatus" to "REJECTED")
    }
}
