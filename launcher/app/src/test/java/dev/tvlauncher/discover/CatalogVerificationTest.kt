package dev.tvlauncher.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The catalog must match the committed verification record (assets/popular_apps_verification.txt). */
class CatalogVerificationTest {
    private data class Line(val pkg: String, val http: String, val date: String, val tv: String, val status: String)

    private val lines: Map<String, Line> by lazy {
        val f = listOf("src/main/assets/popular_apps_verification.txt", "app/src/main/assets/popular_apps_verification.txt")
            .map { File(it) }.first { it.isFile }
        f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { l ->
            val c = l.split('\t')
            assertEquals(l, 5, c.size)
            Line(c[0], c[1], c[2], c[3], c[4])
        }.associateBy { it.pkg }
    }

    @Test fun everyCatalogPackage_wasVerified200_andKept() {
        PopularApps.all.forEach { app ->
            val l = lines[app.packageName]
            assertNotNull(app.packageName, l)
            assertEquals(app.packageName, "200", l!!.http)
            assertEquals(app.packageName, "kept", l.status)
            assertEquals(app.packageName, PopularApps.VERIFIED_ON, l.date)
        }
    }

    @Test fun tvReady_onlyWhenListingShowedTv() {
        PopularApps.all.forEach { app ->
            assertEquals(app.packageName, if (app.tvReady) "yes" else "no", lines.getValue(app.packageName).tv)
        }
    }

    @Test fun droppedPackages_areNotInCatalog() {
        val dropped = lines.values.filter { it.status.startsWith("dropped") }
        assertTrue(dropped.isNotEmpty())
        dropped.forEach { assertEquals(it.pkg, null, PopularApps.byPackage(it.pkg)) }
    }

    @Test fun fdroidSources_wereVerified() {
        PopularApps.all.mapNotNull { it.source as? Source.Fdroid }.forEach {
            assertEquals(it.packageName, "200", lines.getValue("fdroid:" + it.packageName).http)
        }
    }

    @Test fun keptCount_matchesCatalog() {
        assertEquals(PopularApps.all.size, lines.values.count { it.status == "kept" && !it.pkg.startsWith("fdroid:") })
    }
}
