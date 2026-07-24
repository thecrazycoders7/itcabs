package com.itcabs.data

import android.util.Base64
import com.itcabs.core.network.TokenSession
import com.itcabs.domain.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Direct uploads to the private Supabase Storage bucket `kyc-docs`.
 *
 * RLS scopes each driver to a folder named after their Supabase user id, so we read that id from the
 * `sub` claim of the access token and upload to `<uid>/<name>`. The bucket is private — objects are
 * only readable via a signed URL (admin app) or the owner's own token, so documents never sit at a
 * public URL. Duplicate submissions overwrite the same key (x-upsert), keeping one copy per doc type.
 */
class SupabaseStorage(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val tokens: TokenSession,
    private val client: OkHttpClient = OkHttpClient(),
) {
    /** Uploads [jpeg] to kyc-docs/<uid>/<name>; on success returns the object path for registration. */
    suspend fun upload(name: String, jpeg: ByteArray): AppResult<String> {
        val access = tokens.accessToken() ?: return AppResult.Err(0, "Sign in again to upload")
        val uid = subOf(access) ?: return AppResult.Err(0, "Sign in again to upload")
        val path = "$uid/$name"
        val req = Request.Builder()
            .url("${supabaseUrl.trimEnd('/')}/storage/v1/object/$BUCKET/$path")
            .addHeader("Authorization", "Bearer $access")
            .addHeader("apikey", anonKey)
            .addHeader("x-upsert", "true")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) AppResult.Ok(path)
                    else AppResult.Err(resp.code, uploadError(resp.code, resp.body?.string()))
                }
            }.getOrElse { AppResult.Err(0, it.message ?: "Upload failed") }
        }
    }

    /** A short-lived signed URL for a private object (admin review). RLS must permit the caller to read it. */
    suspend fun signedUrl(path: String, expiresInSec: Int = 300): AppResult<String> {
        val access = tokens.accessToken() ?: return AppResult.Err(0, "Sign in again")
        val body = """{"expiresIn":$expiresInSec}""".toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("${supabaseUrl.trimEnd('/')}/storage/v1/object/sign/$BUCKET/$path")
            .addHeader("Authorization", "Bearer $access")
            .addHeader("apikey", anonKey)
            .post(body)
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string()
                    if (!resp.isSuccessful) return@use AppResult.Err(resp.code, "Couldn't open document (${resp.code})")
                    // { "signedURL": "/object/sign/kyc-docs/...?token=..." } — make it absolute.
                    val rel = Regex("\"signedURL\"\\s*:\\s*\"([^\"]+)\"").find(text ?: "")?.groupValues?.get(1)
                        ?: return@use AppResult.Err(0, "Malformed sign response")
                    AppResult.Ok("${supabaseUrl.trimEnd('/')}/storage/v1${rel.replace("\\/", "/")}")
                }
            }.getOrElse { AppResult.Err(0, it.message ?: "Couldn't open document") }
        }
    }

    /** JWT `sub` = Supabase user id, which the folder must be named after (RLS). */
    private fun subOf(jwt: String): String? = runCatching {
        val payload = jwt.split(".").getOrNull(1) ?: return null
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    }.getOrNull()

    private fun uploadError(code: Int, body: String?): String = when {
        code == 400 && body?.contains("Bucket not found", true) == true ->
            "Document storage isn't set up yet. Ask support to enable it."
        code == 403 -> "Not allowed to upload — sign in again."
        else -> "Upload failed (${code})"
    }

    private companion object { const val BUCKET = "kyc-docs" }
}
