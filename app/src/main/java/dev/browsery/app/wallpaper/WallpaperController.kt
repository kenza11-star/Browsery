package dev.browsery.app.wallpaper

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class Quality { AUTO, BATTERY, HIGH }

enum class Kind { NONE, IMAGE, GIF, VIDEO }

/**
 * Wallpaper lokal (foto, GIF, video). Salinan privat di filesDir; tanpa izin penyimpanan luas; tidak diunggah.
 * `sharp` = gambar tajam (bingkai pertama untuk GIF/video) yang di-crop ke rasio layar; `blurred` = salinan kecil
 * untuk isi cairan (hanya foto). GIF/video: area cairan berupa kaca tembus pandang tanpa blur (degradasi eksplisit).
 * Tanpa pilihan pengguna, foto bawaan (pemandangan) dipakai; pilihan pengguna tersimpan lintas proses.
 */
class WallpaperController(private val app: Application) {
    private val prefs = app.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
    private val imgFile = File(app.filesDir, "wallpaper.src")
    private val vidFile = File(app.filesDir, "wallpaper.vid")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var blurJob: Job? = null

    var liquid by mutableStateOf(prefs.getBoolean("liquid", true))
        private set
    var animate by mutableStateOf(prefs.getBoolean("animate", true))
        private set
    var coverage by mutableIntStateOf(prefs.getInt("coverage", 35).coerceIn(25, 50))
        private set
    var blurLevel by mutableIntStateOf(prefs.getInt("blur", 60).coerceIn(0, 100))
        private set
    var speed by mutableIntStateOf(prefs.getInt("speed", 40).coerceIn(0, 100))
        private set
    var refresh by mutableIntStateOf(prefs.getInt("refresh", 0))
        private set
    var quality by mutableStateOf(runCatching { Quality.valueOf(prefs.getString("quality", "AUTO").orEmpty()) }.getOrDefault(Quality.AUTO))
        private set

    var kind by mutableStateOf(Kind.NONE)
        private set
    var version by mutableIntStateOf(0)
        private set
    var sharp by mutableStateOf<ImageBitmap?>(null)
        private set
    var blurred by mutableStateOf<ImageBitmap?>(null)
        private set
    var info by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** true bila yang tampil adalah foto bawaan (bukan pilihan pengguna). */
    var isDefault by mutableStateOf(false)
        private set

    /** Fase aliran cairan dan posisi video dipertahankan selama proses hidup agar tidak mengulang dari awal. */
    var phase: Float = 1.2f
    var videoPos: Int = 0

    private var sharpBitmap: Bitmap? = null

    val mediaFile: File get() = if (kind == Kind.VIDEO) vidFile else imgFile

    init {
        val k = runCatching { Kind.valueOf(prefs.getString("kind", "NONE").orEmpty()) }.getOrDefault(Kind.NONE)
        if (k != Kind.NONE && fileFor(k).exists()) reload(k) else loadDefault()
    }

    /** Foto bawaan dibuat sinkron (kecil) agar tidak ada kedipan saat aplikasi dibuka. */
    private fun loadDefault() {
        try {
            val dm = app.resources.displayMetrics
            val sw = minOf(dm.widthPixels, dm.heightPixels)
            val sh = maxOf(dm.widthPixels, dm.heightPixels)
            val bmp = DefaultWallpaper.create(sw, sh)
            sharpBitmap = bmp
            sharp = bmp.asImageBitmap()
            info = "Bawaan: foto pemandangan"
            isDefault = true
            blurred = null
            buildBlur(bmp)
        } catch (e: Exception) {
            isDefault = false
        }
    }

    private fun fileFor(k: Kind) = if (k == Kind.VIDEO) vidFile else imgFile

    fun updateLiquid(v: Boolean) { liquid = v; prefs.edit().putBoolean("liquid", v).apply() }
    fun updateAnimate(v: Boolean) { animate = v; prefs.edit().putBoolean("animate", v).apply() }
    fun updateCoverage(v: Int) { coverage = v.coerceIn(25, 50); prefs.edit().putInt("coverage", coverage).apply() }
    fun updateSpeed(v: Int) { speed = v.coerceIn(0, 100); prefs.edit().putInt("speed", speed).apply() }
    fun updateRefresh(hz: Int) { refresh = hz; prefs.edit().putInt("refresh", hz).apply() }
    fun updateBlur(v: Int) {
        blurLevel = v.coerceIn(0, 100)
        prefs.edit().putInt("blur", blurLevel).apply()
        rebuildBlur()
    }
    fun updateQuality(q: Quality) {
        quality = q
        prefs.edit().putString("quality", q.name).apply()
        rebuildBlur()
    }

    /** Hemat daya: GIF/video tidak digerakkan (bingkai pertama dipakai). */
    fun mediaMayAnimate(): Boolean = animate && effectiveQuality() != Quality.BATTERY

    /** Batas frame per detik animasi cairan. 120 FPS hanyalah target perangkat, tidak dijanjikan. */
    fun fpsCap(): Int = when (effectiveQuality()) {
        Quality.BATTERY -> 30
        Quality.HIGH -> if (refresh > 0) refresh else 120
        Quality.AUTO -> 60
    }

    private fun effectiveQuality(): Quality {
        if (quality != Quality.AUTO) return quality
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (pm?.isPowerSaveMode == true) Quality.BATTERY else Quality.AUTO
    }

    /** Salin media pilihan pengguna ke penyimpanan privat lalu decode. */
    fun import(uri: Uri) {
        busy = true
        error = null
        scope.launch {
            val tmp = File(app.filesDir, "wallpaper.tmp")
            try {
                val mime = app.contentResolver.getType(uri).orEmpty()
                val k = when {
                    mime.startsWith("video/") -> Kind.VIDEO
                    mime == "image/gif" -> Kind.GIF
                    else -> Kind.IMAGE
                }
                val cap = if (k == Kind.VIDEO) MAX_VIDEO else MAX_IMAGE
                app.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { out ->
                        var total = 0L
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > cap) throw IllegalStateException("Berkas terlalu besar")
                            out.write(buf, 0, n)
                        }
                    }
                } ?: throw IllegalStateException("Berkas tidak dapat dibuka")
                if (decodeFrom(tmp, k)) {
                    imgFile.delete()
                    vidFile.delete()
                    tmp.copyTo(fileFor(k), overwrite = true)
                    kind = k
                    version++
                    prefs.edit().putString("kind", k.name).apply()
                } else {
                    error = "Format media tidak didukung perangkat ini."
                }
            } catch (e: OutOfMemoryError) {
                error = "Memori tidak cukup untuk media ini."
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat media."
            } finally {
                tmp.delete()
                busy = false
            }
        }
    }

    fun reset() {
        blurJob?.cancel()
        isDefault = false
        imgFile.delete()
        vidFile.delete()
        prefs.edit().putString("kind", "NONE").apply()
        kind = Kind.NONE
        version++
        sharp = null
        blurred = null
        sharpBitmap = null
        info = null
        error = null
        videoPos = 0
        loadDefault()
    }

    /** Dipanggil bila pemutar video/GIF gagal saat dipakai. */
    fun fail(message: String) {
        reset()
        error = message
    }

    private fun reload(k: Kind) {
        scope.launch {
            try {
                if (decodeFrom(fileFor(k), k)) {
                    kind = k
                    version++
                } else {
                    fileFor(k).delete()
                    error = "Wallpaper tersimpan tidak dapat dibaca; kembali ke bawaan."
                    loadDefault()
                }
            } catch (e: OutOfMemoryError) {
                error = "Memori tidak cukup untuk wallpaper."
                loadDefault()
            } catch (e: Exception) {
                fileFor(k).delete()
                error = "Wallpaper tersimpan rusak; kembali ke bawaan."
                loadDefault()
            }
        }
    }

    private fun decodeFrom(src: File, k: Kind): Boolean {
        val dm = app.resources.displayMetrics
        val sw = minOf(dm.widthPixels, dm.heightPixels)
        val sh = maxOf(dm.widthPixels, dm.heightPixels)
        var ow = 0
        var oh = 0
        val bmp: Bitmap
        if (k == Kind.VIDEO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(src.path)
                ow = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                oh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) {
                    val t = ow
                    ow = oh
                    oh = t
                }
                bmp = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return false
            } finally {
                r.release()
            }
        } else if (Build.VERSION.SDK_INT >= 28) {
            bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(src)) { dec, i, _ ->
                ow = i.size.width
                oh = i.size.height
                dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                dec.setTargetSampleSize(sampleSizeFor(ow, oh, sw, sh))
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.path, o)
            ow = o.outWidth
            oh = o.outHeight
            val o2 = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(ow, oh, sw, sh) }
            bmp = BitmapFactory.decodeFile(src.path, o2) ?: return false
        }
        // Crop tengah ke rasio layar potret agar memori kecil; sumber asli tetap tersimpan di berkas.
        val targetRatio = sw.toFloat() / sh.toFloat()
        val ratio = bmp.width.toFloat() / bmp.height.toFloat()
        var cropped = bmp
        if (ratio > targetRatio) {
            val cw = (bmp.height * targetRatio).toInt().coerceAtLeast(1)
            cropped = Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, 0, cw, bmp.height)
        } else if (ratio < targetRatio) {
            val ch = (bmp.width / targetRatio).toInt().coerceAtLeast(1)
            cropped = Bitmap.createBitmap(bmp, 0, (bmp.height - ch) / 2, bmp.width, ch)
        }
        if (cropped !== bmp) bmp.recycle()
        isDefault = false
        videoPos = 0
        sharpBitmap = cropped
        sharp = cropped.asImageBitmap()
        val label = when (k) {
            Kind.VIDEO -> "Video"
            Kind.GIF -> "GIF"
            else -> "Foto"
        }
        info = "$label asli ${ow}×${oh} px · dipakai ${cropped.width}×${cropped.height} px"
        blurred = null
        buildBlur(cropped)
        return true
    }

    private fun rebuildBlur() {
        val b = sharpBitmap ?: return
        blurJob?.cancel()
        blurJob = scope.launch { buildBlur(b) }
    }

    /** Buram murah: turunkan ukuran bertahap lalu di-upscale saat digambar. Tanpa RenderEffect, jalan di semua API. */
    private fun buildBlur(src: Bitmap) {
        val q = effectiveQuality()
        val divisor = 8 + blurLevel * 24 / 100 + if (q == Quality.BATTERY) 8 else 0
        var w = src.width
        var h = src.height
        val tw = (src.width / divisor).coerceAtLeast(16)
        val th = (src.height / divisor).coerceAtLeast(16)
        var cur = src
        while (w / 2 > tw && h / 2 > th) {
            w /= 2
            h /= 2
            val next = Bitmap.createScaledBitmap(cur, w, h, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val out = Bitmap.createScaledBitmap(cur, tw, th, true)
        if (cur !== src && cur !== out) cur.recycle()
        blurred = out.asImageBitmap()
    }

    private companion object {
        const val MAX_IMAGE = 80L * 1024 * 1024
        const val MAX_VIDEO = 150L * 1024 * 1024
    }
}
