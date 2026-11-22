package dev.browsery.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.browsery.app.browser.TabManager
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.ui.AppRoot
import dev.browsery.app.ui.BrowseryTheme

class MainActivity : ComponentActivity() {
    private lateinit var tabs: TabManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = AppDatabase.get(applicationContext)
        tabs = TabManager(applicationContext, db)
        tabs.onFullScreenChange = ::applyFullScreen
        tabs.start()
        setContent {
            BrowseryTheme {
                AppRoot(tabs, db)
            }
        }
        // Hindari membuka ulang tautan yang sama saat Activity dibuat ulang.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        tabs.onFullScreenChange = null
        tabs.closeAll()
        super.onDestroy()
    }

    /** Hanya tautan http/https dari intent VIEW yang diterima; skema lain diabaikan. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        val scheme = data.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            tabs.openExternal(data.toString())
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
