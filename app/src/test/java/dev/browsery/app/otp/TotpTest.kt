package dev.browsery.app.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpTest {
    private val sha1Key = "12345678901234567890".toByteArray()
    private val sha256Key = "12345678901234567890123456789012".toByteArray()
    private val sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    @Test
    fun rfc6238Sha1Vectors() {
        val v = mapOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130",
        )
        for ((t, want) in v) assertEquals("t=$t", want, Totp.code(sha1Key, t, 8, 30, OtpAlgorithm.SHA1))
    }

    @Test
    fun rfc6238Sha256And512Vectors() {
        assertEquals("46119246", Totp.code(sha256Key, 59L, 8, 30, OtpAlgorithm.SHA256))
        assertEquals("68084774", Totp.code(sha256Key, 1111111109L, 8, 30, OtpAlgorithm.SHA256))
        assertEquals("90693936", Totp.code(sha512Key, 59L, 8, 30, OtpAlgorithm.SHA512))
        assertEquals("25091201", Totp.code(sha512Key, 1111111109L, 8, 30, OtpAlgorithm.SHA512))
    }

    @Test
    fun sixDigitCodeIsLastSixOfEight() {
        assertEquals("287082", Totp.code(sha1Key, 59L, 6, 30, OtpAlgorithm.SHA1))
    }

    @Test
    fun secondsRemaining() {
        assertEquals(30, Totp.secondsRemaining(60L, 30))
        assertEquals(1, Totp.secondsRemaining(59L, 30))
        assertEquals(25, Totp.secondsRemaining(65L, 30))
    }

    @Test
    fun base32Decode() {
        assertEquals("abc", String(Base32.decode("MFRGG===")!!))
        assertEquals("abc", String(Base32.decode("mfr gg")!!))
        assertEquals("12345678901234567890", String(Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")!!))
        assertNull(Base32.decode("MFRG1"))
        assertNull(Base32.decode(""))
    }

    @Test
    fun parseStandardUri() {
        val e = OtpUri.parse("otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example")
        assertNotNull(e)
        assertEquals("Example", e!!.issuer)
        assertEquals("alice@example.com", e.account)
        assertEquals("JBSWY3DPEHPK3PXP", e.secret)
        assertEquals(6, e.digits)
        assertEquals(30, e.period)
        assertEquals(OtpAlgorithm.SHA1, e.algorithm)
    }

    @Test
    fun parseOptionsAndRejectInvalid() {
        val e = OtpUri.parse("otpauth://totp/a%40b.com?secret=jbsw y3dp ehpk 3pxp&digits=8&period=60&algorithm=SHA256")
        assertNotNull(e)
        assertEquals(8, e!!.digits)
        assertEquals(60, e.period)
        assertEquals(OtpAlgorithm.SHA256, e.algorithm)
        assertEquals("a@b.com", e.account)
        assertNull(OtpUri.parse("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP"))
        assertNull(OtpUri.parse("otpauth://totp/x?secret=ABC"))
        assertNull(OtpUri.parse("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=4"))
        assertNull(OtpUri.parse("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&algorithm=MD5"))
        assertNull(OtpUri.parse("https://example.com"))
    }

    @Test
    fun formatRoundTrip() {
        val e = OtpEntry(id = "id-1", issuer = "My Site", account = "bob+x@mail.com", secret = "JBSWY3DPEHPK3PXP", digits = 8, period = 60, algorithm = OtpAlgorithm.SHA512)
        val back = OtpUri.parse(OtpUri.format(e))
        assertEquals(e, back)
        assertTrue(OtpUri.format(e).startsWith("otpauth://totp/"))
    }
}
