package com.itcabs.identity

import com.itcabs.shared.badRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*

/** The document types every corporate driver must upload before KYC review. */
val REQUIRED_KYC_DOCS = listOf(
    "DL_FRONT", "DL_BACK", "AADHAAR_FRONT", "AADHAAR_BACK",
    "RC_FRONT", "RC_BACK", "PERMIT", "INSURANCE", "FITNESS",
)

data class DocRegisterInput(val docType: String, val storagePath: String)

/**
 * KYC document registry. Files upload directly to the private Supabase bucket (RLS-scoped to the
 * driver's folder); the driver's app registers each uploaded object here so review + validation can
 * track which documents exist. Admin viewing/rejection lives in [DriverController] (admin endpoints).
 */
@RestController
@RequestMapping("/api/v1/driver/kyc")
class KycDocumentController(private val db: NamedParameterJdbcTemplate) {

    /** Register (or replace) one uploaded document. Clears any prior re-upload request. */
    @PostMapping("/documents")
    fun register(req: HttpServletRequest, @RequestBody body: DocRegisterInput): Map<String, Any> {
        val uid = requireUserId(req)
        val type = body.docType.uppercase()
        if (type !in REQUIRED_KYC_DOCS) throw badRequest("unknown document type")
        if (body.storagePath.isBlank()) throw badRequest("storagePath required")
        db.update(
            """INSERT INTO kyc_documents(user_id, doc_type, storage_path, status, reject_reason, uploaded_at)
               VALUES (:u,:t,:p,'UPLOADED',NULL, now())
               ON CONFLICT (user_id, doc_type) DO UPDATE SET
                 storage_path=:p, status='UPLOADED', reject_reason=NULL, uploaded_at=now()""",
            MapSqlParameterSource().addValue("u", uid).addValue("t", type).addValue("p", body.storagePath),
        )
        return mapOf("ok" to true)
    }

    /** The driver's own documents + review state (path returned so they can preview their own uploads). */
    @GetMapping("/documents")
    fun mine(req: HttpServletRequest): List<Map<String, Any?>> {
        val uid = requireUserId(req)
        return db.queryForList(
            "SELECT doc_type, storage_path, status, reject_reason FROM kyc_documents WHERE user_id = :u",
            MapSqlParameterSource("u", uid),
        )
    }
}
