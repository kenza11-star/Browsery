package dev.browsery.app.auth

import java.net.URLDecoder
import java.net.URLEncoder

/** Fungsi murni (bisa diuji unit) untuk tautan OAuth dan validasi input. */
object AuthLinks {
    data class Callback(
        val accessToken: String?,
        val refreshToken: String?,
        val expiresIn: Long,
        val error: String?,
    )

    fun authorizeUrl(baseUrl: String, provider: String, redirect: String): String =
        baseUrl.trimEnd('/') + "/auth/v1/authorize?provider=" + enc(provider) + "&redirect_to=" + enc(redirect)

    fun isCallback(uri: String, redirect: String): Boolean = uri.startsWith(redirect)

    /** Token dikirim Supabase pada fragmen (#access_token=...); galat bisa pada fragmen atau query. */
    fun parseCallback(uri: String): Callback? {
        val fragment = uri.substringAfter('#', "")
        val query = uri.substringBefore('#').substringAfter('?', "")
        val params = HashMap<String, String>()
        for (part in listOf(fragment, query)) {
            part.split('&').filter { it.isNotEmpty() }.forEach { kv ->
                val k = dec(kv.substringBefore('='))
                val v = dec(kv.substringAfter('=', ""))
                if (!params.containsKey(k)) params[k] = v
            }
        }
        val error = params["error_description"] ?: params["error"]
        val access = params["access_token"]
        val refresh = params["refresh_token"]
        if (error == null && (access.isNullOrBlank() || refresh.isNullOrBlank())) return null
        return Callback(access, refresh, params["expires_in"]?.toLongOrNull() ?: 3600L, error)
    }

    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email.trim())

    fun isPasswordOk(password: String): Boolean = password.length >= 8

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }
}
