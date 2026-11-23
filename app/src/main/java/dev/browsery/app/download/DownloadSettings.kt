package dev.browsery.app.download

import android.content.Context

/** Pengaturan unduhan sederhana (SharedPreferences). Stage 6 akan memindahkannya ke pusat pengaturan. */
class DownloadSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("download_settings", Context.MODE_PRIVATE)

    /** 0 = otomatis, selain itu jumlah koneksi paralel per berkas. */
    var connections: Int
        get() = prefs.getInt("connections", 0).takeIf { it in ALLOWED } ?: 0
        set(v) {
            prefs.edit().putInt("connections", if (v in ALLOWED) v else 0).apply()
        }

    /** Jumlah berkas yang diunduh bersamaan (terpisah dari koneksi paralel per berkas). */
    val maxConcurrent: Int get() = prefs.getInt("max_concurrent", 3).coerceIn(1, 5)

    companion object {
        val ALLOWED = listOf(0, 1, 2, 4, 8)
    }
}
