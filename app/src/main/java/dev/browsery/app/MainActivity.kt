package dev.browsery.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.browsery.app.browser.BrowserController
import dev.browsery.app.ui.BrowserScreen
import dev.browsery.app.ui.BrowseryTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: BrowserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = BrowserController(applicationContext).also { it.attach() }
        controller.onFullScreenChange = ::applyFullScreen
        setContent {
            BrowseryTheme {
                BrowserScreen(controller)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        controller.close()
        super.onDestroy()
    }

    /** Hanya tautan http/https dari intent VIEW yang diterima; skema lain diabaikan. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        val scheme = data.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            controller.loadExternalWebUrl(data.toString())
        }
    }

    private fun applyFullScreen(fullScreen: Boolean) {
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (fullScreen) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
