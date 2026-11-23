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

/** Nilai pengaturan (murni, tanpa Android agar mudah diuji). */
data class SettingsData(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val customTemplate: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val toolbarBottom: Boolean = false,
) {
    /** Template pencarian efektif. Template kustom yang tidak valid jatuh kembali ke Google. */
    fun searchTemplate(): String = when (searchEngine) {
        SearchEngine.CUSTOM ->
            if (UrlUtils.isValidSearchTemplate(customTemplate)) customTemplate.trim() else SearchEngine.GOOGLE.template
        else -> searchEngine.template
    }
}
