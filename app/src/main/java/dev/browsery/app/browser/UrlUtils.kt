package dev.browsery.app.browser

import java.net.URLEncoder

/** Hasil interpretasi teks yang diketik di address bar. */
sealed interface ResolvedInput {
    data object Empty : ResolvedInput
    data class Navigate(val url: String) : ResolvedInput
    data class Search(val url: String, val query: String) : ResolvedInput
    data class External(val url: String) : ResolvedInput
    data object Rejected : ResolvedInput
}

/** Logika murni (tanpa Android) untuk normalisasi URL dan pencarian, agar mudah diuji. */
object UrlUtils {
    const val DEFAULT_SEARCH_TEMPLATE = "https://www.google.com/search?q=%s"
    const val BLANK = "about:blank"

    /** Template pencarian aktif (diatur dari Pengaturan). Default sama dengan [DEFAULT_SEARCH_TEMPLATE]. */
    @Volatile
    var currentTemplate: String = DEFAULT_SEARCH_TEMPLATE

    private val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val authorityRegex = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,})(:\\d{1,5})?$",
    )
    private val ipv4Regex = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
    private val webSchemes = setOf("http", "https")
    private val externalSchemes = setOf("tel", "mailto", "sms")
    private val blockedSchemes = setOf(
        "javascript", "data", "file", "blob", "intent", "content", "view-source", "vbscript",
    )

    fun resolve(raw: String, searchTemplate: String = currentTemplate): ResolvedInput {
        val input = raw.trim()
        if (input.isEmpty()) return ResolvedInput.Empty
        if (input.any { it.isISOControl() }) return ResolvedInput.Rejected
        if (input.equals(BLANK, ignoreCase = true)) return ResolvedInput.Navigate(BLANK)

        val hasWhitespace = input.any { it.isWhitespace() }
        val scheme = schemeRegex.find(input)?.groupValues?.get(1)?.lowercase()

        if (scheme != null && !looksLikeHost(input)) {
            when {
                scheme in blockedSchemes -> return ResolvedInput.Rejected
                hasWhitespace -> Unit
                scheme in webSchemes ->
                    return if (hasWebHost(input)) ResolvedInput.Navigate(input) else ResolvedInput.Rejected
                scheme in externalSchemes -> return ResolvedInput.External(input)
            }
        }

        if (!hasWhitespace && looksLikeHost(input)) {
            val host = authorityOf(input).substringBefore(':')
            val plain = host.equals("localhost", ignoreCase = true) || ipv4Regex.matches(host)
            return ResolvedInput.Navigate((if (plain) "http://" else "https://") + input)
        }

        return ResolvedInput.Search(buildSearchUrl(input, searchTemplate), input)
    }

    fun buildSearchUrl(query: String, template: String = currentTemplate): String =
        template.replace("%s", URLEncoder.encode(query, "UTF-8"))

    /** Template valid: http/https, tanpa spasi, tepat satu penanda %s. */
    fun isValidSearchTemplate(template: String): Boolean {
        val t = template.trim()
        if (!(t.startsWith("https://") || t.startsWith("http://"))) return false
        if (t.any { it.isWhitespace() }) return false
        return t.split("%s").size == 2
    }

    private fun authorityOf(input: String): String =
        input.substringBefore('/').substringBefore('?').substringBefore('#')

    private fun looksLikeHost(input: String): Boolean {
        val authority = authorityOf(input)
        if (!authorityRegex.matches(authority)) return false
        val host = authority.substringBefore(':')
        return !ipv4Regex.matches(host) || host.split('.').all { it.toInt() in 0..255 }
    }

    private fun hasWebHost(input: String): Boolean {
        val rest = input.substringAfter("://", "")
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        return authority.substringAfterLast('@').isNotEmpty()
    }
}
