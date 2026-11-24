package dev.browsery.app.sync

/** Logika merge murni (diuji unit). Kunci pencocokan: URL. Merge bersifat aditif: tidak ada penghapusan. */
object SyncMerge {
    data class Item(val url: String, val title: String)

    data class Plan(val toPull: List<Item>, val toPush: List<Item>)

    fun syncable(url: String): Boolean =
        url.length in 1..2048 && (url.startsWith("http://") || url.startsWith("https://"))

    fun plan(local: List<Item>, remote: List<Item>): Plan {
        val l = local.filter { syncable(it.url) }.distinctBy { it.url }
        val r = remote.filter { syncable(it.url) }.distinctBy { it.url }
        val localUrls = l.map { it.url }.toSet()
        val remoteUrls = r.map { it.url }.toSet()
        return Plan(
            toPull = r.filter { it.url !in localUrls },
            toPush = l.filter { it.url !in remoteUrls }.map { it.copy(title = it.title.take(500)) },
        )
    }
}
