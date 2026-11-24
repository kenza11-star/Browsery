package dev.browsery.app.download

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Server HTTP lokal untuk menguji engine unduhan (206, 200 tanpa Range, redirect, 404, koneksi putus, file berubah). */
class TestServer {
    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    @Volatile var content: ByteArray = gen(1024, 1)
    @Volatile var etag: String = "\"v1\""
    @Volatile var slowMs: Long = 0
    val flaky = AtomicInteger(0)
    val rangeStarts = CopyOnWriteArrayList<Long>()
    val served = AtomicLong(0)
    val rangeRequests = AtomicInteger(0)

    init {
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/file") { ex -> serve(ex, honorRange = true) }
        server.createContext("/norange") { ex -> serve(ex, honorRange = false) }
        server.createContext("/redirect") { ex ->
            ex.responseHeaders.add("Location", "/file")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.createContext("/missing") { ex ->
            ex.sendResponseHeaders(404, -1)
            ex.close()
        }
        server.start()
    }

    fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    fun stop() = server.stop(0)

    private fun serve(ex: HttpExchange, honorRange: Boolean) {
        try {
            val data = content
            val range = ex.requestHeaders.getFirst("Range")
            val ifRange = ex.requestHeaders.getFirst("If-Range")
            ex.responseHeaders.add("ETag", etag)
            ex.responseHeaders.add("Accept-Ranges", if (honorRange) "bytes" else "none")
            var start = 0
            var end = data.size - 1
            var partial = false
            if (honorRange && range != null && (ifRange == null || ifRange == etag)) {
                val m = Regex("bytes=(\\d+)-(\\d*)").matchEntire(range)
                if (m != null) {
                    start = m.groupValues[1].toInt()
                    end = m.groupValues[2].toIntOrNull()?.coerceAtMost(data.size - 1) ?: (data.size - 1)
                    partial = true
                    rangeRequests.incrementAndGet()
                    rangeStarts.add(start.toLong())
                }
            }
            val len = end - start + 1
            if (partial) {
                ex.responseHeaders.add("Content-Range", "bytes $start-$end/${data.size}")
                ex.sendResponseHeaders(206, len.toLong())
            } else {
                ex.sendResponseHeaders(200, len.toLong())
            }
            val cut = len > 10_000 && flaky.get() > 0 && flaky.getAndDecrement() > 0
            val limit = if (cut) len / 2 else len
            val out = ex.responseBody
            var pos = 0
            while (pos < limit) {
                val n = minOf(65536, limit - pos)
                out.write(data, start + pos, n)
                out.flush()
                served.addAndGet(n.toLong())
                pos += n
                if (slowMs > 0) Thread.sleep(slowMs)
            }
            if (!cut) out.close()
        } catch (e: IOException) {
            // klien memutus koneksi (probe, pause, atau potongan lain gagal)
        } finally {
            try {
                ex.close()
            } catch (e: IOException) {
                // pemutusan sengaja
            }
        }
    }

    companion object {
        fun gen(size: Int, seed: Int) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }
    }
}
