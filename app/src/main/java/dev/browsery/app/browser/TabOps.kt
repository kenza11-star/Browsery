package dev.browsery.app.browser

/** Catatan satu tab. [url] adalah URL terakhir yang diketahui (untuk restore/ditampilkan saat tab belum dimuat). */
data class TabRecord(
    val id: String,
    val isPrivate: Boolean,
    val url: String = "",
    val title: String = "",
)

data class TabsState(
    val tabs: List<TabRecord> = emptyList(),
    val activeId: String? = null,
    val closed: List<TabRecord> = emptyList(),
) {
    val active: TabRecord? get() = tabs.firstOrNull { it.id == activeId }
}

/** Operasi tab murni (tanpa Android) agar mudah diuji. */
object TabOps {
    const val MAX_CLOSED = 10

    fun add(s: TabsState, tab: TabRecord, activate: Boolean = true): TabsState =
        s.copy(
            tabs = s.tabs + tab,
            activeId = if (activate || s.activeId == null) tab.id else s.activeId,
        )

    fun select(s: TabsState, id: String): TabsState =
        if (s.tabs.any { it.id == id }) s.copy(activeId = id) else s

    fun update(s: TabsState, id: String, transform: (TabRecord) -> TabRecord): TabsState =
        s.copy(tabs = s.tabs.map { if (it.id == id) transform(it) else it })

    /** Menutup tab. Tab privat tidak pernah masuk daftar "buka lagi". */
    fun close(s: TabsState, id: String): TabsState {
        val idx = s.tabs.indexOfFirst { it.id == id }
        if (idx < 0) return s
        val removed = s.tabs[idx]
        val rest = s.tabs.filterNot { it.id == id }
        val closed =
            if (!removed.isPrivate && removed.url.isNotEmpty()) {
                (listOf(removed) + s.closed).take(MAX_CLOSED)
            } else {
                s.closed
            }
        val active =
            if (s.activeId != id) s.activeId else (rest.getOrNull(idx) ?: rest.getOrNull(idx - 1))?.id
        return s.copy(tabs = rest, activeId = active, closed = closed)
    }

    fun reopen(s: TabsState): TabsState {
        val tab = s.closed.firstOrNull() ?: return s
        return s.copy(tabs = s.tabs + tab, activeId = tab.id, closed = s.closed.drop(1))
    }

    fun move(s: TabsState, id: String, delta: Int): TabsState {
        val i = s.tabs.indexOfFirst { it.id == id }
        if (i < 0) return s
        val j = (i + delta).coerceIn(0, s.tabs.lastIndex)
        if (i == j) return s
        val list = s.tabs.toMutableList()
        val tab = list.removeAt(i)
        list.add(j, tab)
        return s.copy(tabs = list)
    }

    /** Hanya tab normal yang boleh ditulis ke disk. */
    fun persistable(s: TabsState): List<TabRecord> = s.tabs.filter { !it.isPrivate }
}
