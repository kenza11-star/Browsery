package dev.browsery.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunksAndNamesTest {
    @Test fun planCoversEverythingWithoutGaps() {
        val chunks = Chunks.plan(1003, 4)
        assertEquals(4, chunks.size)
        assertEquals(0L, chunks.first().start)
        assertEquals(1002L, chunks.last().end)
        assertEquals(1003L, chunks.sumOf { it.length })
        assertTrue(Chunks.isValid(chunks, 1003))
    }

    @Test fun planNeverMakesMoreChunksThanBytes() {
        assertEquals(3, Chunks.plan(3, 16).size)
        assertEquals(1, Chunks.plan(1, 8).size)
    }

    @Test fun planClampsConnectionCount() {
        assertEquals(1, Chunks.plan(100, 0).size)
        assertEquals(1, Chunks.plan(100, -5).size)
    }

    @Test fun codecRoundTripAndGarbage() {
        val chunks = listOf(Chunk(0, 99, 50), Chunk(100, 199, 0))
        assertEquals(chunks, Chunks.decode(Chunks.encode(chunks)))
        assertTrue(Chunks.decode("abc").isEmpty())
        assertTrue(Chunks.decode(null).isEmpty())
    }

    @Test fun invalidChunksAreRejected() {
        assertFalse(Chunks.isValid(emptyList(), 10))
        assertFalse(Chunks.isValid(listOf(Chunk(0, 4), Chunk(6, 9)), 10))
        assertFalse(Chunks.isValid(listOf(Chunk(0, 9, 11)), 10))
        assertFalse(Chunks.isValid(listOf(Chunk(0, 4)), 10))
    }

    @Test fun autoConnectionsGrowWithSize() {
        assertEquals(1, Chunks.autoConnections(100_000))
        assertEquals(2, Chunks.autoConnections(5L shl 20))
        assertEquals(4, Chunks.autoConnections(50L shl 20))
        assertEquals(8, Chunks.autoConnections(500L shl 20))
    }

    @Test fun contentDispositionVariants() {
        assertEquals("a b.zip", FileNames.fromContentDisposition("attachment; filename=\"a b.zip\""))
        assertEquals("x.pdf", FileNames.fromContentDisposition("attachment; filename=x.pdf"))
        assertEquals("résumé.pdf", FileNames.fromContentDisposition("attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf"))
        assertEquals("a+b.txt", FileNames.fromContentDisposition("attachment; filename*=UTF-8''a+b.txt"))
        assertNull(FileNames.fromContentDisposition("inline"))
        assertNull(FileNames.fromContentDisposition(null))
    }

    @Test fun fileNameFromUrl() {
        assertEquals("file.zip", FileNames.fromUrl("https://a.com/dir/file.zip?x=1"))
        assertNull(FileNames.fromUrl("https://a.com/"))
    }

    @Test fun sanitizeBlocksTraversalAndBadChars() {
        assertEquals("passwd", FileNames.sanitize("../../etc/passwd"))
        assertEquals("evil.exe", FileNames.sanitize("..\\..\\evil.exe"))
        assertEquals("a_b_c.txt", FileNames.sanitize("a<b>c.txt"))
        assertEquals("download", FileNames.sanitize("..."))
        assertEquals("download", FileNames.sanitize(null))
        assertEquals("hidden", FileNames.sanitize(".hidden"))
    }

    @Test fun sanitizeLimitsLengthKeepingExtension() {
        val n = FileNames.sanitize("a".repeat(300) + ".mp4")
        assertTrue(n.length <= 120)
        assertTrue(n.endsWith(".mp4"))
    }

    @Test fun uniqueAddsCounterBeforeExtension() {
        val taken = setOf("a.zip", "a (1).zip")
        assertEquals("a (2).zip", FileNames.unique("a.zip") { it in taken })
        assertEquals("b.zip", FileNames.unique("b.zip") { it in taken })
        assertEquals("noext (1)", FileNames.unique("noext") { it == "noext" })
    }

    @Test fun formatting() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1.5 KB", Format.bytes(1536))
        assertEquals("2.0 MB", Format.bytes(2L shl 20))
        assertEquals("1.0 MB/s", Format.speed(1L shl 20))
        assertEquals("45d", Format.eta(45))
        assertEquals("2m 5d", Format.eta(125))
        assertEquals("1j 1m", Format.eta(3700))
    }
}
