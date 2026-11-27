package dev.browsery.app.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Menampilkan GeckoSession yang dimiliki pihak lain (BrowserController).
 * Session TIDAK ditutup di sini, sehingga recomposition/rotasi tidak memuat ulang halaman.
 * Session harus sudah dibuka (open) sebelum composable ini tampil.
 */
@Composable
fun GeckoHost(session: GeckoSession, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> GeckoView(ctx).also { it.setSession(session); ThumbnailStore.attach(it) } },
        onRelease = { view ->
            ThumbnailStore.detach(view)
            view.releaseSession()
        },
    )
}
