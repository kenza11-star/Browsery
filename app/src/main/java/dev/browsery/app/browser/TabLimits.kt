package dev.browsery.app.browser

import android.app.ActivityManager
import android.content.Context

/** Batas jumlah session tab yang dimuat sekaligus, menurut kelas memori perangkat. */
object TabLimits {
    fun forMemory(memoryClassMb: Int, lowRam: Boolean): Int = when {
        lowRam -> 3
        memoryClassMb < 192 -> 3
        memoryClassMb < 256 -> 4
        memoryClassMb < 384 -> 6
        else -> 8
    }

    fun forDevice(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 4
        return forMemory(am.memoryClass, am.isLowRamDevice)
    }
}
