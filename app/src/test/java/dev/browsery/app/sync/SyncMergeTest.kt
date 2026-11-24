package dev.browsery.app.sync

import dev.browsery.app.sync.SyncMerge.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    @Test
    fun unionBothWays() {
        val local = listOf(Item("https://a.com", "A"), Item("https://b.com", "B"))
        val remote = listOf(Item("https://b.com", "B2"), Item("https://c.com", "C"))
        val p = SyncMerge.plan(local, remote)
        assertEquals(listOf("https://c.com"), p.toPull.map { it.url })
        assertEquals(listOf("https://a.com"), p.toPush.map { it.url })
    }

    @Test
    fun identicalSetsDoNothing() {
        val x = listOf(Item("https://a.com", "A"))
        val p = SyncMerge.plan(x, x)
        assertTrue(p.toPull.isEmpty() && p.toPush.isEmpty())
    }

    @Test
    fun dedupesAndSkipsNonWebUrls() {
        val local = listOf(Item("https://a.com", "A"), Item("https://a.com", "A again"), Item("javascript:alert(1)", "x"), Item("file:///x", "y"))
        val p = SyncMerge.plan(local, emptyList())
        assertEquals(1, p.toPush.size)
    }

    @Test
    fun truncatesLongTitleAndRejectsLongUrl() {
        val p = SyncMerge.plan(listOf(Item("https://a.com", "t".repeat(900))), emptyList())
        assertEquals(500, p.toPush[0].title.length)
        assertFalse(SyncMerge.syncable("https://a.com/" + "x".repeat(2100)))
    }
}
