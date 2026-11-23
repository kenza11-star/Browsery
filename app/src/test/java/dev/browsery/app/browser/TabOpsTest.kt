package dev.browsery.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabOpsTest {
    private fun tab(id: String, url: String = "https://$id.example", priv: Boolean = false) =
        TabRecord(id, priv, url, "T-$id")

    private fun three(): TabsState {
        var s = TabsState()
        s = TabOps.add(s, tab("a"))
        s = TabOps.add(s, tab("b"))
        s = TabOps.add(s, tab("c"))
        return s
    }

    @Test fun addActivatesNewTab() {
        val s = three()
        assertEquals(listOf("a", "b", "c"), s.tabs.map { it.id })
        assertEquals("c", s.activeId)
    }

    @Test fun addWithoutActivateKeepsActive() {
        var s = TabOps.add(TabsState(), tab("a"))
        s = TabOps.add(s, tab("b"), activate = false)
        assertEquals("a", s.activeId)
    }

    @Test fun selectSwitchesWithoutMixingUrls() {
        var s = three()
        s = TabOps.select(s, "a")
        assertEquals("https://a.example", s.active?.url)
        s = TabOps.select(s, "b")
        assertEquals("https://b.example", s.active?.url)
        s = TabOps.select(s, "zzz")
        assertEquals("b", s.activeId)
    }

    @Test fun closeActiveSelectsRightNeighbour() {
        var s = TabOps.select(three(), "b")
        s = TabOps.close(s, "b")
        assertEquals("c", s.activeId)
        assertEquals(listOf("a", "c"), s.tabs.map { it.id })
    }

    @Test fun closeLastActiveSelectsLeftNeighbour() {
        val s = TabOps.close(three(), "c")
        assertEquals("b", s.activeId)
    }

    @Test fun closeNonActiveKeepsActive() {
        val s = TabOps.close(three(), "a")
        assertEquals("c", s.activeId)
    }

    @Test fun closeOnlyTabLeavesNoActive() {
        val s = TabOps.close(TabOps.add(TabsState(), tab("a")), "a")
        assertTrue(s.tabs.isEmpty())
        assertNull(s.activeId)
    }

    @Test fun closedNormalTabCanBeReopened() {
        var s = TabOps.close(three(), "b")
        assertEquals(1, s.closed.size)
        s = TabOps.reopen(s)
        assertEquals("b", s.activeId)
        assertEquals("https://b.example", s.active?.url)
        assertTrue(s.closed.isEmpty())
    }

    @Test fun privateTabIsNeverReopenable() {
        var s = TabOps.add(TabsState(), tab("p", priv = true))
        s = TabOps.close(s, "p")
        assertTrue(s.closed.isEmpty())
    }

    @Test fun emptyNewTabIsNotReopenable() {
        var s = TabOps.add(TabsState(), tab("n", url = ""))
        s = TabOps.close(s, "n")
        assertTrue(s.closed.isEmpty())
    }

    @Test fun closedListIsBounded() {
        var s = TabsState()
        repeat(15) { s = TabOps.add(s, tab("t$it")) }
        repeat(15) { s = TabOps.close(s, "t$it") }
        assertEquals(TabOps.MAX_CLOSED, s.closed.size)
    }

    @Test fun persistableExcludesPrivateTabs() {
        var s = three()
        s = TabOps.add(s, tab("p", priv = true))
        val saved = TabOps.persistable(s)
        assertEquals(listOf("a", "b", "c"), saved.map { it.id })
        assertFalse(saved.any { it.isPrivate })
    }

    @Test fun moveReordersAndClamps() {
        var s = three()
        s = TabOps.move(s, "c", -1)
        assertEquals(listOf("a", "c", "b"), s.tabs.map { it.id })
        s = TabOps.move(s, "a", -5)
        assertEquals(listOf("a", "c", "b"), s.tabs.map { it.id })
        s = TabOps.move(s, "a", 5)
        assertEquals(listOf("c", "b", "a"), s.tabs.map { it.id })
    }

    @Test fun updateChangesOnlyTargetTab() {
        val s = TabOps.update(three(), "b") { it.copy(url = "https://new.example") }
        assertEquals("https://new.example", s.tabs[1].url)
        assertEquals("https://a.example", s.tabs[0].url)
    }
}
