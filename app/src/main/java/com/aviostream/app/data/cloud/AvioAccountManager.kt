package com.aviostream.app.data.cloud

import android.content.Context
import com.aviostream.app.BuildConfig
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Avio account (owner's order 2026-10-25): email + password sign-in on
 * Supabase Auth, with the full app backup (the same payload the Google
 * Drive sync carries — library, progress, addons, settings and API keys)
 * stored in a per-user row guarded by row-level security.
 *
 * Email codes are used for password reset (recover -> verify OTP ->
 * set new password) and for passwordless sign-in (otp -> verify OTP ->
 * session, no password typed on the phone). Google Drive stays
 * available as a second door.
 *
 * The feature hides itself until the backend is configured at build
 * time (AVIO_SUPABASE_URL / AVIO_SUPABASE_ANON_KEY).
 */
class AvioAccountManager(
    context: Context,
    private val repository: StreamRepository
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    /**
     * S3: the session tokens live in the encrypted secret store
     * (SettingsStore's AES-GCM/Keystore file), not in the plaintext
     * plavio_account prefs — the refresh token is long-lived, so it
     * gets the same protection as every other secret. Lazy: the
     * store is only built when a token is actually read or written.
     */
    private val secrets: SettingsStore by lazy { SettingsStore(appContext) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val PREFS = "plavio_account"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_LAST_SYNC = "last_sync"

        fun isConfigured(): Boolean =
            BuildConfig.AVIO_SUPABASE_URL.isNotBlank() &&
                BuildConfig.AVIO_SUPABASE_ANON_KEY.isNotBlank()
    }

    fun isSignedIn(): Boolean = accessToken() != null &&
        prefs.getString(KEY_USER_ID, null) != null

    /** S3: encrypted-store read, with a one-time move of a plaintext token left in plavio_account by older builds. */
    private fun accessToken(): String? = secrets.getSecretValue(KEY_ACCESS) ?: migrateLegacyToken(KEY_ACCESS)

    /** S3: same as [accessToken], for the long-lived refresh token. */
    private fun refreshToken(): String? = secrets.getSecretValue(KEY_REFRESH) ?: migrateLegacyToken(KEY_REFRESH)

    /**
     * One-time move of a plaintext token from plavio_account into
     * the encrypted store. The plaintext entry is deleted only
     * after the encrypted copy is confirmed persisted; the token
     * is returned either way, so a failed move never signs the
     * user out — it just retries on the next read.
     */
    private fun migrateLegacyToken(key: String): String? {
        val legacy = prefs.getString(key, null) ?: return null
        runCatching { secrets.putSecretValue(key, legacy) }
        if (secrets.getSecretValue(key) != null) prefs.edit().remove(key).apply()
        return legacy
    }

    fun accountEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun lastSyncTime(): String? = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }?.let {
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(it))
    }

    // ------------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------------

    /** Returns true when a session was issued immediately (no email confirmation pending). */
    suspend fun signUp(email: String, password: String): Result<Boolean> = authCall {
        val body = JSONObject().put("email", email).put("password", password)
        val resp = post("/auth/v1/signup", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        val json = resp.json ?: throw AuthException("Unexpected response from the account server.")
        if (json.has("access_token")) {
            saveSession(json, email)
            true
        } else {
            false
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = authCall {
        val body = JSONObject().put("email", email).put("password", password)
        val resp = post("/auth/v1/token?grant_type=password", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        val json = resp.json ?: throw AuthException("Unexpected response from the account server.")
        saveSession(json, email)
    }

    /**
     * Passwordless sign-in, step 1: sends a 6-digit sign-in code to the
     * email. This is the Nuvio device-link equivalent on Avio's
     * backend — no password is typed on the phone; the code is read
     * from email on any device. (A true Nuvio-style device link — phone
     * shows a code, another device approves it — needs server-side
     * RPCs + an edge function + a link page on the Supabase project,
     * which Avio's project does not have; the email code is
     * Supabase's native passwordless route and needs no backend work.)
     */
    suspend fun requestSignInCode(email: String): Result<Unit> = authCall {
        val body = JSONObject().put("email", email).put("create_user", false)
        val resp = post("/auth/v1/otp", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
    }

    /** Passwordless sign-in, step 2: verifies the emailed code and signs in. Ends signed in. */
    suspend fun verifySignInCode(email: String, code: String): Result<Unit> = authCall {
        val body = JSONObject().put("email", email).put("token", code).put("type", "email")
        val resp = post("/auth/v1/verify", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        val json = resp.json ?: throw AuthException("Unexpected response from the account server.")
        saveSession(json, email)
    }

    suspend fun signOut() {
        val token = accessToken()
        if (token != null) {
            runCatching { post("/auth/v1/logout", JSONObject(), accessToken = token) }
        }
        prefs.edit().clear().apply()
        secrets.putSecretValue(KEY_ACCESS, null)
        secrets.putSecretValue(KEY_REFRESH, null)
    }

    /** Sends the 6-digit reset code email. */
    suspend fun requestPasswordReset(email: String): Result<Unit> = authCall {
        val body = JSONObject().put("email", email)
        val resp = post("/auth/v1/recover", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
    }

    /** Verifies the reset code and sets the new password. Ends signed out. */
    suspend fun verifyResetAndSetPassword(email: String, code: String, newPassword: String): Result<Unit> = authCall {
        val body = JSONObject().put("email", email).put("token", code).put("type", "recovery")
        val resp = post("/auth/v1/verify", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        val json = resp.json ?: throw AuthException("Unexpected response from the account server.")
        val recoveryToken = json.optString("access_token")
            .ifBlank { throw AuthException("The reset code was not accepted.") }
        val update = JSONObject().put("password", newPassword)
        val resp2 = put("/auth/v1/user", update, accessToken = recoveryToken)
        if (resp2.code !in 200..299) throw AuthException(errorMessage(resp2.json, resp2.code))
        runCatching { post("/auth/v1/logout", JSONObject(), accessToken = recoveryToken) }
    }

    suspend fun changePassword(newPassword: String): Result<Unit> = authCall {
        withFreshToken { token ->
            val body = JSONObject().put("password", newPassword)
            val resp = put("/auth/v1/user", body, accessToken = token)
            if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        }
    }

    // ------------------------------------------------------------------
    // Sync (same payload as the Google Drive backup)
    // ------------------------------------------------------------------

    /** Uploads the current app backup into the account row. */
    suspend fun syncNow(): Result<Unit> = authCall {
        val payload = repository.exportBackupJson().getOrThrow()
        withFreshToken { token ->
            val uid = prefs.getString(KEY_USER_ID, null)
                ?: throw AuthException("Not signed in.")
            val body = JSONObject()
                .put("user_id", uid)
                .put("payload", payload)
                .put("updated_at", isoNow())
            val resp = post(
                "/rest/v1/account_backups", body, accessToken = token,
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
            if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
        }
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    /** True when a cloud backup existed (and was imported). */
    suspend fun restoreFromAccount(): Result<Boolean> = authCall {
        var restored = false
        withFreshToken { token ->
            val uid = prefs.getString(KEY_USER_ID, null)
                ?: throw AuthException("Not signed in.")
            val resp = get(
                "/rest/v1/account_backups?select=payload,updated_at&user_id=eq.$uid",
                accessToken = token
            )
            if (resp.code !in 200..299) throw AuthException(errorMessage(resp.json, resp.code))
            val rows = resp.jsonArray
            val payload = rows?.optJSONObject(0)?.optString("payload")?.takeIf { it.isNotBlank() }
            if (payload != null) {
                repository.importBackupJson(payload).getOrThrow()
                restored = true
            }
        }
        restored
    }

    /**
     * Post-sign-in flow: restore the account backup when one exists;
     * otherwise upload the current device state as the first backup.
     */
    suspend fun syncAfterSignIn(): Result<Boolean> = authCall {
        val restored = restoreFromAccount().getOrThrow()
        if (!restored) syncNow().getOrThrow()
        restored
    }

    // ------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------

    private class AuthException(message: String) : Exception(message)

    private data class Resp(val code: Int, val json: JSONObject?, val jsonArray: org.json.JSONArray?)

    private fun baseRequest(path: String, accessToken: String?): Request.Builder =
        Request.Builder()
            .url(BuildConfig.AVIO_SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.AVIO_SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${accessToken ?: BuildConfig.AVIO_SUPABASE_ANON_KEY}")
            .header("Content-Type", "application/json")

    private fun execute(req: Request): Resp {
        val text = client.newCall(req).execute().use { r ->
            r.code to (r.body?.string().orEmpty())
        }
        val (code, body) = text
        val trimmed = body.trim()
        return Resp(
            code,
            runCatching { JSONObject(trimmed) }.getOrNull(),
            runCatching { org.json.JSONArray(trimmed) }.getOrNull()
        )
    }

    private fun post(path: String, body: JSONObject, accessToken: String?, extraHeaders: Map<String, String> = emptyMap()): Resp {
        val builder = baseRequest(path, accessToken)
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return execute(builder.post(body.toString().toRequestBody("application/json".toMediaType())).build())
    }

    private fun put(path: String, body: JSONObject, accessToken: String?): Resp =
        execute(baseRequest(path, accessToken).put(body.toString().toRequestBody("application/json".toMediaType())).build())

    private fun get(path: String, accessToken: String?): Resp =
        execute(baseRequest(path, accessToken).get().build())

    private fun errorMessage(json: JSONObject?, code: Int): String {
        val raw = json?.optString("msg")?.ifBlank { null }
            ?: json?.optString("message")?.ifBlank { null }
            ?: json?.optString("error_description")?.ifBlank { null }
            ?: json?.optString("error")?.ifBlank { null }
        return when {
            raw == null -> "Account server error ($code). Please try again."
            raw.contains("Invalid login credentials", ignoreCase = true) ->
                "Wrong email or password."
            raw.contains("Email not confirmed", ignoreCase = true) ->
                "Please confirm your email first — check your inbox."
            raw.contains("already registered", ignoreCase = true) ||
                raw.contains("already been registered", ignoreCase = true) ->
                "An account with this email already exists. Try signing in."
            raw.contains("Password should be at least", ignoreCase = true) -> raw
            else -> raw
        }
    }

    private fun saveSession(json: JSONObject, email: String) {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val expiresIn = json.optLong("expires_in", 3600)
        val userId = json.optJSONObject("user")?.optString("id").orEmpty()
        if (access.isBlank() || userId.isBlank()) throw AuthException("Unexpected sign-in response.")
        secrets.putSecretValue(KEY_ACCESS, access)
        secrets.putSecretValue(KEY_REFRESH, refresh)
        prefs.edit()
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + expiresIn * 1000)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, json.optJSONObject("user")?.optString("email")?.ifBlank { null } ?: email)
            .apply()
    }

    /** Runs [block] with a valid access token, refreshing (and retrying once on 401). */
    private suspend fun withFreshToken(block: suspend (String) -> Unit) {
        var token = accessToken()
            ?: throw AuthException("Not signed in.")
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        if (System.currentTimeMillis() > expires - 60_000) {
            refreshSession()
            token = accessToken() ?: throw AuthException("Session expired.")
        }
        try {
            block(token)
        } catch (e: AuthException) {
            if (e.message?.contains("401") == true) {
                refreshSession()
                val fresh = accessToken() ?: throw e
                block(fresh)
            } else throw e
        }
    }

    private suspend fun refreshSession() {
        val refresh = refreshToken()
            ?: throw AuthException("Session expired. Please sign in again.")
        val body = JSONObject().put("refresh_token", refresh)
        val resp = post("/auth/v1/token?grant_type=refresh_token", body, accessToken = null)
        if (resp.code !in 200..299) throw AuthException("Session expired. Please sign in again.")
        val json = resp.json ?: throw AuthException("Session expired. Please sign in again.")
        saveSession(json, prefs.getString(KEY_EMAIL, "").orEmpty())
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())

    private suspend fun <T> authCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: AuthException) {
                Result.failure(Exception(e.message))
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Something went wrong. Please try again."))
            }
        }
}
