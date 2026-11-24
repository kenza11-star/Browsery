package dev.browsery.app.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AuthState {
    data object SignedOut : AuthState
    data object Busy : AuthState
    data class SignedIn(val email: String, val userId: String) : AuthState
}

/** Status akun untuk UI. Sesi disimpan terenkripsi; kata sandi tidak pernah disimpan. */
class AuthManager(context: Context) {
    private val store = SecureStore(context.applicationContext)
    private val client = AuthClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: AuthSession? = null

    var state: AuthState by mutableStateOf(AuthState.SignedOut)
        private set
    var message: String? by mutableStateOf(null)
        private set

    init {
        val saved = store.get()?.let { AuthSession.fromJson(it) }
        if (saved != null) accept(saved, persist = false)
    }

    fun clearMessage() {
        message = null
    }

    private fun accept(s: AuthSession, persist: Boolean = true) {
        session = s
        if (persist) store.put(s.toJson())
        state = AuthState.SignedIn(s.email, s.userId)
        message = null
    }

    private fun local() {
        session = null
        store.clear()
        state = AuthState.SignedOut
    }

    fun signIn(email: String, password: String) = run(email, password) { client.signIn(email.trim(), password) }

    fun signUp(email: String, password: String) = run(email, password) {
        client.signUp(email.trim(), password)
            ?: throw AuthException("Akun dibuat. Cek email untuk konfirmasi, lalu masuk.")
    }

    private fun run(email: String, password: String, block: () -> AuthSession) {
        if (!AuthLinks.isValidEmail(email)) {
            message = "Format email tidak valid."
            return
        }
        if (!AuthLinks.isPasswordOk(password)) {
            message = "Kata sandi minimal 8 karakter."
            return
        }
        val before = state
        state = AuthState.Busy
        scope.launch {
            try {
                val s = withContext(Dispatchers.IO) { block() }
                accept(s)
            } catch (e: AuthException) {
                state = before
                message = e.message
            }
        }
    }

    /** Dipanggil saat browsery://auth-callback dibuka (login Google lewat browser eksternal). */
    fun handleCallback(uri: String) {
        val cb = AuthLinks.parseCallback(uri)
        if (cb == null) {
            message = "Tautan login tidak berisi sesi yang valid."
            return
        }
        if (cb.error != null) {
            message = cb.error
            return
        }
        val before = state
        state = AuthState.Busy
        scope.launch {
            try {
                val access = cb.accessToken.orEmpty()
                val (id, email) = withContext(Dispatchers.IO) { client.fetchUser(access) }
                accept(
                    AuthSession(
                        access,
                        cb.refreshToken.orEmpty(),
                        System.currentTimeMillis() + cb.expiresIn * 1000L,
                        id,
                        email,
                    ),
                )
            } catch (e: AuthException) {
                state = before
                message = e.message
            }
        }
    }

    fun signOut() {
        val token = session?.accessToken
        local()
        if (token != null) scope.launch(Dispatchers.IO) { client.logout(token) }
    }

    /** Token akses yang masih berlaku (menyegarkan bila hampir kedaluwarsa); null bila tidak masuk. Dipakai Stage 10. */
    suspend fun accessToken(): String? {
        val s = session ?: return null
        if (s.expiresAtMs - 60_000L > System.currentTimeMillis()) return s.accessToken
        return try {
            val n = withContext(Dispatchers.IO) { client.refresh(s.refreshToken, s) }
            accept(n)
            n.accessToken
        } catch (e: AuthException) {
            if (e.fatal) {
                local()
                message = "Sesi berakhir, silakan masuk lagi."
            }
            null
        }
    }
}
