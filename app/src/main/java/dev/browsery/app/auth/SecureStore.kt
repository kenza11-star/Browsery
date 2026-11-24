package dev.browsery.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Menyimpan satu string rahasia (sesi) terenkripsi AES-GCM dengan kunci Android Keystore (tidak dapat diekspor). */
class SecureStore(context: Context, private val alias: String = "browsery_auth_key") {
    private val prefs = context.getSharedPreferences("auth_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun put(value: String): Boolean = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val blob = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString("v", Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        true
    } catch (e: Exception) {
        false
    }

    fun get(): String? = try {
        val blob = Base64.decode(prefs.getString("v", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        String(cipher.doFinal(blob, 12, blob.size - 12))
    } catch (e: Exception) {
        null
    }

    fun clear() {
        prefs.edit().remove("v").apply()
    }
}
