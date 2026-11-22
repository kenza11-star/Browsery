package dev.browsery.app.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Tempat GeckoView untuk Stage 1. Hanya membuka about:blank.
 * Navigasi, address bar, dan tab dibangun di Stage 2.
 */
@Composable
fun GeckoHost(modifier: Modifier = Modifier) {
    val session = remember { GeckoSession() }

    DisposableEffect(session) {
        onDispose { session.close() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            session.open(GeckoRuntimeProvider.get(ctx))
            session.loadUri("about:blank")
            GeckoView(ctx).also { it.setSession(session) }
        },
    )
}
