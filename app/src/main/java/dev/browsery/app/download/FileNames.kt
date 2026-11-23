package dev.browsery.app.download

import java.net.URI
import java.net.URLDecoder

/** Pengolahan nama file murni (tanpa Android) agar mudah diuji. */
object FileNames {
    private const val MAX_LEN = 120
    private val badChars = Regex("[<>:\"|?*\\u0000-\\u001F]")
    private val extStar = Regex("filename\\*\\s*=\\s*([^';]*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE)
    private val quoted = Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val plain = Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE)

    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        extStar.find(header)?.let { m ->
            val charset = m.groupValues[1].ifBlank { "UTF-8" }
            return try {
                URLDecoder.decode(m.groupValues[2].trim().replace("+", "%2B"), charset)
            } catch (e: Exception) {
                null
            }
        }
        quoted.find(header)?.let { return it.groupValues[1] }
        plain.find(header)?.let { return it.groupValues[1].trim() }
        return null
    }

    fun fromUrl(url: String): String? = try {
        val path = URI(url).path ?: ""
        path.substringAfterLast('/').takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /** Menghasilkan nama file aman: tanpa path, karakter kontrol/terlarang, titik di awal, dan dibatasi panjangnya. */
    fun sanitize(raw: String?, fallback: String = "download"): String {
        var name = (raw ?: "").substringAfterLast('/').substringAfterLast('\\')
        name = badChars.replace(name, "_").trim().trim('.').trim()
        if (name.isEmpty()) return fallback
        if (name.length > MAX_LEN) {
            val ext = name.substringAfterLast('.', "").take(10)
            name = if (ext.isNotEmpty() && ext.length < name.length) {
                name.take(MAX_LEN - ext.length - 1) + "." + ext
            } else {
                name.take(MAX_LEN)
            }
        }
        return name
    }

    /** Bila [name] sudah ada, tambahkan " (1)", " (2)", ... sebelum ekstensi. */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }
}
