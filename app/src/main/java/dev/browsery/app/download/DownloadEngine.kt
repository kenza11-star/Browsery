package dev.browsery.app.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference

enum class Reason { HTTP, NETWORK, CHANGED, IO, UNSUPPORTED, SIZE_MISMATCH }

class DownloadException(val reason: Reason, message: String) : Exception(message)

private class StoppedException : Exception()

/** Hasil pemeriksaan awal server. [total] = -1 bila tidak diketahui. */
data class Probe(
    val url: String,
    val total: Long,
    val acceptRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
    val contentDisposition: String?,
    val contentType: String?,
)

/** Status yang cukup untuk melanjutkan unduhan nanti. */
data class Resume(
    val total: Long,
    val etag: String?,
    val lastModified: String?,
    val chunks: List<Chunk>,
)

data class Snapshot(
    val downloaded: Long,
    val total: Long,
    val chunks: List<Chunk>,
    val etag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean,
)

sealed interface Outcome {
    data object Completed : Outcome
    data object Stopped : Outcome
    data class Failed(val reason: Reason, val message: String) : Outcome
}

/**
 * Engine unduhan berbasis HttpURLConnection (tanpa dependensi tambahan), blocking, dipanggil dari thread latar.
 *
 * - Server mendukung Range (206 + Content-Range valid + ukuran diketahui): file dibagi menjadi potongan; tiap
 *   potongan diunduh oleh satu koneksi. 1 koneksi = 1 potongan (tetap bisa dilanjutkan).
 * - Selain itu: satu aliran biasa (tidak dapat dilanjutkan; mulai ulang dari nol).
 * - Resume hanya bila total, ETag/Last-Modified sama dan file parsial utuh; selain itu dimulai ulang dari nol.
 */
class DownloadEngine(private val cfg: Config = Config()) {
    data class Config(
        val connectTimeoutMs: Int = 15_000,
        val readTimeoutMs: Int = 20_000,
        val maxRetries: Int = 3,
        val backoffBaseMs: Long = 500,
        val minParallelBytes: Long = 1L shl 20,
        val userAgent: String = "Mozilla/5.0 (Android 14; Mobile; rv:159.0) Gecko/159.0 Firefox/159.0",
    )

    /**
     * @param connections 1 = satu koneksi, 0 = otomatis (berdasarkan ukuran), >1 = jumlah koneksi.
     */
    fun download(
        url: String,
        referer: String?,
        part: File,
        connections: Int,
        resume: Resume?,
        stop: AtomicBoolean,
        onProbe: (Probe) -> Unit = {},
        onProgress: (Snapshot) -> Unit = {},
    ): Outcome {
        val probe = try {
            probe(url, referer, stop)
        } catch (e: StoppedException) {
            return Outcome.Stopped
        } catch (e: DownloadException) {
            return Outcome.Failed(e.reason, e.message.orEmpty())
        } catch (e: IOException) {
            return Outcome.Failed(Reason.NETWORK, e.message.orEmpty())
        }
        onProbe(probe)
        return try {
            part.parentFile?.mkdirs()
            if (probe.acceptRanges && probe.total > 0) {
                rangedDownload(probe, referer, part, connections, resume, stop, onProgress)
            } else {
                streamDownload(probe, referer, part, stop, onProgress)
            }
        } catch (e: StoppedException) {
            Outcome.Stopped
        } catch (e: DownloadException) {
            Outcome.Failed(e.reason, e.message.orEmpty())
        } catch (e: IOException) {
            Outcome.Failed(Reason.IO, e.message.orEmpty())
        }
    }

    // ---------------------------------------------------------------- probe

    private fun probe(url: String, referer: String?, stop: AtomicBoolean): Probe {
        var current = url
        var failures = 0
        var redirects = 0
        while (true) {
            if (stop.get()) throw StoppedException()
            val conn = open(current, referer)
            try {
                conn.setRequestProperty("Range", "bytes=0-0")
                val code = conn.responseCode
                if (code in REDIRECTS) {
                    val loc = conn.getHeaderField("Location")
                        ?: throw DownloadException(Reason.HTTP, "Pengalihan tanpa Location")
                    if (++redirects > MAX_REDIRECTS) throw DownloadException(Reason.HTTP, "Terlalu banyak pengalihan")
                    current = try {
                        URL(URL(current), loc).toString()
                    } catch (e: MalformedURLException) {
                        throw DownloadException(Reason.HTTP, "Alamat pengalihan tidak valid")
                    }
                    continue
                }
                val etag = conn.getHeaderField("ETag")
                val lm = conn.getHeaderField("Last-Modified")
                val cd = conn.getHeaderField("Content-Disposition")
                val ct = conn.contentType
                when {
                    code == 206 -> {
                        val m = CONTENT_RANGE.matchEntire(conn.getHeaderField("Content-Range")?.trim().orEmpty())
                        val total = m?.groupValues?.get(3)?.toLongOrNull() ?: -1L
                        return Probe(current, total, total > 0, etag, lm, cd, ct)
                    }
                    code == 200 -> return Probe(current, conn.contentLengthLong, false, etag, lm, cd, ct)
                    code == 416 -> return Probe(current, 0L, false, etag, lm, cd, ct)
                    isRetryable(code) -> throw IOException("HTTP $code")
                    else -> throw DownloadException(Reason.HTTP, "HTTP $code")
                }
            } catch (e: IOException) {
                if (++failures > cfg.maxRetries) {
                    throw DownloadException(Reason.NETWORK, e.message ?: "Gagal terhubung")
                }
                sleepChecked(backoff(failures), stop, null)
            } finally {
                conn.disconnect()
            }
        }
    }

    // --------------------------------------------------------------- ranged

    private fun rangedDownload(
        probe: Probe,
        referer: String?,
        part: File,
        connections: Int,
        resume: Resume?,
        stop: AtomicBoolean,
        onProgress: (Snapshot) -> Unit,
    ): Outcome {
        val usable = resume != null && part.exists() && part.length() == probe.total &&
            sameIdentity(resume, probe) && Chunks.isValid(resume.chunks, probe.total)
        val plan: List<Chunk> = if (usable && resume != null) {
            resume.chunks
        } else {
            part.delete()
            val n = when {
                connections == 1 || probe.total < cfg.minParallelBytes -> 1
                connections <= 0 -> Chunks.autoConnections(probe.total)
                else -> connections
            }
            Chunks.plan(probe.total, n)
        }
        RandomAccessFile(part, "rw").use { it.setLength(probe.total) }

        val done = AtomicLongArray(plan.size)
        plan.forEachIndexed { i, c -> done.set(i, c.done) }

        fun snapshot(): Snapshot {
            var sum = 0L
            val list = plan.mapIndexed { i, c ->
                val d = done.get(i)
                sum += d
                c.copy(done = d)
            }
            return Snapshot(sum, probe.total, list, probe.etag, probe.lastModified, true)
        }

        val failure = AtomicReference<Throwable?>(null)
        val abort = AtomicBoolean(false)
        val threads = plan.indices.filter { !plan[it].isComplete }.map { i ->
            Thread({
                try {
                    runChunk(i, plan[i], probe, referer, part, done, stop, abort)
                } catch (e: StoppedException) {
                    // berhenti karena pause/cancel atau karena potongan lain gagal
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                    abort.set(true)
                }
            }, "browsery-dl-$i").also { it.start() }
        }
        while (threads.any { it.isAlive }) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                stop.set(true)
            }
            onProgress(snapshot())
        }
        threads.forEach { it.join() }
        val finalSnap = snapshot()
        onProgress(finalSnap)

        val err = failure.get()
        if (err != null) {
            return if (err is DownloadException) {
                Outcome.Failed(err.reason, err.message.orEmpty())
            } else {
                Outcome.Failed(Reason.IO, err.message.orEmpty())
            }
        }
        if (stop.get()) return Outcome.Stopped
        if (finalSnap.downloaded != probe.total || part.length() != probe.total) {
            return Outcome.Failed(Reason.SIZE_MISMATCH, "Ukuran tidak sesuai")
        }
        return Outcome.Completed
    }

    private fun sameIdentity(r: Resume, p: Probe): Boolean {
        val hasValidator = r.etag != null || r.lastModified != null
        return hasValidator && r.total == p.total && r.etag == p.etag && r.lastModified == p.lastModified
    }

    private fun ifRange(p: Probe): String? = when {
        p.etag != null && !p.etag.startsWith("W/") -> p.etag
        p.lastModified != null -> p.lastModified
        else -> null
    }

    private fun runChunk(
        i: Int,
        chunk: Chunk,
        probe: Probe,
        referer: String?,
        part: File,
        done: AtomicLongArray,
        stop: AtomicBoolean,
        abort: AtomicBoolean,
    ) {
        RandomAccessFile(part, "rw").use { raf ->
            var failures = 0
            while (done.get(i) < chunk.length) {
                checkStop(stop, abort)
                val before = done.get(i)
                try {
                    fetchRange(probe, referer, chunk, i, raf, done, stop, abort)
                } catch (e: IOException) {
                    if (done.get(i) > before) failures = 0
                    failures++
                    if (failures > cfg.maxRetries) {
                        throw DownloadException(Reason.NETWORK, e.message ?: "Koneksi terputus")
                    }
                    sleepChecked(backoff(failures), stop, abort)
                }
            }
        }
    }

    private fun fetchRange(
        probe: Probe,
        referer: String?,
        chunk: Chunk,
        i: Int,
        raf: RandomAccessFile,
        done: AtomicLongArray,
        stop: AtomicBoolean,
        abort: AtomicBoolean,
    ) {
        val conn = open(probe.url, referer)
        try {
            val from = chunk.start + done.get(i)
            conn.setRequestProperty("Range", "bytes=$from-${chunk.end}")
            ifRange(probe)?.let { conn.setRequestProperty("If-Range", it) }
            val code = conn.responseCode
            when {
                code == 206 -> Unit
                code == 200 || code == 416 ->
                    throw DownloadException(Reason.CHANGED, "Berkas berubah atau server mengabaikan Range")
                isRetryable(code) -> throw IOException("HTTP $code")
                else -> throw DownloadException(Reason.HTTP, "HTTP $code")
            }
            val cr = CONTENT_RANGE.matchEntire(conn.getHeaderField("Content-Range")?.trim().orEmpty())
                ?: throw DownloadException(Reason.CHANGED, "Content-Range tidak valid")
            val rangeStart = cr.groupValues[1].toLong()
            val rangeTotal = cr.groupValues[3].toLongOrNull()
            if (rangeStart != from || (rangeTotal != null && rangeTotal != probe.total)) {
                throw DownloadException(Reason.CHANGED, "Content-Range tidak cocok dengan permintaan")
            }
            var pos = from
            val buf = ByteArray(32 * 1024)
            conn.inputStream.use { ins ->
                while (pos <= chunk.end) {
                    checkStop(stop, abort)
                    val want = minOf(buf.size.toLong(), chunk.end - pos + 1).toInt()
                    val n = ins.read(buf, 0, want)
                    if (n < 0) break
                    raf.seek(pos)
                    raf.write(buf, 0, n)
                    pos += n
                    done.addAndGet(i, n.toLong())
                }
            }
            if (done.get(i) < chunk.length) throw IOException("Data terputus sebelum selesai")
        } finally {
            conn.disconnect()
        }
    }

    // --------------------------------------------------------------- stream

    private fun streamDownload(
        probe: Probe,
        referer: String?,
        part: File,
        stop: AtomicBoolean,
        onProgress: (Snapshot) -> Unit,
    ): Outcome {
        var failures = 0
        while (true) {
            if (stop.get()) return Outcome.Stopped
            part.delete()
            val conn = open(probe.url, referer)
            try {
                val code = conn.responseCode
                if (code != 200) {
                    if (isRetryable(code)) throw IOException("HTTP $code")
                    return Outcome.Failed(Reason.HTTP, "HTTP $code")
                }
                var got = 0L
                var lastEmit = 0L
                val buf = ByteArray(32 * 1024)
                FileOutputStream(part).use { out ->
                    conn.inputStream.use { ins ->
                        while (true) {
                            if (stop.get()) return Outcome.Stopped
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 200) {
                                lastEmit = now
                                onProgress(Snapshot(got, probe.total, emptyList(), probe.etag, probe.lastModified, false))
                            }
                        }
                    }
                }
                if (probe.total >= 0 && got != probe.total) throw IOException("Data terputus sebelum selesai")
                onProgress(Snapshot(got, if (probe.total >= 0) probe.total else got, emptyList(), probe.etag, probe.lastModified, false))
                return Outcome.Completed
            } catch (e: IOException) {
                if (++failures > cfg.maxRetries) return Outcome.Failed(Reason.NETWORK, e.message ?: "Koneksi terputus")
                sleepChecked(backoff(failures), stop, null)
            } finally {
                conn.disconnect()
            }
        }
    }

    // -------------------------------------------------------------- helpers

    private fun open(url: String, referer: String?): HttpURLConnection {
        val u = try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw DownloadException(Reason.UNSUPPORTED, "Alamat tidak valid")
        }
        val proto = u.protocol.lowercase()
        if (proto != "http" && proto != "https") throw DownloadException(Reason.UNSUPPORTED, "Skema tidak didukung")
        val c = u.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = cfg.connectTimeoutMs
        c.readTimeout = cfg.readTimeoutMs
        c.setRequestProperty("User-Agent", cfg.userAgent)
        // Hindari gzip agar Content-Length dan Range sesuai dengan byte file sebenarnya.
        c.setRequestProperty("Accept-Encoding", "identity")
        if (!referer.isNullOrBlank()) c.setRequestProperty("Referer", referer)
        return c
    }

    private fun isRetryable(code: Int) = code == 408 || code == 429 || code in 500..599

    private fun backoff(attempt: Int): Long = minOf(cfg.backoffBaseMs shl (attempt - 1).coerceIn(0, 4), 8_000L)

    private fun checkStop(stop: AtomicBoolean, abort: AtomicBoolean?) {
        if (stop.get() || abort?.get() == true) throw StoppedException()
    }

    private fun sleepChecked(ms: Long, stop: AtomicBoolean, abort: AtomicBoolean?) {
        var left = ms
        while (left > 0) {
            checkStop(stop, abort)
            val step = minOf(50L, left)
            try {
                Thread.sleep(step)
            } catch (e: InterruptedException) {
                throw StoppedException()
            }
            left -= step
        }
    }

    private companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        const val MAX_REDIRECTS = 10
        val CONTENT_RANGE = Regex("(?i)bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)")
    }
}
