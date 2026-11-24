package dev.browsery.app

import android.app.Application
import dev.browsery.app.auth.AuthManager
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.download.DownloadManager
import dev.browsery.app.extensions.ExtensionManager
import dev.browsery.app.settings.AppSettings

class BrowseryApp : Application() {
    /** Satu pengelola unduhan per proses; dipakai oleh UI, BrowserController, dan service. */
    val downloads: DownloadManager by lazy { DownloadManager(this, AppDatabase.get(this)) }

    val settings: AppSettings by lazy { AppSettings(this) }

    val extensions: ExtensionManager by lazy { ExtensionManager(this, settings) }

    val auth: AuthManager by lazy { AuthManager(this) }
}
