package dev.browsery.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabLimitsTest {
    @Test
    fun lowRamIsAlwaysSmall() {
        assertEquals(3, TabLimits.forMemory(512, true))
    }

    @Test
    fun growsWithMemoryClass() {
        assertEquals(3, TabLimits.forMemory(128, false))
        assertEquals(4, TabLimits.forMemory(192, false))
        assertEquals(6, TabLimits.forMemory(256, false))
        assertEquals(8, TabLimits.forMemory(512, false))
    }

    @Test
    fun neverBelowTwo() {
        assertTrue(TabLimits.forMemory(0, true) >= 2)
    }
}
