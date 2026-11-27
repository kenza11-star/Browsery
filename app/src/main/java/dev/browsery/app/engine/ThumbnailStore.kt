package dev.browsery.app.engine

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
