package dev.browsery.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.browsery.app.browser.TabManager
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.download.DownloadManager
import dev.browsery.app.extensions.ExtensionManager
import dev.browsery.app.settings.AppSettings
import dev.browsery.app.settings.ThemeMode
import dev.browsery.app.ui.AppRoot
import dev.browsery.app.ui.BrowseryTheme

class MainActivity : ComponentActivity() {
    private lateinit var tabs: TabManager
    private lateinit var downloads: DownloadManager
    private lateinit var settings: AppSettings
    private lateinit var extensions: ExtensionManager

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* unduhan tetap berjalan tanpa notifikasi */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BrowseryApp
        val db = AppDatabase.get(applicationContext)
        settings = app.settings
        downloads = app.downloads
        extensions = app.extensions
        downloads.onFirstEnqueue = ::askNotificationPermission
        downloads.start()
        tabs = TabManager(applicationContext, db)
        tabs.onFullScreenChange = ::applyFullScreen
        tabs.start()
        // Pasang ulang ekstensi yang pernah disetujui pengguna; kegagalan hanya menjadi status per ekstensi.
        try {
            extensions.restore()
        } catch (e: Exception) {
            // jangan menggagalkan start aplikasi
        }
        setContent {
            val s by settings.state.collectAsState()
            val dark = when (s.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BrowseryTheme(darkTheme = dark) {
                AppRoot(tabs, db, downloads, settings, extensions)
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
        downloads.onFirstEnqueue = null
        tabs.onFullScreenChange = null
        tabs.closeAll()
        super.onDestroy()
    }

    /** Hanya tautan http/https yang diterima (dari VIEW atau teks yang dibagikan); skema lain diabaikan. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                val scheme = data.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") tabs.openExternal(data.toString())
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val m = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(text) ?: return
                tabs.openExternal(m.value)
            }
        }
    }

    /** Diminta sekali saja, saat unduhan pertama dimulai (Android 13+). */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("download_settings", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notification", false)) return
        prefs.edit().putBoolean("asked_notification", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
