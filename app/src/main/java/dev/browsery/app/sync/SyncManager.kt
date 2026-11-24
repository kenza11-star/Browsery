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

    private var enabledState: Boolean by mutableStateOf(prefs.getBoolean("bookmarks", false))
    private var busyState: Boolean by mutableStateOf(false)
    private var statusState: String? by mutableStateOf(null)
    private var lastState: Long by mutableLongStateOf(prefs.getLong("last", 0L))

    val enabled: Boolean get() = enabledState
    val busy: Boolean get() = busyState
    val status: String? get() = statusState
    val lastSyncMs: Long get() = lastState

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("bookmarks", value).apply()
        enabledState = value
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
        if (busyState || !enabledState) return
        busyState = true
        statusState = null
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
                lastState = System.currentTimeMillis()
                prefs.edit().putLong("last", lastState).apply()
                statusState = "Selesai: ${plan.toPull.size} diunduh, ${plan.toPush.size} diunggah."
            } catch (e: SyncException) {
                statusState = e.message
            } catch (e: Exception) {
                statusState = "Sinkronisasi gagal."
            } finally {
                busyState = false
            }
        }
    }
}
