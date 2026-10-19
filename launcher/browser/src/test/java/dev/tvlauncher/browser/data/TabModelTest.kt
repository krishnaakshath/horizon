package dev.tvlauncher.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabModelTest {
    @Test fun startsWithOneStartPageTab() {
        val m = TabModel()
        assertEquals(1, m.tabs.size)
        assertNull(m.active.url)
    }

    @Test fun open_activates_andRespectsLimit() {
        val m = TabModel(max = 3)
        assertTrue(m.canOpen())
        val t2 = m.open("https://a.com/")!!
        assertEquals(t2.id, m.active.id)
        m.open("https://b.com/")
        assertFalse(m.canOpen())
        assertNull(m.open("https://c.com/"))
        assertEquals(3, m.tabs.size)
    }

    @Test fun open_inBackground_keepsActive() {
        val m = TabModel()
        val first = m.active
        m.open("https://a.com/", activate = false)
        assertEquals(first.id, m.active.id)
        assertEquals("https://a.com/", m.tabs[1].url)
    }

    @Test fun close_active_selectsRightThenLeftNeighbor() {
        val m = TabModel()
        val a = m.active; val b = m.open("https://b.com/")!!; val c = m.open("https://c.com/")!!
        m.switchTo(1)
        m.close(b.id)
        assertEquals(c.id, m.active.id)
        m.close(c.id)
        assertEquals(a.id, m.active.id)
    }

    @Test fun close_inactive_keepsActive() {
        val m = TabModel()
        val a = m.active; val b = m.open("https://b.com/")!!
        m.close(a.id)
        assertEquals(b.id, m.active.id)
        assertEquals(1, m.tabs.size)
    }

    @Test fun close_lastTab_leavesFreshStartTab() {
        val m = TabModel()
        val only = m.active
        m.active.url = "https://x.com/"
        m.close(only.id)
        assertEquals(1, m.tabs.size)
        assertNotEquals(only.id, m.active.id)
        assertNull(m.active.url)
    }

    @Test fun switchTo_clampsIndex() {
        val m = TabModel()
        m.open("https://b.com/")
        m.switchTo(7)
        assertEquals(1, m.activeIndex)
        m.switchTo(-3)
        assertEquals(0, m.activeIndex)
    }

    @Test fun session_roundTrip_andTolerance() {
        val m = TabModel()
        m.active.url = "https://a.com/"; m.active.title = "A"; m.active.scrollY = 120; m.active.desktop = true
        m.open("https://b.com/")
        m.switchTo(0)
        val r = TabModel.decode(m.encode())
        assertEquals(listOf("https://a.com/", "https://b.com/"), r.tabs.map { it.url })
        assertEquals(120, r.tabs[0].scrollY)
        assertTrue(r.tabs[0].desktop)
        assertEquals(0, r.activeIndex)
        val junk = TabModel.decode("""{"active":9,"tabs":[{"url":"javascript:x"},{"url":"https://ok.com/","scrollY":-5},1,2,3,{"url":"https://c.com/"},{"url":"https://d.com/"}]}""")
        assertEquals(listOf(null, "https://ok.com/", "https://c.com/"), junk.tabs.map { it.url })
        assertEquals(0, junk.tabs[1].scrollY)
        assertEquals(2, junk.activeIndex)
        assertEquals(1, TabModel.decode("garbage").tabs.size)
    }
}
