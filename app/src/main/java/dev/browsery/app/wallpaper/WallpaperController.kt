package dev.browsery.app.wallpaper

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

/**
 * Wallpaper foto lokal. Salinan privat di filesDir (tanpa izin penyimpanan luas, tidak diunggah ke mana pun).
 * Dua bitmap: `sharp` (tajam, di-crop ke rasio layar) dan `blurred` (salinan kecil yang di-upscale = buram murah),
 * yang HANYA dipakai di dalam mask cairan. GIF/video belum didukung: GIF dibaca sebagai gambar diam.
 */
class WallpaperController(private val app: Application) {
    private val prefs = app.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
    private val file = File(app.filesDir, "wallpaper.src")
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
    var quality by mutableStateOf(runCatching { Quality.valueOf(prefs.getString("quality", "AUTO").orEmpty()) }.getOrDefault(Quality.AUTO))
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

    private var sharpBitmap: Bitmap? = null

    init {
        if (file.exists()) reload()
    }

    fun updateLiquid(v: Boolean) { liquid = v; prefs.edit().putBoolean("liquid", v).apply() }
    fun updateAnimate(v: Boolean) { animate = v; prefs.edit().putBoolean("animate", v).apply() }
    fun updateCoverage(v: Int) { coverage = v.coerceIn(25, 50); prefs.edit().putInt("coverage", coverage).apply() }
    fun updateSpeed(v: Int) { speed = v.coerceIn(0, 100); prefs.edit().putInt("speed", speed).apply() }
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

    /** Batas frame per detik untuk animasi cairan. 120 FPS hanyalah target perangkat, tidak dijanjikan. */
    fun fpsCap(): Int = when (effectiveQuality()) {
        Quality.BATTERY -> 30
        Quality.HIGH -> 120
        Quality.AUTO -> 60
    }

    private fun effectiveQuality(): Quality {
        if (quality != Quality.AUTO) return quality
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (pm?.isPowerSaveMode == true) Quality.BATTERY else Quality.AUTO
    }

    /** Salin gambar pilihan pengguna ke penyimpanan privat lalu decode ulang. */
    fun import(uri: Uri) {
        busy = true
        error = null
        scope.launch {
            try {
                val tmp = File(app.filesDir, "wallpaper.tmp")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { out ->
                        var total = 0L
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) throw IllegalStateException("Berkas terlalu besar")
                            out.write(buf, 0, n)
                        }
                    }
                } ?: throw IllegalStateException("Berkas tidak dapat dibuka")
                val ok = decodeFrom(tmp)
                if (ok) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                } else {
                    tmp.delete()
                    error = "Format gambar tidak didukung perangkat ini."
                }
            } catch (e: OutOfMemoryError) {
                error = "Memori tidak cukup untuk gambar ini."
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat gambar."
            } finally {
                busy = false
            }
        }
    }

    fun reset() {
        blurJob?.cancel()
        file.delete()
        sharp = null
        blurred = null
        sharpBitmap = null
        info = null
        error = null
    }

    private fun reload() {
        scope.launch {
            try {
                if (!decodeFrom(file)) {
                    file.delete()
                    error = "Wallpaper tersimpan tidak dapat dibaca; kembali ke bawaan."
                }
            } catch (e: OutOfMemoryError) {
                error = "Memori tidak cukup untuk wallpaper."
            } catch (e: Exception) {
                file.delete()
                error = "Wallpaper tersimpan rusak; kembali ke bawaan."
            }
        }
    }

    private fun decodeFrom(src: File): Boolean {
        val dm = app.resources.displayMetrics
        val sw = minOf(dm.widthPixels, dm.heightPixels)
        val sh = maxOf(dm.widthPixels, dm.heightPixels)
        var ow = 0
        var oh = 0
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(src)) { dec, i, _ ->
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
            BitmapFactory.decodeFile(src.path, o2) ?: return false
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
        sharpBitmap = cropped
        sharp = cropped.asImageBitmap()
        info = "Asli ${ow}×${oh} px · dipakai ${cropped.width}×${cropped.height} px"
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
        const val MAX_BYTES = 80L * 1024 * 1024
    }
}
