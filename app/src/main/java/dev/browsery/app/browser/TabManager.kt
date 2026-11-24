package dev.browsery.app.browser

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.browsery.app.data.AppDatabase
import dev.browsery.app.data.HistoryEntity
import dev.browsery.app.data.SavedTabEntity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mengelola banyak tab. Setiap tab yang SUDAH dipakai punya satu [BrowserController] (GeckoSession).
 * Tab baru kosong tidak membuat session (menghemat RAM) sampai pengguna menavigasi.
 * Tab normal latar belakang dibatasi [maxLoaded] (menurut memori perangkat, lihat [TabLimits]); yang melebihi
 * batas dilepas dan dimuat ulang dari URL terakhir saat dipilih (posisi scroll/form tidak dipertahankan).
 */
class TabManager(context: Context, private val db: AppDatabase) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val maxLoaded = TabLimits.forDevice(appContext)

    var state by mutableStateOf(TabsState())
        private set

    private val controllers = mutableStateMapOf<String, BrowserController>()

    var onFullScreenChange: ((Boolean) -> Unit)? = null

    private var started = false
    private var restored = false
    private val pendingUrls = mutableListOf<String>()
    private var saveJob: Job? = null
    private var lastHistoryUrl: String? = null
    private var lastHistoryAt = 0L

    fun controllerOf(id: String): BrowserController? = controllers[id]

    fun displayUrl(rec: TabRecord): String = controllers[rec.id]?.state?.displayUrl ?: rec.url

    fun displayTitle(rec: TabRecord): String {
        val live = controllers[rec.id]?.state?.title.orEmpty()
        if (live.isNotBlank()) return live
        if (rec.title.isNotBlank()) return rec.title
        val url = displayUrl(rec)
        return runCatching { Uri.parse(url).host }.getOrNull().orEmpty().ifEmpty { url }
    }

    /** Memulihkan tab normal dari database, lalu memproses tautan yang menunggu. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val saved = runCatching { db.savedTabDao().getAll() }.getOrDefault(emptyList())
            var s = TabsState()
            for (e in saved.sortedBy { it.position }) {
                s = TabOps.add(s, TabRecord(e.id, false, e.url, e.title), activate = e.isActive)
            }
            state = s
            if (state.tabs.isEmpty()) newTab(false) else state.activeId?.let { ensureLoaded(it) }
            restored = true
            val queued = pendingUrls.toList()
            pendingUrls.clear()
            queued.forEach { openInNewTab(it) }
        }
    }

    /** Tautan dari aplikasi lain. Ditunda sampai restore selesai agar tidak tertimpa. */
    fun openExternal(url: String) {
        if (!restored) pendingUrls.add(url) else openInNewTab(url)
    }

    fun newTab(isPrivate: Boolean = false): String {
        val rec = TabRecord(UUID.randomUUID().toString(), isPrivate)
        state = TabOps.add(state, rec)
        scheduleSave()
        return rec.id
    }

    fun openInNewTab(url: String, isPrivate: Boolean = false) {
        newTab(isPrivate)
        navigate(url)
    }

    /** Menavigasi tab aktif (alamat atau kata pencarian). */
    fun navigate(input: String) {
        val rec = state.active ?: return
        val c = controllers[rec.id] ?: createController(rec)
        c.submit(input)
    }

    fun select(id: String) {
        state = TabOps.select(state, id)
        ensureLoaded(id)
        scheduleSave()
    }

    fun closeTab(id: String) {
        syncRecords()
        controllers.remove(id)?.close()
        state = TabOps.close(state, id)
        if (state.tabs.isEmpty()) newTab(false) else state.activeId?.let { ensureLoaded(it) }
        scheduleSave()
    }

    fun reopenClosed() {
        state = TabOps.reopen(state)
        state.activeId?.let { ensureLoaded(it) }
        scheduleSave()
    }

    fun moveTab(id: String, delta: Int) {
        state = TabOps.move(state, id, delta)
        scheduleSave()
    }

    /** Dipanggil saat Activity dihancurkan: simpan lalu tutup semua session. */
    fun closeAll() {
        saveJob?.cancel()
        syncRecords()
        val snapshot = buildSnapshot()
        scope.launch { runCatching { db.savedTabDao().replaceAll(snapshot) } }
        controllers.values.forEach { it.close() }
        controllers.clear()
    }

    private fun ensureLoaded(id: String) {
        val rec = state.tabs.firstOrNull { it.id == id } ?: return
        if (controllers[id] == null && rec.url.isNotEmpty()) {
            createController(rec).loadUrl(rec.url)
        }
        enforceLimit(keep = id)
    }

    private fun createController(rec: TabRecord): BrowserController {
        val c = BrowserController(appContext, rec.isPrivate)
        c.attach()
        c.onFullScreenChange = { fs -> if (state.activeId == rec.id) onFullScreenChange?.invoke(fs) }
        c.onChanged = { scheduleSave() }
        c.onPageLoaded = { url, title -> if (!rec.isPrivate) recordHistory(url, title) }
        controllers[rec.id] = c
        return c
    }

    /** Melepas session tab normal terlama di latar belakang bila melebihi batas. Tab privat tidak dilepas. */
    private fun enforceLimit(keep: String) {
        while (controllers.size > maxLoaded) {
            val victim = state.tabs.firstOrNull { it.id != keep && !it.isPrivate && controllers.containsKey(it.id) }
                ?: return
            syncRecords()
            controllers.remove(victim.id)?.close()
        }
    }

    private fun recordHistory(url: String, title: String) {
        val now = System.currentTimeMillis()
        if (url == lastHistoryUrl && now - lastHistoryAt < 3000) return
        lastHistoryUrl = url
        lastHistoryAt = now
        scope.launch {
            runCatching { db.historyDao().insert(HistoryEntity(url = url, title = title.ifBlank { url }, visitedAt = now)) }
        }
    }

    /** Menyalin URL/judul terbaru dari session ke catatan tab. */
    private fun syncRecords() {
        var s = state
        for ((id, c) in controllers) {
            val st = c.state
            if (st.started && st.url.isNotEmpty() && !st.url.startsWith("data:")) {
                s = TabOps.update(s, id) { it.copy(url = st.url, title = st.title) }
            }
        }
        state = s
    }

    private fun buildSnapshot(): List<SavedTabEntity> {
        val normal = TabOps.persistable(state)
        val activeNormal = normal.firstOrNull { it.id == state.activeId }?.id ?: normal.lastOrNull()?.id
        return normal.mapIndexed { i, t -> SavedTabEntity(t.id, t.url, t.title, i, t.id == activeNormal) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            syncRecords()
            val snapshot = buildSnapshot()
            runCatching { db.savedTabDao().replaceAll(snapshot) }
        }
    }
}
