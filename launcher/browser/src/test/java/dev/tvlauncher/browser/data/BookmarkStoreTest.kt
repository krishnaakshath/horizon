package dev.tvlauncher.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookmarkStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val defaults = listOf(Bookmark("https://a.com/", "A"), Bookmark("https://b.com/", "B"))

    private fun store(name: String = "b.json", max: Int = 50) = BookmarkStore(java.io.File(tmp.root, name), defaults, max)

    @Test fun missingFile_seedsDefaults() {
        assertEquals(defaults, store().items)
    }

    @Test fun add_remove_contains_andPersist() {
        val s = store()
        assertTrue(s.add("https://c.com/", "C"))
        assertTrue(s.contains("https://c.com/"))
        assertTrue(s.remove("https://a.com/"))
        assertFalse(s.remove("https://zzz.com/"))
        s.save()
        assertEquals(listOf("https://b.com/", "https://c.com/"), store().items.map { it.url })
    }

    @Test fun add_dedupes_andValidatesUrls() {
        val s = store()
        assertFalse(s.add("https://a.com/", "dup"))
        assertFalse(s.add("javascript:alert(1)", "x"))
        assertFalse(s.add("file:///sdcard/x", "x"))
        assertFalse(s.add("https://x.com/" + "a".repeat(3000), "long"))
        assertEquals(2, s.items.size)
    }

    @Test fun add_respectsCap_andTrimsTitle() {
        val s = store(max = 3)
        assertTrue(s.add("https://c.com/", "  " + "t".repeat(500) + " "))
        assertFalse(s.add("https://d.com/", "D"))
        assertEquals(200, s.items.last().title.length)
        assertTrue(s.add("https://e.com/", "") .not() || s.items.size <= 3)
    }

    @Test fun emptyTitle_fallsBackToHost() {
        val s = store()
        s.add("https://www.example.org/path", "  ")
        assertEquals("example.org", s.items.last().title)
    }

    @Test fun move_reorders_andIgnoresBadIndexes() {
        val s = store()
        s.add("https://c.com/", "C")
        s.move(2, 0)
        assertEquals(listOf("C", "A", "B"), s.items.map { it.title })
        s.move(0, 99)
        s.move(-1, 0)
        assertEquals(listOf("C", "A", "B"), s.items.map { it.title })
        s.move(0, 2)
        assertEquals(listOf("A", "B", "C"), s.items.map { it.title })
    }

    @Test fun corruptFile_isEmpty_notDefaults() {
        java.io.File(tmp.root, "bad.json").writeText("{not json[[")
        assertTrue(store("bad.json").items.isEmpty())
    }

    @Test fun tolerantJson_skipsBadEntries() {
        java.io.File(tmp.root, "t.json").writeText(
            """{"v":1,"items":[{"u":"https://ok.com/","t":"Ok"},{"u":"javascript:x","t":"bad"},{"t":"no url"},5,null,{"u":"https://ok.com/","t":"dup"}]}"""
        )
        assertEquals(listOf(Bookmark("https://ok.com/", "Ok")), store("t.json").items)
    }
}
