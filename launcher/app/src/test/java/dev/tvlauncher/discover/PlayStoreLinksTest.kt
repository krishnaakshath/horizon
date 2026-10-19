package dev.tvlauncher.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreLinksTest {
    @Test fun candidates_marketVending_thenMarket_thenWeb() {
        val c = PlayStoreLinks.detailsIntents("com.netflix.ninja")
        assertEquals(3, c.size)
        assertEquals("market://details?id=com.netflix.ninja", c[0].uri)
        assertEquals("com.android.vending", c[0].targetPackage)
        assertEquals("market://details?id=com.netflix.ninja", c[1].uri)
        assertEquals(null, c[1].targetPackage)
        assertEquals("https://play.google.com/store/apps/details?id=com.netflix.ninja", c[2].uri)
        assertEquals(PlayStoreLinks.Kind.WEB, c[2].kind)
    }

    @Test fun validPackages() {
        listOf("a.b", "com.netflix.ninja", "org.xbmc.kodi", "tv.pluto.android", "com.a_b.c9", "deezer.android.app")
            .forEach { assertTrue(it, PlayStoreLinks.isValidPackage(it)) }
    }

    @Test fun hostile_ids_produceNoCandidates() {
        listOf(
            "", " ", "com.netflix.ninja&hl=xx", "com.x#frag", "com x", "com.x\n", "com.x?y=1",
            "market://details?id=com.x", "com.x/../y", "com.x%26a", "com.x;rm", "javascript:alert(1)",
            ".com.x", "com.x.", "com..x", "nodots", "9com.x", "com.9x", "com.x\u0000", "com.ｘ", "com.x'y\"",
            "a".repeat(300) + ".b"
        ).forEach {
            assertFalse(it, PlayStoreLinks.isValidPackage(it))
            assertTrue(it, PlayStoreLinks.detailsIntents(it).isEmpty())
        }
    }

    @Test fun uris_neverContainExtraQueryOrFragment() {
        PopularApps.all.forEach { app ->
            PlayStoreLinks.detailsIntents(app.packageName).forEach { c ->
                val q = c.uri.substringAfter("?")
                assertEquals(c.uri, "id=${app.packageName}", q)
                assertFalse(c.uri.contains('#'))
                assertFalse(c.uri.contains(' '))
            }
        }
    }

    @Test fun marketUri_andWebUri_nullOnInvalid() {
        assertEquals(null, PlayStoreLinks.marketUri("bad id"))
        assertEquals(null, PlayStoreLinks.webUri("x&y"))
        assertEquals("market://details?id=a.b", PlayStoreLinks.marketUri("a.b"))
    }
}
