package dev.browsery.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test fun emptyInput() {
        assertEquals(ResolvedInput.Empty, UrlUtils.resolve(""))
        assertEquals(ResolvedInput.Empty, UrlUtils.resolve("   "))
    }

    @Test fun fullHttpsUrlIsKept() {
        assertEquals(
            ResolvedInput.Navigate("https://example.com/a?b=1"),
            UrlUtils.resolve("https://example.com/a?b=1"),
        )
    }

    @Test fun uppercaseSchemeAccepted() {
        assertEquals(
            ResolvedInput.Navigate("HTTPS://EXAMPLE.COM"),
            UrlUtils.resolve("HTTPS://EXAMPLE.COM"),
        )
    }

    @Test fun bareDomainGetsHttps() {
        assertEquals(ResolvedInput.Navigate("https://example.com"), UrlUtils.resolve("example.com"))
        assertEquals(
            ResolvedInput.Navigate("https://example.com/path?x=1"),
            UrlUtils.resolve("  example.com/path?x=1  "),
        )
    }

    @Test fun domainWithPort() {
        assertEquals(
            ResolvedInput.Navigate("https://example.com:8443"),
            UrlUtils.resolve("example.com:8443"),
        )
    }

    @Test fun localhostAndIpUseHttp() {
        assertEquals(
            ResolvedInput.Navigate("http://localhost:8080"),
            UrlUtils.resolve("localhost:8080"),
        )
        assertEquals(
            ResolvedInput.Navigate("http://192.168.1.1"),
            UrlUtils.resolve("192.168.1.1"),
        )
    }

    @Test fun invalidIpBecomesSearch() {
        assertTrue(UrlUtils.resolve("999.1.1.1") is ResolvedInput.Search)
    }

    @Test fun wordsBecomeSearch() {
        assertEquals(
            ResolvedInput.Search("https://www.google.com/search?q=hello+world", "hello world"),
            UrlUtils.resolve("hello world"),
        )
        assertTrue(UrlUtils.resolve("example") is ResolvedInput.Search)
    }

    @Test fun queryIsUrlEncoded() {
        val r = UrlUtils.resolve("c++ & kotlin") as ResolvedInput.Search
        assertEquals("https://www.google.com/search?q=c%2B%2B+%26+kotlin", r.url)
    }

    @Test fun customSearchTemplate() {
        val r = UrlUtils.resolve("kucing lucu", "https://duckduckgo.com/?q=%s")
        assertEquals(
            ResolvedInput.Search("https://duckduckgo.com/?q=kucing+lucu", "kucing lucu"),
            r,
        )
    }

    @Test fun dangerousSchemesRejected() {
        assertEquals(ResolvedInput.Rejected, UrlUtils.resolve("javascript:alert(1)"))
        assertEquals(ResolvedInput.Rejected, UrlUtils.resolve("file:///etc/passwd"))
        assertEquals(ResolvedInput.Rejected, UrlUtils.resolve("data:text/html,hi"))
        assertEquals(ResolvedInput.Rejected, UrlUtils.resolve("intent://x#Intent;end"))
    }

    @Test fun httpWithoutHostRejected() {
        assertEquals(ResolvedInput.Rejected, UrlUtils.resolve("http://"))
    }

    @Test fun externalSchemes() {
        assertEquals(ResolvedInput.External("tel:+62812345"), UrlUtils.resolve("tel:+62812345"))
        assertEquals(ResolvedInput.External("tel:1234"), UrlUtils.resolve("tel:1234"))
        assertEquals(ResolvedInput.External("mailto:a@b.co"), UrlUtils.resolve("mailto:a@b.co"))
    }

    @Test fun unknownSchemeLikeTextIsSearch() {
        assertTrue(UrlUtils.resolve("foo:bar") is ResolvedInput.Search)
    }

    @Test fun aboutBlank() {
        assertEquals(ResolvedInput.Navigate("about:blank"), UrlUtils.resolve("about:blank"))
    }

    @Test fun controlCharactersRejected() {
        assertEquals(ResolvedInput.Rejected, UrlUtils.resolve("exa\u0000mple.com"))
    }

    @Test fun searchTemplateValidation() {
        assertTrue(UrlUtils.isValidSearchTemplate("https://www.bing.com/search?q=%s"))
        assertFalse(UrlUtils.isValidSearchTemplate("https://www.bing.com/search?q="))
        assertFalse(UrlUtils.isValidSearchTemplate("https://x.com/?a=%s&b=%s"))
        assertFalse(UrlUtils.isValidSearchTemplate("javascript:%s"))
        assertFalse(UrlUtils.isValidSearchTemplate("https://x.com/ ?q=%s"))
    }
}
