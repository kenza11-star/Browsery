package dev.browsery.app.download

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: TestServer
    private lateinit var part: File
    private val engine = DownloadEngine(DownloadEngine.Config(backoffBaseMs = 20, readTimeoutMs = 5_000))

    @Before fun setUp() {
        server = TestServer()
        part = File(tmp.root, "x.part")
    }

    @After fun tearDown() = server.stop()

    private fun run(path: String, connections: Int, resume: Resume? = null, stop: AtomicBoolean = AtomicBoolean(false)) =
        engine.download(server.url(path), null, part, connections, resume, stop)

    @Test fun singleConnectionWithRangeSupport() {
        server.content = TestServer.gen(300_000, 3)
        assertEquals(Outcome.Completed, run("/file", 1))
        assertArrayEquals(server.content, part.readBytes())
    }

    @Test fun parallelUsesSeveralRangesAndProducesIdenticalFile() {
        server.content = TestServer.gen(4 * 1024 * 1024, 5)
        assertEquals(Outcome.Completed, run("/file", 4))
        assertArrayEquals(server.content, part.readBytes())
        val expectedStarts = Chunks.plan(server.content.size.toLong(), 4).map { it.start }
        assertTrue("harus ada 4 permintaan Range pada awal tiap potongan", server.rangeStarts.containsAll(expectedStarts))
    }

    @Test fun serverWithoutRangeFallsBackToSingleStream() {
        server.content = TestServer.gen(2 * 1024 * 1024, 7)
        assertEquals(Outcome.Completed, run("/norange", 4))
        assertArrayEquals(server.content, part.readBytes())
        assertEquals(0, server.rangeRequests.get())
    }

    @Test fun redirectIsFollowed() {
        server.content = TestServer.gen(50_000, 9)
        assertEquals(Outcome.Completed, run("/redirect", 1))
        assertArrayEquals(server.content, part.readBytes())
    }

    @Test fun notFoundFailsWithHttpReason() {
        val o = run("/missing", 1)
        assertTrue(o is Outcome.Failed)
        assertEquals(Reason.HTTP, (o as Outcome.Failed).reason)
    }

    @Test fun connectionDropsAreRetriedAndResumedFromWhereTheyStopped() {
        server.content = TestServer.gen(1024 * 1024, 11)
        server.flaky.set(2)
        assertEquals(Outcome.Completed, run("/file", 1))
        assertArrayEquals(server.content, part.readBytes())
    }

    @Test fun pauseThenResumeContinuesWithoutRedownloadingEverything() {
        server.content = TestServer.gen(4 * 1024 * 1024, 13)
        server.slowMs = 40
        val stop = AtomicBoolean(false)
        var last: Snapshot? = null
        val first = engine.download(server.url("/file"), null, part, 2, null, stop, onProgress = { s ->
            last = s
            if (s.downloaded > 256 * 1024) stop.set(true)
        })
        assertEquals(Outcome.Stopped, first)
        val snap = last!!
        assertTrue("harus parsial", snap.downloaded > 0 && snap.downloaded < server.content.size)

        server.slowMs = 0
        server.served.set(0)
        val second = run("/file", 2, Resume(snap.total, snap.etag, snap.lastModified, snap.chunks))
        assertEquals(Outcome.Completed, second)
        assertArrayEquals(server.content, part.readBytes())
        assertTrue("tidak boleh mengunduh ulang seluruh file", server.served.get() < server.content.size)
    }

    @Test fun changedFileOnResumeRestartsFromScratchInsteadOfCorrupting() {
        server.content = TestServer.gen(4 * 1024 * 1024, 17)
        server.slowMs = 40
        val stop = AtomicBoolean(false)
        var last: Snapshot? = null
        engine.download(server.url("/file"), null, part, 2, null, stop, onProgress = { s ->
            last = s
            if (s.downloaded > 256 * 1024) stop.set(true)
        })
        val snap = last!!

        server.slowMs = 0
        server.content = TestServer.gen(4 * 1024 * 1024, 19)
        server.etag = "\"v2\""
        val o = run("/file", 2, Resume(snap.total, snap.etag, snap.lastModified, snap.chunks))
        assertEquals(Outcome.Completed, o)
        assertArrayEquals("harus berisi versi baru, bukan campuran", server.content, part.readBytes())
    }

    @Test fun stopBeforeStartReturnsStopped() {
        assertEquals(Outcome.Stopped, run("/file", 1, stop = AtomicBoolean(true)))
    }

    @Test fun unsupportedSchemeFails() {
        val o = engine.download("ftp://example.com/a", null, part, 1, null, AtomicBoolean(false))
        assertTrue(o is Outcome.Failed)
        assertEquals(Reason.UNSUPPORTED, (o as Outcome.Failed).reason)
    }
}
