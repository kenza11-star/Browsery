package dev.browsery.app.engine

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

/** GeckoRuntime hanya boleh dibuat satu kali per proses. */
object GeckoRuntimeProvider {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
        }
}
