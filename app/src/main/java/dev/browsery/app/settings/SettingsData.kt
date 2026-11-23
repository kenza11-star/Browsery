package dev.browsery.app.settings

import dev.browsery.app.browser.UrlUtils

enum class SearchEngine(val id: String, val label: String, val template: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s"),
    CUSTOM("custom", "Kustom", ""),
    ;

    companion object {
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Tingkat Enhanced Tracking Protection milik engine Gecko. */
enum class TrackingLevel(val id: String) {
    OFF("off"),
    STANDARD("standard"),
    STRICT("strict"),
    ;

    companion object {
        fun fromId(id: String?): TrackingLevel = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** Kebijakan cookie (hanya opsi yang didukung GeckoView). */
enum class CookiePolicy(val id: String) {
    ALL("all"),
    NON_TRACKERS("non_trackers"),
    ISOLATE("isolate"),
    FIRST_PARTY("first_party"),
    ;

    companion object {
        fun fromId(id: String?): CookiePolicy = entries.firstOrNull { it.id == id } ?: ISOLATE
    }
}

object Hosts {
    /** Nama host huruf kecil tanpa awalan "www."; null bila kosong. */
    fun normalize(host: String?): String? {
        val h = host?.trim()?.lowercase()?.removePrefix("www.")
        return if (h.isNullOrEmpty()) null else h
    }
}

/** Nilai pengaturan (murni, tanpa Android agar mudah diuji). */
data class SettingsData(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val customTemplate: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val toolbarBottom: Boolean = false,
    val trackingLevel: TrackingLevel = TrackingLevel.STANDARD,
    val cookiePolicy: CookiePolicy = CookiePolicy.ISOLATE,
    val httpsOnly: Boolean = false,
    val globalPrivacyControl: Boolean = false,
    /** Situs yang perlindungan pelacaknya dimatikan (host ternormalisasi). */
    val siteExceptions: Set<String> = emptySet(),
    /** ID ekstensi bawaan yang disetujui pengguna untuk dipasang. */
    val extensionsEnabled: Set<String> = emptySet(),
    /** ID ekstensi yang diizinkan pengguna berjalan di tab privat. */
    val extensionsPrivate: Set<String> = emptySet(),
) {
    /** Template pencarian efektif. Template kustom yang tidak valid jatuh kembali ke Google. */
    fun searchTemplate(): String = when (searchEngine) {
        SearchEngine.CUSTOM ->
            if (UrlUtils.isValidSearchTemplate(customTemplate)) customTemplate.trim() else SearchEngine.GOOGLE.template
        else -> searchEngine.template
    }

    fun isExcepted(host: String?): Boolean {
        val h = Hosts.normalize(host) ?: return false
        return h in siteExceptions
    }

    /** Perlindungan pelacak aktif untuk [host] (global menyala dan situs tidak dikecualikan). */
    fun protectionFor(host: String?): Boolean = trackingLevel != TrackingLevel.OFF && !isExcepted(host)

    fun withException(host: String?, excepted: Boolean): SettingsData {
        val h = Hosts.normalize(host) ?: return this
        return copy(siteExceptions = if (excepted) siteExceptions + h else siteExceptions - h)
    }

    fun withExtension(id: String, enabled: Boolean): SettingsData =
        copy(extensionsEnabled = if (enabled) extensionsEnabled + id else extensionsEnabled - id)

    fun withExtensionPrivate(id: String, allowed: Boolean): SettingsData =
        copy(extensionsPrivate = if (allowed) extensionsPrivate + id else extensionsPrivate - id)
}
