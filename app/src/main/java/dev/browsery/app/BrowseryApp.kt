package dev.browsery.app

import android.app.Application
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.download.DownloadManager

class BrowseryApp : Application() {
    /** Satu pengelola unduhan per proses; dipakai oleh UI, BrowserController, dan service. */
    val downloads: DownloadManager by lazy { DownloadManager(this, AppDatabase.get(this)) }
}
