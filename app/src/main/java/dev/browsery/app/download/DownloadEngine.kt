package dev.browsery.app.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

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

private class Redirect(val to: String)

/**
 * Engine unduhan berbasis OkHttp, blocking, dipanggil dari thread latar.
 *
 * Mengapa OkHttp dengan HTTP/1.1 dipaksa: dengan HTTP/2, semua permintaan ke satu host dimultipleks di SATU koneksi TCP,
 * sehingga "8 koneksi paralel" tidak menambah kecepatan sama sekali. Dengan HTTP/1.1 tiap permintaan Range memakai
 * koneksi TCP sendiri, jadi paralelisme benar-benar terjadi. Koneksi dipakai ulang (keep-alive) antarsegmen.
 *
 * - Server mendukung Range (206 + Content-Range valid + ukuran diketahui): file dibagi menjadi segmen kecil yang
 *   diambil bergantian oleh N pekerja (lihat [Chunks.planSegments]).
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
        val bufferBytes: Int = 64 * 1024,
        val userAgent: String = "Mozilla/5.0 (Android 14; Mobile; rv:159.0) Gecko/159.0 Firefox/159.0",
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .connectTimeout(cfg.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(cfg.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .connectionPool(ConnectionPool(32, 60, TimeUnit.SECONDS))
        .build()

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
            val req = request(current, referer).newBuilder().header("Range", "bytes=0-0").build()
            try {
                val r: Any = client.newCall(req).execute().use { resp ->
                    val code = resp.code
                    if (code in REDIRECTS) {
                        val loc = resp.header("Location")
                            ?: throw DownloadException(Reason.HTTP, "Pengalihan tanpa Location")
                        val next = resp.request.url.resolve(loc)?.toString()
                            ?: throw DownloadException(Reason.HTTP, "Alamat pengalihan tidak valid")
                        return@use Redirect(next)
                    }
                    val etag = resp.header("ETag")
                    val lm = resp.header("Last-Modified")
                    val cd = resp.header("Content-Disposition")
                    val ct = resp.header("Content-Type")
                    when {
                        code == 206 -> {
                            val m = CONTENT_RANGE.matchEntire(resp.header("Content-Range")?.trim().orEmpty())
                            val total = m?.groupValues?.get(3)?.toLongOrNull() ?: -1L
                            Probe(current, total, total > 0, etag, lm, cd, ct)
                        }
                        code == 200 -> Probe(current, resp.body?.contentLength() ?: -1L, false, etag, lm, cd, ct)
                        code == 416 -> Probe(current, 0L, false, etag, lm, cd, ct)
                        isRetryable(code) -> throw IOException("HTTP $code")
                        else -> throw DownloadException(Reason.HTTP, "HTTP $code")
                    }
                }
                if (r is Probe) return r
                if (++redirects > MAX_REDIRECTS) throw DownloadException(Reason.HTTP, "Terlalu banyak pengalihan")
                current = (r as Redirect).to
            } catch (e: IOException) {
                if (++failures > cfg.maxRetries) {
                    throw DownloadException(Reason.NETWORK, e.message ?: "Gagal terhubung")
                }
                sleepChecked(backoff(failures), stop, null)
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
        val workers = when {
            connections == 1 || probe.total < cfg.minParallelBytes -> 1
            connections <= 0 -> Chunks.autoConnections(probe.total)
            else -> connections
        }
        val plan: List<Chunk> = if (usable && resume != null) {
            resume.chunks
        } else {
            part.delete()
            Chunks.planSegments(probe.total, workers)
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
        val calls = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Call, Boolean>())
        val queue = ConcurrentLinkedQueue(plan.indices.filter { !plan[it].isComplete })
        val threads = (0 until minOf(workers, queue.size)).map { w ->
            Thread({
                try {
                    RandomAccessFile(part, "rw").use { raf ->
                        while (true) {
                            val i = queue.poll() ?: break
                            runChunk(i, plan[i], probe, referer, raf, done, stop, abort, calls)
                        }
                    }
                } catch (e: StoppedException) {
                    // berhenti karena pause/cancel atau karena pekerja lain gagal
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                    abort.set(true)
                }
            }, "browsery-dl-$w").also { it.start() }
        }
        while (threads.any { it.isAlive }) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                stop.set(true)
            }
            // Batalkan permintaan yang sedang menunggu agar jeda/batal terasa seketika, bukan menunggu timeout baca.
            if (stop.get() || abort.get()) calls.forEach { it.cancel() }
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
        raf: RandomAccessFile,
        done: AtomicLongArray,
        stop: AtomicBoolean,
        abort: AtomicBoolean,
        calls: MutableSet<Call>,
    ) {
        var failures = 0
        while (done.get(i) < chunk.length) {
            checkStop(stop, abort)
            val before = done.get(i)
            try {
                fetchRange(probe, referer, chunk, i, raf, done, stop, abort, calls)
            } catch (e: IOException) {
                // Pembatalan karena jeda/batal bukan kegagalan jaringan.
                checkStop(stop, abort)
                if (done.get(i) > before) failures = 0
                failures++
                if (failures > cfg.maxRetries) {
                    throw DownloadException(Reason.NETWORK, e.message ?: "Koneksi terputus")
                }
                sleepChecked(backoff(failures), stop, abort)
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
        calls: MutableSet<Call>,
    ) {
        val from = chunk.start + done.get(i)
        val b = request(probe.url, referer).newBuilder().header("Range", "bytes=$from-${chunk.end}")
        ifRange(probe)?.let { b.header("If-Range", it) }
        val call = client.newCall(b.build())
        calls.add(call)
        try {
            call.execute().use { resp ->
                val code = resp.code
                when {
                    code == 206 -> Unit
                    code == 200 || code == 416 ->
                        throw DownloadException(Reason.CHANGED, "Berkas berubah atau server mengabaikan Range")
                    isRetryable(code) -> throw IOException("HTTP $code")
                    else -> throw DownloadException(Reason.HTTP, "HTTP $code")
                }
                val cr = CONTENT_RANGE.matchEntire(resp.header("Content-Range")?.trim().orEmpty())
                    ?: throw DownloadException(Reason.CHANGED, "Content-Range tidak valid")
                val rangeStart = cr.groupValues[1].toLong()
                val rangeTotal = cr.groupValues[3].toLongOrNull()
                if (rangeStart != from || (rangeTotal != null && rangeTotal != probe.total)) {
                    throw DownloadException(Reason.CHANGED, "Content-Range tidak cocok dengan permintaan")
                }
                val body = resp.body ?: throw IOException("Respons tanpa isi")
                var pos = from
                val buf = ByteArray(cfg.bufferBytes)
                body.byteStream().use { ins ->
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
            }
        } finally {
            calls.remove(call)
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
            val call = client.newCall(request(probe.url, referer))
            try {
                call.execute().use { resp ->
                    val code = resp.code
                    if (code != 200) {
                        if (isRetryable(code)) throw IOException("HTTP $code")
                        return Outcome.Failed(Reason.HTTP, "HTTP $code")
                    }
                    val body = resp.body ?: throw IOException("Respons tanpa isi")
                    var got = 0L
                    var lastEmit = 0L
                    val buf = ByteArray(cfg.bufferBytes)
                    FileOutputStream(part).use { out ->
                        body.byteStream().use { ins ->
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
                    onProgress(
                        Snapshot(got, if (probe.total >= 0) probe.total else got, emptyList(), probe.etag, probe.lastModified, false),
                    )
                    return Outcome.Completed
                }
            } catch (e: IOException) {
                if (++failures > cfg.maxRetries) return Outcome.Failed(Reason.NETWORK, e.message ?: "Koneksi terputus")
                sleepChecked(backoff(failures), stop, null)
            }
        }
    }

    // -------------------------------------------------------------- helpers

    private fun request(url: String, referer: String?): Request {
        val http = url.toHttpUrlOrNull() ?: throw DownloadException(Reason.UNSUPPORTED, "Alamat atau skema tidak didukung")
        val b = Request.Builder()
            .url(http)
            .header("User-Agent", cfg.userAgent)
            // Hindari gzip agar Content-Length dan Range sesuai dengan byte file sebenarnya.
            .header("Accept-Encoding", "identity")
        if (!referer.isNullOrBlank()) b.header("Referer", referer)
        return b.build()
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
