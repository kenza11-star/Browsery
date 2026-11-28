package dev.browsery.app.engine

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Gambar mini tab dari piksel halaman nyata (GeckoView.capturePixels), diambil saat pengguna meninggalkan halaman
 * menuju daftar tab. Hanya di memori (tidak ditulis ke disk, tidak disinkronkan), sehingga tab privat tetap privat.
 * Kunci = GeckoSession (lemah): hilang otomatis saat tab ditutup.
 */
object ThumbnailStore {
    private val map = WeakHashMap<GeckoSession, Bitmap>()
    private val edges = WeakHashMap<GeckoSession, IntArray>()
    private var viewRef: WeakReference<GeckoView>? = null

    var version by mutableIntStateOf(0)
        private set

    fun attach(v: GeckoView) {
        viewRef = WeakReference(v)
    }

    fun detach(v: GeckoView) {
        if (viewRef?.get() === v) viewRef = null
    }

    fun get(session: GeckoSession?): ImageBitmap? {
        if (session == null) return null
        return map[session]?.asImageBitmap()
    }

    /** Warna rata-rata tepi atas dan tepi bawah halaman (dari tangkapan terakhir), untuk menyamakan warna bar. */
    fun edgeColors(session: GeckoSession?): Pair<Color, Color>? {
        if (session == null) return null
        val e = edges[session] ?: return null
        return Color(e[0]) to Color(e[1])
    }

    private fun rowAverage(b: Bitmap, y0: Int, y1: Int): Int {
        var r = 0L
        var g = 0L
        var bl = 0L
        var n = 0L
        for (y in y0..y1) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                bl += p and 0xFF
                n++
                x += 3
            }
        }
        if (n == 0L) return 0xFF000000.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }

    /** Lepas referensi gambar mini (tanpa recycle: mungkin masih tergambar) saat memori sistem menipis; dibuat ulang saat dibutuhkan. */
    fun trim() {
        map.clear()
        edges.clear()
        version++
    }

    /** Ambil gambar mini halaman yang sedang tampil. Aman dipanggil kapan saja; gagal = diabaikan. */
    fun captureActive() {
        val v = viewRef?.get() ?: return
        if (!v.isAttachedToWindow || v.width <= 0 || v.height <= 0) return
        val session = v.session ?: return
        try {
            v.capturePixels().accept(
                { bmp ->
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        val w = 360
                        val h = (bmp.height * (w.toFloat() / bmp.width)).toInt().coerceAtLeast(1)
                        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
                        if (small !== bmp) bmp.recycle()
                        map[session] = small
                        edges[session] = intArrayOf(
                            rowAverage(small, 0, minOf(1, small.height - 1)),
                            rowAverage(small, maxOf(0, small.height - 2), small.height - 1),
                        )
                        version++
                    }
                },
                { _ -> },
            )
        } catch (e: Exception) {
            // thumbnail hanya hiasan; kegagalan tidak boleh mengganggu navigasi
        }
    }
}
