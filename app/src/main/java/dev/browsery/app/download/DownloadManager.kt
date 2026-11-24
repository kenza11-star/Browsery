package dev.browsery.app.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.browsery.app.R
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.DownloadEntity
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class DownloadState { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

data class DownloadRequest(val url: String, val referer: String?, val fileName: String, val mimeType: String?)

/** Progres langsung (tidak disimpan ke DB setiap saat). */
data class Live(val downloaded: Long, val total: Long, val speed: Long)

data class Summary(val count: Int, val downloaded: Long, val total: Long)

private enum class StopReason { NONE, PAUSE, REMOVE }

private class Control {
    val stop = AtomicBoolean(false)
    val started = AtomicBoolean(false)

    @Volatile var reason = StopReason.NONE

    @Volatile var job: Job? = null
}

/**
 * Pengelola unduhan tingkat aplikasi (satu instans per proses). Semua operasi basis data berjalan di
 * Dispatchers.IO. Batas berkas bersamaan ([DownloadSettings.maxConcurrent]) terpisah dari jumlah koneksi
 * paralel untuk satu berkas.
 */
class DownloadManager(context: Context, db: AppDatabase) {
    private val app = context.applicationContext
    private val dao = db.downloadDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = DownloadEngine()
    private val main = Handler(Looper.getMainLooper())
    private val controls = ConcurrentHashMap<Long, Control>()
    private val started = AtomicBoolean(false)

    val settings = DownloadSettings(app)
    private val gate = Semaphore(settings.maxConcurrent)

    /** Progres langsung untuk UI (Compose membaca map ini). */
    val live = mutableStateMapOf<Long, Live>()

    val items: Flow<List<DownloadEntity>> get() = dao.observeAll()

    /** Dipanggil di thread utama setiap ada unduhan baru (dipakai untuk meminta izin notifikasi sekali). */
    var onFirstEnqueue: (() -> Unit)? = null

    /** Lanjutkan unduhan yang terputus karena proses dihentikan (sekali per proses). */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            dao.active().forEach { e ->
                dao.update(e.copy(state = DownloadState.QUEUED.name))
                launchJob(e.id)
            }
        }
    }

    fun enqueue(req: DownloadRequest) {
        scope.launch {
            val id = dao.insert(
                DownloadEntity(
                    url = req.url,
                    referer = req.referer,
                    fileName = FileNames.sanitize(req.fileName),
                    mimeType = req.mimeType,
                    state = DownloadState.QUEUED.name,
                    totalBytes = -1,
                    downloadedBytes = 0,
                    etag = null,
                    lastModified = null,
                    acceptRanges = false,
                    chunks = null,
                    error = null,
                    filePath = null,
                    contentUri = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            launchJob(id)
        }
        main.post { onFirstEnqueue?.invoke() }
    }

    fun pause(id: Long) {
        scope.launch {
            val ctl = controls[id] ?: return@launch
            ctl.reason = StopReason.PAUSE
            ctl.stop.set(true)
            if (!ctl.started.get()) {
                ctl.job?.cancel()
                dao.get(id)?.let { dao.update(it.copy(state = DownloadState.PAUSED.name)) }
            }
        }
    }

    fun pauseAll() {
        controls.keys.forEach { pause(it) }
    }

    fun resume(id: Long) {
        scope.launch {
            val e = dao.get(id) ?: return@launch
            val st = stateOf(e)
            if (st == DownloadState.PAUSED || st == DownloadState.FAILED) {
                dao.update(e.copy(state = DownloadState.QUEUED.name, error = null))
                launchJob(id)
            }
        }
    }

    /** Menghapus catatan; berkas parsial dan (bila sudah selesai) berkas hasil unduhan ikut dihapus. */
    fun remove(id: Long) {
        scope.launch {
            val ctl = controls[id]
            if (ctl == null) {
                deleteRecord(id)
            } else {
                ctl.reason = StopReason.REMOVE
                ctl.stop.set(true)
                if (!ctl.started.get()) {
                    ctl.job?.cancel()
                    deleteRecord(id)
                }
            }
        }
    }

    /** Hanya membersihkan daftar (selesai/gagal). Berkas yang sudah tersimpan tidak dihapus. */
    fun clearFinished() {
        scope.launch {
            dao.finished().forEach { partFile(it.id).delete() }
            dao.clearFinished()
        }
    }

    fun summary(): Summary {
        var done = 0L
        var total = 0L
        controls.keys.forEach { id ->
            live[id]?.let { l ->
                done += l.downloaded
                if (l.total > 0) total += l.total
            }
        }
        return Summary(controls.size, done, total)
    }

    fun open(e: DownloadEntity): Boolean {
        val uri: Uri = try {
            e.contentUri?.let { Uri.parse(it) }
                ?: e.filePath?.let { FileProvider.getUriForFile(app, "${app.packageName}.files", File(it)) }
        } catch (ex: Exception) {
            null
        } ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, e.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            app.startActivity(intent)
            true
        } catch (ex: Exception) {
            false
        }
    }

    // ------------------------------------------------------------ internal

    private fun stateOf(e: DownloadEntity): DownloadState =
        runCatching { DownloadState.valueOf(e.state) }.getOrDefault(DownloadState.FAILED)

    private fun partFile(id: Long) = File(app.getExternalFilesDir(null) ?: app.filesDir, "partial/$id.part")

    private fun launchJob(id: Long) {
        val ctl = Control()
        if (controls.putIfAbsent(id, ctl) != null) return
        ensureService()
        ctl.job = scope.launch {
            try {
                gate.withPermit {
                    ctl.started.set(true)
                    execute(id, ctl)
                }
            } finally {
                controls.remove(id, ctl)
                live.remove(id)
            }
        }
    }

    private fun ensureService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        } catch (e: Exception) {
            // Tidak boleh memulai service latar depan (mis. aplikasi di latar belakang): unduhan tetap berjalan
            // selama proses hidup, tetapi tanpa notifikasi.
        }
    }

    private fun execute(id: Long, ctl: Control) {
        val e0 = dao.get(id) ?: return
        if (ctl.stop.get()) {
            finishStopped(e0, ctl)
            return
        }
        var cur = e0.copy(state = DownloadState.RUNNING.name, error = null)
        dao.update(cur)
        val part = partFile(id)
        val resume = if (cur.acceptRanges && cur.totalBytes > 0) {
            Chunks.decode(cur.chunks).takeIf { it.isNotEmpty() }
                ?.let { Resume(cur.totalBytes, cur.etag, cur.lastModified, it) }
        } else {
            null
        }
        var latest: Snapshot? = null
        var lastPersist = System.currentTimeMillis()
        var lastBytes = cur.downloadedBytes
        var lastTime = System.nanoTime()
        var speed = 0L

        val outcome = engine.download(
            url = cur.url,
            referer = cur.referer,
            part = part,
            connections = settings.connections,
            resume = resume,
            stop = ctl.stop,
            onProbe = { p ->
                val headerName = FileNames.fromContentDisposition(p.contentDisposition)?.let { FileNames.sanitize(it) }
                val probeMime = p.contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                val currentMime = cur.mimeType?.takeIf { it.isNotBlank() && it != OCTET }
                val name = headerName ?: cur.fileName
                val mime = currentMime ?: probeMime?.takeIf { it != OCTET } ?: guessMime(name) ?: cur.mimeType ?: probeMime
                cur = cur.copy(
                    fileName = name,
                    mimeType = mime,
                    totalBytes = p.total,
                    etag = p.etag,
                    lastModified = p.lastModified,
                    acceptRanges = p.acceptRanges,
                )
                dao.update(cur)
            },
            onProgress = { s ->
                latest = s
                val now = System.nanoTime()
                val dt = now - lastTime
                if (dt >= 500_000_000L) {
                    val inst = (s.downloaded - lastBytes) * 1_000_000_000L / dt
                    speed = if (speed == 0L) inst else (speed * 6 + inst * 4) / 10
                    lastBytes = s.downloaded
                    lastTime = now
                }
                live[id] = Live(s.downloaded, s.total, speed)
                val wall = System.currentTimeMillis()
                if (wall - lastPersist >= 2_000) {
                    lastPersist = wall
                    cur = withProgress(cur, s)
                    dao.update(cur)
                }
            },
        )
        val snap = latest
        if (snap != null) cur = withProgress(cur, snap)

        when (outcome) {
            Outcome.Completed -> complete(cur, part, snap)
            Outcome.Stopped -> finishStopped(cur, ctl)
            is Outcome.Failed -> {
                dao.update(cur.copy(state = DownloadState.FAILED.name, error = errorText(outcome)))
            }
        }
    }

    private fun withProgress(e: DownloadEntity, s: Snapshot): DownloadEntity = e.copy(
        downloadedBytes = s.downloaded,
        totalBytes = if (s.total > 0) s.total else e.totalBytes,
        chunks = if (s.chunks.isEmpty()) null else Chunks.encode(s.chunks),
    )

    private fun finishStopped(e: DownloadEntity, ctl: Control) {
        if (ctl.reason == StopReason.REMOVE) {
            deleteRecord(e.id)
        } else {
            dao.update(e.copy(state = DownloadState.PAUSED.name))
        }
    }

    private fun complete(e: DownloadEntity, part: File, snap: Snapshot?) {
        val size = snap?.downloaded ?: part.length()
        try {
            val (uri, path) = publish(e, part)
            dao.update(
                e.copy(
                    state = DownloadState.COMPLETED.name,
                    totalBytes = size,
                    downloadedBytes = size,
                    chunks = null,
                    error = null,
                    contentUri = uri,
                    filePath = path,
                ),
            )
        } catch (ex: Exception) {
            dao.update(e.copy(state = DownloadState.FAILED.name, error = app.getString(R.string.dl_err_save)))
        }
    }

    private fun publish(e: DownloadEntity, part: File): Pair<String?, String?> {
        val name = FileNames.sanitize(e.fileName)
        val mime = e.mimeType ?: OCTET
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Browsery")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore insert gagal")
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("Tidak bisa membuka keluaran")
                out.use { o -> part.inputStream().use { it.copyTo(o) } }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            } catch (ex: Exception) {
                try {
                    resolver.delete(uri, null, null)
                } catch (ignored: Exception) {
                    // best effort
                }
                throw ex
            }
            part.delete()
            return uri.toString() to null
        }
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(app.filesDir, "downloads")
        dir.mkdirs()
        val target = File(dir, FileNames.unique(name) { File(dir, it).exists() })
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = false)
            part.delete()
        }
        return null to target.absolutePath
    }

    private fun deleteRecord(id: Long) {
        val e = dao.get(id)
        partFile(id).delete()
        if (e != null) {
            try {
                e.contentUri?.let { app.contentResolver.delete(Uri.parse(it), null, null) }
                e.filePath?.let { File(it).delete() }
            } catch (ex: Exception) {
                // best effort: catatan tetap dihapus
            }
            dao.delete(id)
        }
        live.remove(id)
    }

    private fun errorText(f: Outcome.Failed): String = when (f.reason) {
        Reason.HTTP -> app.getString(R.string.dl_err_http, f.message)
        Reason.NETWORK -> app.getString(R.string.dl_err_network)
        Reason.CHANGED -> app.getString(R.string.dl_err_changed)
        Reason.IO -> app.getString(R.string.dl_err_io)
        Reason.UNSUPPORTED -> app.getString(R.string.dl_err_unsupported)
        Reason.SIZE_MISMATCH -> app.getString(R.string.dl_err_size)
    }

    private fun guessMime(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext.isEmpty()) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private companion object {
        const val OCTET = "application/octet-stream"
    }
}
