package dev.browsery.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentsTest {
    @Test fun singleConnectionIsOneChunk() {
        assertEquals(1, Chunks.planSegments(70L shl 20, 1).size)
    }

    @Test fun eightConnectionsOn70MbMakeManySmallSegments() {
        val total = 70L shl 20
        val segs = Chunks.planSegments(total, 8)
        assertTrue("harus jauh lebih banyak dari jumlah koneksi: ${segs.size}", segs.size >= 24)
        assertTrue(Chunks.isValid(segs, total))
        assertTrue(segs.all { it.length <= (9L shl 20) })
    }

    @Test fun neverFewerSegmentsThanConnectionsAndAlwaysCovered() {
        for (total in listOf(1_500_000L, 3_000_001L, 100L shl 20, 5_000L shl 20)) {
            for (c in listOf(2, 4, 8)) {
                val segs = Chunks.planSegments(total, c)
                assertTrue(segs.size >= c)
                assertTrue(segs.size <= 512)
                assertTrue(Chunks.isValid(segs, total))
            }
        }
    }
}
