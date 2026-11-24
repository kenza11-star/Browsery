package dev.browsery.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLinksTest {
    @Test
    fun authorizeUrlIsEncoded() {
        val u = AuthLinks.authorizeUrl("https://x.supabase.co/", "google", "browsery://auth-callback")
        assertEquals(
            "https://x.supabase.co/auth/v1/authorize?provider=google&redirect_to=browsery%3A%2F%2Fauth-callback",
            u,
        )
    }

    @Test
    fun parsesTokensFromFragment() {
        val cb = AuthLinks.parseCallback(
            "browsery://auth-callback#access_token=abc.def&refresh_token=r1&expires_in=7200&token_type=bearer",
        )
        assertNotNull(cb)
        assertEquals("abc.def", cb!!.accessToken)
        assertEquals("r1", cb.refreshToken)
        assertEquals(7200L, cb.expiresIn)
        assertNull(cb.error)
    }

    @Test
    fun parsesErrorDescription() {
        val cb = AuthLinks.parseCallback("browsery://auth-callback?error=access_denied&error_description=User+cancelled")
        assertNotNull(cb)
        assertEquals("User cancelled", cb!!.error)
        assertNull(cb.accessToken)
    }

    @Test
    fun rejectsCallbackWithoutTokens() {
        assertNull(AuthLinks.parseCallback("browsery://auth-callback"))
        assertNull(AuthLinks.parseCallback("browsery://auth-callback#access_token=only"))
    }

    @Test
    fun validatesInput() {
        assertTrue(AuthLinks.isValidEmail("a@b.co"))
        assertFalse(AuthLinks.isValidEmail("a@b"))
        assertFalse(AuthLinks.isValidEmail("a b@c.de"))
        assertTrue(AuthLinks.isPasswordOk("12345678"))
        assertFalse(AuthLinks.isPasswordOk("1234567"))
    }

    @Test
    fun callbackPrefix() {
        assertTrue(AuthLinks.isCallback("browsery://auth-callback#x=1", SupabaseConfig.REDIRECT))
        assertFalse(AuthLinks.isCallback("https://evil.example/", SupabaseConfig.REDIRECT))
    }
}
