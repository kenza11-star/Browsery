package dev.browsery.app.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.browsery.app.auth.AuthManager
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.BookmarkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sinkronisasi bookmark, opt-in (mati secara bawaan). Hanya bookmark dari penyimpanan lokal yang dikirim;
 * riwayat, tab (termasuk tab privat), dan pengaturan TIDAK disinkronkan. Merge aditif berdasarkan URL.
 */
class SyncManager(context: Context, private val db: AppDatabase, private val auth: AuthManager) {
    private val prefs = context.applicationContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val client = SyncClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var enabled: Boolean by mutableStateOf(prefs.getBoolean("bookmarks", false))
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var status: String? by mutableStateOf(null)
        private set
    var lastSyncMs: Long by mutableLongStateOf(prefs.getLong("last", 0L))
        private set

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("bookmarks", value).apply()
        enabled = value
        if (value) syncNow()
    }

    private fun readLocal(): List<SyncMerge.Item> {
        db.openHelper.readableDatabase.query("SELECT url, title FROM bookmarks").use { c ->
            val out = ArrayList<SyncMerge.Item>()
            while (c.moveToNext()) out.add(SyncMerge.Item(c.getString(0), c.getString(1)))
            return out
        }
    }

    fun syncNow() {
        if (busy || !enabled) return
        busy = true
        status = null
        scope.launch {
            try {
                val token = auth.accessToken() ?: throw SyncException("Belum masuk.")
                val local = withContext(Dispatchers.IO) { readLocal() }
                val remote = withContext(Dispatchers.IO) { client.fetchBookmarks(token) }
                val plan = SyncMerge.plan(local, remote)
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    plan.toPull.forEach {
                        db.bookmarkDao().insert(BookmarkEntity(url = it.url, title = it.title.ifBlank { it.url }, createdAt = now))
                    }
                    client.pushBookmarks(token, plan.toPush)
                }
                lastSyncMs = System.currentTimeMillis()
                prefs.edit().putLong("last", lastSyncMs).apply()
                status = "Selesai: ${plan.toPull.size} diunduh, ${plan.toPush.size} diunggah."
            } catch (e: SyncException) {
                status = e.message
            } catch (e: Exception) {
                status = "Sinkronisasi gagal."
            } finally {
                busy = false
            }
        }
    }
}
