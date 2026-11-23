package dev.browsery.app.download

/** Satu potongan byte (rentang inklusif) dari satu file. [done] = byte yang sudah tertulis. */
data class Chunk(val start: Long, val end: Long, val done: Long = 0) {
    val length: Long get() = end - start + 1
    val isComplete: Boolean get() = done >= length
}

object Chunks {
    /** Membagi [total] byte menjadi paling banyak [connections] potongan berurutan tanpa celah. */
    fun plan(total: Long, connections: Int): List<Chunk> {
        require(total > 0) { "total harus > 0" }
        val n = minOf(connections.coerceIn(1, 64).toLong(), total).toInt()
        val base = total / n
        val extra = total % n
        val out = ArrayList<Chunk>(n)
        var pos = 0L
        for (i in 0 until n) {
            val len = base + (if (i < extra) 1 else 0)
            out.add(Chunk(pos, pos + len - 1))
            pos += len
        }
        return out
    }

    /** Mode otomatis: jumlah koneksi berdasarkan ukuran file (bukan ukuran jaringan). */
    fun autoConnections(total: Long): Int = when {
        total < (1L shl 20) -> 1
        total < (16L shl 20) -> 2
        total < (128L shl 20) -> 4
        else -> 8
    }

    fun encode(chunks: List<Chunk>): String =
        chunks.joinToString(";") { "${it.start},${it.end},${it.done}" }

    fun decode(s: String?): List<Chunk> {
        if (s.isNullOrBlank()) return emptyList()
        return try {
            s.split(';').map { part ->
                val a = part.split(',')
                Chunk(a[0].toLong(), a[1].toLong(), a[2].toLong())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Potongan valid: berurutan dari 0, tanpa celah/tumpang tindih, mencakup tepat [total] byte. */
    fun isValid(chunks: List<Chunk>, total: Long): Boolean {
        if (chunks.isEmpty()) return false
        var expect = 0L
        for (c in chunks) {
            if (c.start != expect || c.end < c.start || c.done < 0 || c.done > c.length) return false
            expect = c.end + 1
        }
        return expect == total
    }
}
