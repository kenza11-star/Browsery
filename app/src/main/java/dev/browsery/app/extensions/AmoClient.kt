package dev.browsery.app.extensions

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AmoAddon(
    val slug: String,
    val guid: String,
    val name: String,
    val summary: String,
    val users: Long,
    val fileUrl: String,
)

/**
 * Klien kecil untuk katalog resmi Firefox Add-ons (addons.mozilla.org). Hanya membaca data publik; tidak ada akun,
 * tidak ada data pengguna yang dikirim selain kata kunci pencarian. Berkas ekstensi hanya diterima dari host Mozilla,
 * dan engine Gecko tetap memverifikasi tanda tangan sebelum memasang.
 */
object AmoClient {
    private const val BASE = "https://addons.mozilla.org/api/v5/addons"
    private val allowedFilePrefixes = listOf("https://addons.mozilla.org/", "https://addons.cdn.mozilla.net/")

    fun isAllowedFileUrl(url: String): Boolean = allowedFilePrefixes.any { url.startsWith(it) }

    /** Slug dari tautan halaman addons.mozilla.org (…/addon/<slug>/…), atau null. */
    fun slugFromUrl(input: String): String? {
        val t = input.trim()
        if (!t.startsWith("https://addons.mozilla.org/")) return null
        return Regex("/addon/([^/?#]+)").find(t)?.groupValues?.get(1)
    }

    suspend fun search(query: String): List<AmoAddon> = withContext(Dispatchers.IO) {
        val q = if (query.isBlank()) "" else "&q=" + URLEncoder.encode(query, "UTF-8")
        val url = "$BASE/search/?app=android&type=extension&sort=users&page_size=20&lang=en-US$q"
        parseList(get(url).optJSONArray("results"))
    }

    suspend fun bySlug(slug: String): AmoAddon? = withContext(Dispatchers.IO) {
        parse(get("$BASE/addon/" + URLEncoder.encode(slug, "UTF-8") + "/?lang=en-US"))
    }

    /** Tautan unduhan .xpi untuk masukan pengguna: halaman AMO atau berkas .xpi dari host Mozilla. */
    suspend fun resolveInstallUrl(input: String): String? {
        val t = input.trim()
        if (isAllowedFileUrl(t) && t.contains(".xpi")) return t
        val slug = slugFromUrl(t) ?: return null
        return bySlug(slug)?.fileUrl
    }

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "Browsery")
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            c.disconnect()
        }
    }

    private fun parseList(a: JSONArray?): List<AmoAddon> {
        if (a == null) return emptyList()
        val out = ArrayList<AmoAddon>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            parse(o)?.let { out.add(it) }
        }
        return out
    }

    private fun parse(o: JSONObject): AmoAddon? {
        val cv = o.optJSONObject("current_version")
        val file = cv?.optJSONObject("file") ?: cv?.optJSONArray("files")?.optJSONObject(0)
        val fileUrl = file?.optString("url").orEmpty()
        if (!isAllowedFileUrl(fileUrl)) return null
        val name = text(o.opt("name"))
        if (name.isBlank()) return null
        return AmoAddon(
            slug = o.optString("slug"),
            guid = o.optString("guid"),
            name = name,
            summary = text(o.opt("summary")).replace(Regex("<[^>]+>"), "").trim(),
            users = o.optLong("average_daily_users"),
            fileUrl = fileUrl,
        )
    }

    private fun text(v: Any?): String = when (v) {
        is String -> v
        is JSONObject -> {
            val preferred = v.optString("en-US")
            if (preferred.isNotEmpty()) {
                preferred
            } else {
                val k = v.keys()
                if (k.hasNext()) v.optString(k.next()) else ""
            }
        }
        else -> ""
    }
}
