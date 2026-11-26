package dev.browsery.app.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidBandTest {
    @Test
    fun visibleAreaMatchesCoverageAtAllPhases() {
        for (cov in listOf(0.25f, 0.35f, 0.5f)) {
            val band = LiquidBand(1080f, 2400f, cov)
            var p = 0f
            while (p < 60f) {
                assertEquals("cov=$cov p=$p", cov, band.areaFraction(p), 0.01f)
                p += 0.7f
            }
        }
    }

    @Test
    fun bandStaysInsideScreenVertically() {
        val band = LiquidBand(1080f, 2400f, 0.5f)
        var p = 0f
        while (p < 100f) {
            var x = 0f
            while (x <= 1080f) {
                assertTrue(band.top(x, p) >= 0f)
                assertTrue(band.bottom(x, p) <= 2400f)
                x += 30f
            }
            p += 0.5f
        }
    }

    @Test
    fun sampleSizeKeepsAtLeastScreenSize() {
        assertEquals(1, sampleSizeFor(4000, 2250, 1080, 2400))
        assertEquals(2, sampleSizeFor(8000, 6000, 1080, 2400).coerceAtMost(2))
        assertEquals(1, sampleSizeFor(1000, 1000, 1080, 2400))
    }
}
