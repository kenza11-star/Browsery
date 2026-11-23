package dev.browsery.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyDataTest {
    @Test fun defaultsAreStandardIsolateNoHttpsOnly() {
        val d = SettingsData()
        assertEquals(TrackingLevel.STANDARD, d.trackingLevel)
        assertEquals(CookiePolicy.ISOLATE, d.cookiePolicy)
        assertFalse(d.httpsOnly)
        assertFalse(d.globalPrivacyControl)
        assertTrue(d.siteExceptions.isEmpty())
    }

    @Test fun hostNormalization() {
        assertEquals("example.com", Hosts.normalize(" WWW.Example.COM "))
        assertEquals("sub.example.com", Hosts.normalize("sub.example.com"))
        assertNull(Hosts.normalize(""))
        assertNull(Hosts.normalize(null))
    }

    @Test fun exceptionDisablesProtectionOnlyForThatHost() {
        val d = SettingsData().withException("www.Example.com", true)
        assertFalse(d.protectionFor("example.com"))
        assertFalse(d.protectionFor("www.example.com"))
        assertTrue(d.protectionFor("other.com"))
    }

    @Test fun removingExceptionRestoresProtection() {
        val d = SettingsData().withException("a.com", true).withException("a.com", false)
        assertTrue(d.protectionFor("a.com"))
        assertTrue(d.siteExceptions.isEmpty())
    }

    @Test fun globalOffDisablesEverywhere() {
        val d = SettingsData(trackingLevel = TrackingLevel.OFF)
        assertFalse(d.protectionFor("a.com"))
        assertFalse(d.protectionFor(null))
    }

    @Test fun blankHostExceptionIsIgnored() {
        assertEquals(SettingsData(), SettingsData().withException("  ", true))
        assertFalse(SettingsData().isExcepted(null))
    }

    @Test fun unknownIdsFallBack() {
        assertEquals(TrackingLevel.STANDARD, TrackingLevel.fromId("x"))
        assertEquals(TrackingLevel.STRICT, TrackingLevel.fromId("strict"))
        assertEquals(CookiePolicy.ISOLATE, CookiePolicy.fromId(null))
        assertEquals(CookiePolicy.FIRST_PARTY, CookiePolicy.fromId("first_party"))
    }
}
