package dev.browsery.app.auth

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val userId: String,
    val email: String,
) {
    fun toJson(): String = JSONObject()
        .put("a", accessToken).put("r", refreshToken).put("e", expiresAtMs)
        .put("u", userId).put("m", email).toString()

    companion object {
        fun fromJson(text: String): AuthSession? = try {
            val o = JSONObject(text)
            AuthSession(o.getString("a"), o.getString("r"), o.getLong("e"), o.optString("u"), o.optString("m"))
        } catch (e: Exception) {
            null
        }
    }
}

/** [fatal] = server menolak kredensial/refresh token (bukan sekadar jaringan). */
class AuthException(message: String, val fatal: Boolean = false) : Exception(message)

/** Klien GoTrue (Supabase Auth) lewat REST. Hanya memakai kunci publishable. */
class AuthClient(
    private val base: String = SupabaseConfig.URL,
    private val key: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private fun call(method: String, path: String, body: JSONObject?, bearer: String?): Pair<Int, String> {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("apikey", key)
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } catch (e: java.io.IOException) {
            throw AuthException("Tidak bisa terhubung ke server akun.")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorOf(code: Int, text: String): AuthException {
        val msg = try {
            val o = JSONObject(text)
            o.optString("error_description").ifBlank { o.optString("msg") }.ifBlank { o.optString("message") }
        } catch (e: Exception) {
            ""
        }
        return AuthException(msg.ifBlank { "Server menolak permintaan (HTTP $code)." }, fatal = code == 400 || code == 401 || code == 403)
    }

    private fun sessionFrom(text: String, now: Long): AuthSession {
        val o = JSONObject(text)
        val user = o.optJSONObject("user")
        return AuthSession(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAtMs = now + o.optLong("expires_in", 3600L) * 1000L,
            userId = user?.optString("id").orEmpty(),
            email = user?.optString("email").orEmpty(),
        )
    }

    fun signIn(email: String, password: String): AuthSession {
        val body = JSONObject().put("email", email).put("password", password)
        val (code, text) = call("POST", "/auth/v1/token?grant_type=password", body, null)
        if (code !in 200..299) throw errorOf(code, text)
        return sessionFrom(text, System.currentTimeMillis())
    }

    /** Null bila server meminta konfirmasi email dulu (belum ada sesi). */
    fun signUp(email: String, password: String): AuthSession? {
        val body = JSONObject().put("email", email).put("password", password)
        val (code, text) = call("POST", "/auth/v1/signup", body, null)
        if (code !in 200..299) throw errorOf(code, text)
        return if (text.contains("\"access_token\"")) sessionFrom(text, System.currentTimeMillis()) else null
    }

    fun refresh(refreshToken: String, old: AuthSession?): AuthSession {
        val body = JSONObject().put("refresh_token", refreshToken)
        val (code, text) = call("POST", "/auth/v1/token?grant_type=refresh_token", body, null)
        if (code !in 200..299) throw errorOf(code, text)
        val s = sessionFrom(text, System.currentTimeMillis())
        return if (s.email.isBlank() && old != null) s.copy(userId = old.userId, email = old.email) else s
    }

    fun fetchUser(accessToken: String): Pair<String, String> {
        val (code, text) = call("GET", "/auth/v1/user", null, accessToken)
        if (code !in 200..299) throw errorOf(code, text)
        val o = JSONObject(text)
        return o.optString("id") to o.optString("email")
    }

    /** Best effort: mencabut sesi di server; kegagalan tidak menghalangi keluar lokal. */
    fun logout(accessToken: String) {
        try {
            call("POST", "/auth/v1/logout", null, accessToken)
        } catch (e: AuthException) {
            // diabaikan
        }
    }
}
