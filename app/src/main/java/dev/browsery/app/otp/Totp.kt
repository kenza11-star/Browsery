package dev.browsery.app.otp

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class OtpAlgorithm(val id: String, val mac: String) {
    SHA1("SHA1", "HmacSHA1"),
    SHA256("SHA256", "HmacSHA256"),
    SHA512("SHA512", "HmacSHA512"),
    ;

    companion object {
        fun fromId(id: String?): OtpAlgorithm? = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}

/** Satu akun authenticator. [secret] adalah kunci Base32 yang sudah dinormalisasi (huruf besar, tanpa spasi). */
data class OtpEntry(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String,
    val account: String,
    val secret: String,
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
) {
    val title: String get() = issuer.ifBlank { account }
}

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Menormalkan kunci: huruf besar, tanpa spasi, strip, atau '='. */
    fun normalize(input: String): String = input.uppercase().filter { it != ' ' && it != '-' && it != '=' && !it.isWhitespace() }

    /** Decode RFC 4648; null bila ada karakter di luar alfabet atau kosong. */
    fun decode(input: String): ByteArray? {
        val s = normalize(input)
        if (s.isEmpty()) return null
        var buffer = 0
        var bits = 0
        val out = ByteArrayOutputStream()
        for (c in s) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }
}

object Totp {
    /** Kode TOTP (RFC 6238) untuk [timeSeconds] (detik epoch). [digits] 6..8. */
    fun code(secret: ByteArray, timeSeconds: Long, digits: Int, period: Int, algorithm: OtpAlgorithm): String {
        val counter = timeSeconds / period
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance(algorithm.mac)
        mac.init(SecretKeySpec(secret, algorithm.mac))
        val h = mac.doFinal(msg)
        val off = h[h.size - 1].toInt() and 0xF
        val bin = ((h[off].toInt() and 0x7F) shl 24) or
            ((h[off + 1].toInt() and 0xFF) shl 16) or
            ((h[off + 2].toInt() and 0xFF) shl 8) or
            (h[off + 3].toInt() and 0xFF)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (bin % mod).toString().padStart(digits, '0')
    }

    fun codeFor(e: OtpEntry, timeSeconds: Long): String? {
        val key = Base32.decode(e.secret) ?: return null
        return try {
            code(key, timeSeconds, e.digits, e.period, e.algorithm)
        } catch (ex: Exception) {
            null
        }
    }

    fun secondsRemaining(timeSeconds: Long, period: Int): Int = period - (timeSeconds % period).toInt()
}

/** Format otpauth://totp/Penerbit:Akun?secret=...&issuer=...&digits=...&period=...&algorithm=... (standar Google Authenticator). */
object OtpUri {
    private const val PREFIX = "otpauth://totp/"

    private fun dec(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Ukuran minimum kunci 10 byte (80 bit); lebih pendek ditolak. */
    fun validSecret(secret: String): Boolean {
        val b = Base32.decode(secret) ?: return false
        return b.size >= 10
    }

    fun parse(input: String): OtpEntry? {
        val t = input.trim()
        if (!t.startsWith(PREFIX, ignoreCase = true)) return null
        val rest = t.substring(PREFIX.length)
        val q = rest.indexOf('?')
        val labelRaw = if (q >= 0) rest.substring(0, q) else rest
        val query = if (q >= 0) rest.substring(q + 1) else ""
        val params = HashMap<String, String>()
        for (part in query.split('&')) {
            val i = part.indexOf('=')
            if (i > 0) params[dec(part.substring(0, i)).lowercase()] = dec(part.substring(i + 1))
        }
        val label = dec(labelRaw)
        val labelIssuer = if (label.contains(':')) label.substringBefore(':').trim() else ""
        val account = (if (label.contains(':')) label.substringAfter(':') else label).trim()
        val issuer = params["issuer"]?.trim().orEmpty().ifEmpty { labelIssuer }
        val secret = Base32.normalize(params["secret"] ?: return null)
        if (!validSecret(secret)) return null
        val digits = params["digits"]?.toIntOrNull() ?: 6
        if (digits !in 6..8) return null
        val period = params["period"]?.toIntOrNull() ?: 30
        if (period !in 10..120) return null
        val alg = if (params.containsKey("algorithm")) OtpAlgorithm.fromId(params["algorithm"]) ?: return null else OtpAlgorithm.SHA1
        val id = params["bid"]?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return OtpEntry(id, issuer, account, secret, digits, period, alg)
    }

    fun format(e: OtpEntry): String {
        val label = if (e.issuer.isBlank()) enc(e.account) else enc(e.issuer) + ":" + enc(e.account)
        val sb = StringBuilder(PREFIX).append(label)
        sb.append("?secret=").append(e.secret)
        if (e.issuer.isNotBlank()) sb.append("&issuer=").append(enc(e.issuer))
        sb.append("&digits=").append(e.digits)
        sb.append("&period=").append(e.period)
        sb.append("&algorithm=").append(e.algorithm.id)
        sb.append("&bid=").append(enc(e.id))
        return sb.toString()
    }
}
