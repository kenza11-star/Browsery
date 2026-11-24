package dev.browsery.app.settings

import dev.browsery.app.browser.ResolvedInput
import dev.browsery.app.browser.UrlUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDataTest {
    @After fun restore() {
        UrlUtils.currentTemplate = UrlUtils.DEFAULT_SEARCH_TEMPLATE
    }

    @Test fun defaultsAreGoogleSystemTop() {
        val d = SettingsData()
        assertEquals(SearchEngine.GOOGLE.template, d.searchTemplate())
        assertEquals(ThemeMode.SYSTEM, d.theme)
        assertEquals(false, d.toolbarBottom)
        assertEquals(UrlUtils.DEFAULT_SEARCH_TEMPLATE, SearchEngine.GOOGLE.template)
    }

    @Test fun presetTemplatesAreValid() {
        SearchEngine.entries.filter { it != SearchEngine.CUSTOM }.forEach {
            assertTrue(it.id, UrlUtils.isValidSearchTemplate(it.template))
        }
    }

    @Test fun validCustomTemplateIsUsed() {
        val d = SettingsData(searchEngine = SearchEngine.CUSTOM, customTemplate = " https://example.org/s?q=%s ")
        assertEquals("https://example.org/s?q=%s", d.searchTemplate())
    }

    @Test fun invalidCustomTemplateFallsBackToGoogle() {
        listOf("", "example.org/%s", "https://a.com/", "https://a.com/%s%s", "javascript:%s", "https://a.com/ %s").forEach {
            val d = SettingsData(searchEngine = SearchEngine.CUSTOM, customTemplate = it)
            assertEquals(it, SearchEngine.GOOGLE.template, d.searchTemplate())
        }
    }

    @Test fun unknownIdsFallBackToDefaults() {
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId("nope"))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId("nope"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromId("dark"))
    }

    @Test fun currentTemplateDrivesAddressBarSearch() {
        UrlUtils.currentTemplate = SearchEngine.DUCKDUCKGO.template
        val r = UrlUtils.resolve("halo dunia")
        assertTrue(r is ResolvedInput.Search)
        assertEquals("https://duckduckgo.com/?q=halo+dunia", (r as ResolvedInput.Search).url)
    }
}
