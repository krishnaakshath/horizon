package dev.tvlauncher.apps

import dev.tvlauncher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {
    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, label, "$pkg.Main")
    private fun pkgs(l: List<AppEntry>) = l.map { it.packageName }

    private val junk = listOf(
        "com.tcl.initsetup", "com.tcl.copydatatotv", "com.google.android.tungsten.setupwraith",
        "com.tcl.factory.view", "com.tcl.autopair", "com.tcl.micmanager", "com.tcl.useragreement",
        "com.android.htmlviewer", "com.google.android.apps.nbu.smartconnect.tv",
        "com.google.android.partnersetup", "com.android.settings.intelligence"
    )

    @Test fun defaultHidden_containsClutterButNotMediaPlayer() {
        assertTrue(AppFilter.DEFAULT_HIDDEN.containsAll(junk))
        assertTrue("com.tcl.ui_mediaCenter" !in AppFilter.DEFAULT_HIDDEN)
    }

    @Test fun defaultHidden_removedUnlessFavorite() {
        val apps = listOf(app("com.tcl.initsetup", "Initial Setup"), app("com.tcl.copydatatotv", "copy"), app("x", "X"))
        assertEquals(listOf("x"), pkgs(AppFilter.arrange(apps, LauncherSettings())))
        val fav = LauncherSettings(favorites = listOf("com.tcl.copydatatotv"))
        assertEquals(listOf("com.tcl.copydatatotv", "x"), pkgs(AppFilter.arrange(apps, fav)))
    }

    @Test fun tclApps_staysHiddenEvenWhenInUserOrder_butFavoritesWin() {
        val apps = listOf(app("com.tcl.initsetup", "Initial Setup"), app("x", "X"))
        val inOrder = LauncherSettings(order = listOf("x", "com.tcl.initsetup"))
        assertEquals(listOf("x"), pkgs(AppFilter.arrange(apps, inOrder)))
        val favorite = LauncherSettings(favorites = listOf("com.tcl.initsetup"))
        assertEquals(setOf("x", "com.tcl.initsetup"), pkgs(AppFilter.arrange(apps, favorite)).toSet())
    }

    @Test fun userHidden_stillHidden() {
        val apps = listOf(app("a"), app("b"))
        assertEquals(listOf("b"), pkgs(AppFilter.arrange(apps, LauncherSettings(hidden = setOf("a")))))
    }

    @Test fun preferredApps_firstWhenNoUserOrder() {
        val apps = listOf(
            app("com.zeta", "Zeta"), app("com.android.vending", "Google Play Store"),
            app("com.apple.atve.androidtv.appletv", "Apple TV"), app("com.alpha", "Alpha"),
            app("com.google.android.youtube.tv", "YouTube"), app("com.netflix.ninja", "Netflix"),
            app("com.amazon.amazonvideo.livingroom", "Prime Video")
        )
        assertEquals(
            listOf(
                "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
                "com.apple.atve.androidtv.appletv", "com.android.vending", "com.alpha", "com.zeta"
            ),
            pkgs(AppFilter.arrange(apps, LauncherSettings()))
        )
    }

    @Test fun userOrder_beatsPreferredOrder() {
        val apps = listOf(app("com.netflix.ninja", "Netflix"), app("com.alpha", "Alpha"), app("com.google.android.youtube.tv", "YouTube"))
        val s = LauncherSettings(favorites = listOf("com.alpha"), order = listOf("com.google.android.youtube.tv"))
        assertEquals(
            listOf("com.alpha", "com.google.android.youtube.tv", "com.netflix.ninja"),
            pkgs(AppFilter.arrange(apps, s))
        )
    }

    @Test fun unhideAll_clearsHidden_andRestoresInstalledDefaultHiddenAtEnd() {
        val installed = listOf(app("com.tcl.initsetup"), app("a"), app("b"), app("com.tcl.autopair"))
        val s = LauncherSettings(hidden = setOf("b"), favorites = listOf("a"))
        val visible = listOf("a")
        val out = AppFilter.unhideAll(s, visible, installed)
        assertEquals(emptySet<String>(), out.hidden)
        assertEquals(listOf("a"), out.favorites)
        // TCL's own apps are not restored by "Unhide all": H02 replaces that layer.
        val shown = pkgs(AppFilter.arrange(installed, out))
        assertEquals(setOf("a", "b"), shown.toSet())
    }

    @Test fun unhideAll_nothingDefaultHiddenInstalled_keepsOrderUntouched() {
        val installed = listOf(app("a"), app("b"))
        val s = LauncherSettings(hidden = setOf("b"))
        val out = AppFilter.unhideAll(s, listOf("a"), installed)
        assertEquals(LauncherSettings(), out)
    }

    @Test fun searchable_isShelfThenUserHidden_neverClutter() {
        val all = listOf(app("a"), app("com.tcl.initsetup"), app("b"), app("c"))
        val s = LauncherSettings(hidden = setOf("b"))
        val shown = AppFilter.arrange(all, s)
        assertEquals(listOf("a", "c"), pkgs(shown))
        assertEquals(listOf("a", "c", "b"), pkgs(AppFilter.searchable(all, shown, s)))
    }
}
