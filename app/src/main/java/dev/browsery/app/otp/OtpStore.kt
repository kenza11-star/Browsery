package dev.browsery.app.otp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Brankas akun authenticator. Semua lokal: tidak ada server, tidak disinkronkan, tidak masuk Supabase, tidak ikut
 * backup (allowBackup=false). Berkas dienkripsi AES-256-GCM dengan kunci di Android Keystore yang tidak dapat diekspor;
 * bila Keystore gagal, kunci TIDAK pernah ditulis sebagai teks biasa. Isi berkas = daftar URI otpauth:// per baris.
 */
class OtpStore(private val context: Context) {
    val entries = mutableStateListOf<OtpEntry>()

    var error by mutableStateOf<String?>(null)
        private set

    private val file = File(context.filesDir, "otp.vault")
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        try {
            if (!file.exists()) return
            val raw = file.readBytes()
            if (raw.size <= IV_LEN) throw IllegalStateException("berkas rusak")
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, IV_LEN)))
            val plain = cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN).toString(Charsets.UTF_8)
            entries.clear()
            plain.lineSequence().filter { it.isNotBlank() }.mapNotNull { OtpUri.parse(it) }.forEach { entries.add(it) }
        } catch (e: Exception) {
            error = "Brankas tidak dapat dibuka di perangkat ini (" + (e.message ?: e.javaClass.simpleName) + "). Akun tidak ditampilkan agar tidak tertimpa."
            blocked = true
        }
    }

    private var blocked = false

    /** Menambah akun. false bila duplikat (kunci, penerbit, akun sama) atau penyimpanan gagal. */
    fun add(e: OtpEntry): Boolean {
        load()
        if (blocked) return false
        if (entries.any { it.secret == e.secret && it.issuer == e.issuer && it.account == e.account }) return false
        entries.add(e)
        return persistOrRevert { entries.remove(e) }
    }

    fun remove(id: String) {
        val old = entries.firstOrNull { it.id == id } ?: return
        val idx = entries.indexOf(old)
        entries.removeAt(idx)
        persistOrRevert { entries.add(idx.coerceAtMost(entries.size), old) }
    }

    fun rename(id: String, issuer: String, account: String) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx < 0) return
        val old = entries[idx]
        entries[idx] = old.copy(issuer = issuer.trim(), account = account.trim())
        persistOrRevert { entries[idx] = old }
    }

    private fun persistOrRevert(revert: () -> Unit): Boolean {
        return try {
            val plain = entries.joinToString("\n") { OtpUri.format(it) }.toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.iv + cipher.doFinal(plain)
            val tmp = File(context.filesDir, "otp.vault.tmp")
            tmp.writeBytes(out)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IllegalStateException("gagal menulis berkas")
            }
            error = null
            true
        } catch (e: Exception) {
            revert()
            error = "Gagal menyimpan dengan aman: " + (e.message ?: e.javaClass.simpleName)
            false
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "browsery_otp_vault"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
