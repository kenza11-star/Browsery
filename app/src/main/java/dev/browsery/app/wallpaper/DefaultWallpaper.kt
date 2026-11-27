package dev.browsery.app.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import java.util.Random

/**
 * Foto bawaan: pemandangan padang rumput di bawah planet biru. Sumbernya versi kecil (144x256) yang disematkan
 * dalam kode, diperbesar halus ke ukuran layar, lalu bintang dan butir cahaya digambar ulang secara prosedural
 * (deterministik) agar tetap tajam. Ini rekonstruksi, bukan berkas foto resolusi penuh.
 */
object DefaultWallpaper {
    private val B64: String = listOf(
        "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAwICQsJCAwLCgsODQwOEh4UEhEREiUbHBYeLCcuLisnKyoxN0Y7MTRCNCorPVM+QkhKTk9OLztWXFVMW0ZNTkv/2wBDAQ0ODhIQEiQUFCRLMisy",
        "S0tL".repeat(16),
        "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",
        "eB3UTlgJ8pCE8YS4GOQmfcLHZSAtxCYz/9k=",
    ).joinToString("")

    fun create(sw: Int, sh: Int): Bitmap {
        val raw = Base64.decode(B64, Base64.DEFAULT)
        val src = BitmapFactory.decodeByteArray(raw, 0, raw.size)
        val base = if (src != null) {
            Bitmap.createScaledBitmap(src, sw, sh, true)
        } else {
            Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.parseColor("#06101C")) }
        }
        val bmp = base.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bmp)
        val rnd = Random(7)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = sw.toFloat()
        val h = sh.toFloat()
        val horizon = h * 0.68f
        // Bintang hanya di langit gelap (kiri garis tepi planet).
        for (i in 0 until 320) {
            val y = rnd.nextFloat() * horizon * 0.98f
            val limb = w * (1.0f - 0.55f * (y / horizon))
            val x = rnd.nextFloat() * (limb - w * 0.06f)
            if (x < 0f) continue
            val big = rnd.nextFloat() < 0.04f
            val r = if (big) 1.6f + rnd.nextFloat() * 1.4f else 0.5f + rnd.nextFloat() * 0.9f
            val a = if (big) 230 else 90 + rnd.nextInt(130)
            p.color = if (big) Color.argb(a, 150, 200, 255) else Color.argb(a, 235, 240, 255)
            c.drawCircle(x, y, r, p)
            if (big) {
                p.color = Color.argb(40, 120, 180, 255)
                c.drawCircle(x, y, r * 3.2f, p)
            }
        }
        // Butir cahaya kecil di rumput.
        for (i in 0 until 160) {
            val x = rnd.nextFloat() * w
            val y = horizon + rnd.nextFloat() * (h - horizon)
            val warm = rnd.nextFloat() < 0.4f
            p.color = if (warm) Color.argb(120, 255, 170, 70) else Color.argb(110, 80, 190, 255)
            c.drawCircle(x, y, 0.8f + rnd.nextFloat() * 1.4f, p)
        }
        if (base !== src) base.recycle()
        src?.recycle()
        return bmp
    }
}
