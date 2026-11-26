package dev.browsery.app.wallpaper

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometri "cairan": satu pita organik lebar yang mengalir pelan. Murni matematika (mudah diuji).
 * Tebal rata-rata pita = coverage * tinggi layar; variasi tebal memakai tepat 1 periode sinus pada lebar layar
 * sehingga rata-ratanya nol. Pita selalu berada di dalam layar secara vertikal, jadi luas terlihat = coverage.
 */
class LiquidBand(val w: Float, val h: Float, val coverage: Float) {
    private val thick0 = coverage * h
    private val a1 = 0.06f * h
    private val a2 = 0.03f * h
    private val drift = max(0f, min(0.2f * h, h / 2f - 0.6f * thick0 - a1 - a2 - 1f))

    private fun u(x: Float) = (x / w) * TWO_PI

    fun mid(x: Float, p: Float): Float =
        h / 2f + drift * sin(p * 0.37f + 0.8f) + a1 * sin(u(x) + p * 0.9f) + a2 * sin(2f * u(x) - p * 0.6f + 1.3f)

    fun thick(x: Float, p: Float): Float = thick0 * (1f + 0.2f * sin(u(x) + p * 0.5f + 2f))

    fun top(x: Float, p: Float) = mid(x, p) - thick(x, p) / 2f

    fun bottom(x: Float, p: Float) = mid(x, p) + thick(x, p) / 2f

    /** Luas pita yang terlihat (dipotong ke layar) dibagi luas layar, dihitung numerik. */
    fun areaFraction(p: Float, samples: Int = 1024): Float {
        var sum = 0.0
        for (i in 0 until samples) {
            val x = (i + 0.5f) / samples * w
            val t = top(x, p).coerceIn(0f, h)
            val b = bottom(x, p).coerceIn(0f, h)
            sum += (b - t)
        }
        return (sum / samples / h).toFloat()
    }

    private companion object {
        const val TWO_PI = 6.2831855f
    }
}

/** Ukuran sampel decode: terbesar (pangkat 2) yang masih >= ukuran layar pada kedua sisi. */
fun sampleSizeFor(w: Int, h: Int, screenW: Int, screenH: Int): Int {
    var s = 1
    while (w / (s * 2) >= screenW && h / (s * 2) >= screenH) s *= 2
    return s
}
