package dev.tvlauncher.brand

import dev.tvlauncher.settings.Branding
import dev.tvlauncher.type.FontNotices
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandTest {
    @Test fun names() {
        assertEquals("Horizon", Brand.NAME)
        assertEquals("H02", Brand.SHORT)
        assertEquals("HORIZON", Brand.CAPTION)
        assertEquals("H02 Browser", Brand.BROWSER)
        assertEquals("H02 Store", Brand.STORE)
        assertEquals("H02 Weather", Brand.WEATHER)
        assertEquals("H02 Helper", Brand.HELPER)
        assertEquals("H02 Link", Brand.LINK)
        assertEquals("H02 Ambient", Brand.AMBIENT)
        assertEquals(Brand.NAME.uppercase(), Brand.CAPTION)
        assertEquals(Brand.NAME, Branding.NAME)
        assertTrue(Branding.COPYRIGHT_HOLDER.contains(Brand.NAME))
        assertTrue(Brand.TAGLINE.isNotBlank() && !Brand.TAGLINE.contains('\n'))
        assertEquals("Horizon 1.0 (1)", Brand.versionLine("1.0 (1)"))
        for (c in listOf(Brand.BROWSER, Brand.STORE, Brand.WEATHER, Brand.HELPER, Brand.LINK, Brand.AMBIENT)) {
            assertTrue(c, c.startsWith(Brand.SHORT + " "))
        }
    }

    @Test fun marksUseOnlyGlyphsInTheBrandSubset() {
        assertTrue(BrandFont.covers(Brand.SHORT))
        assertTrue(BrandFont.covers(Brand.CAPTION))
        assertTrue(BrandFont.covers(Brand.NAME))
        assertFalse(BrandFont.covers("H02 ★"))
    }
}

/** Every module's system label mirrors [Brand]; manifests read it from @string/app_name. */
class BrandStringsTest {
    private fun appName(file: String): String {
        val xml = File(file).readText()
        return Regex("""<string name="app_name"[^>]*>([^<]*)</string>""").find(xml)!!.groupValues[1]
    }

    @Test fun appNamesMirrorTheShortMark() {
        assertEquals(Brand.SHORT, appName("src/main/res/values/brand_strings.xml"))
        assertEquals(Brand.BROWSER, appName("../browser/src/main/res/values/strings.xml"))
        assertEquals(Brand.STORE, appName("../store/src/main/res/values/styles.xml"))
        assertEquals(Brand.WEATHER, appName("../weather/src/main/res/values/strings.xml"))
        assertEquals(Brand.HELPER, appName("../helper/src/main/res/values/strings.xml"))
        assertEquals(Brand.LINK, appName("../link/src/main/res/values/strings.xml"))
        val app = File("src/main/res/values/brand_strings.xml").readText()
        assertTrue(app.contains(">${Brand.AMBIENT}<"))
        assertTrue(app.contains(">${Brand.AMBIENT_NOTIFICATIONS}<"))
    }

    @Test fun manifestsUseTheStringResource() {
        for (m in listOf("src/main", "../browser/src/main", "../store/src/main", "../weather/src/main", "../helper/src/main", "../link/src/main")) {
            val xml = File("$m/AndroidManifest.xml").readText()
            val app = Regex("""<application[^>]*""").find(xml)!!.value
            assertTrue(m, app.contains("android:label=\"@string/app_name\""))
        }
        val main = File("src/main/AndroidManifest.xml").readText()
        assertTrue(main.contains("@string/ambient_name"))
        assertTrue(main.contains("@string/ambient_notifications_name"))
        // Package and component ids are internal and must not follow the brand (saved TV state).
        assertTrue(File("build.gradle.kts").readText().contains("applicationId = \"dev.tvlauncher\""))
    }

    /** Every module ships the same version, set once in gradle.properties. */
    @Test fun oneVersionForEveryModule() {
        val props = File("../gradle.properties").readText()
        assertTrue(props.contains("horizon.versionName=1.0.1"))
        assertTrue(props.contains("horizon.versionCode=2"))
        for (m in listOf(".", "../browser", "../store", "../weather", "../helper", "../link")) {
            val gradle = File("$m/build.gradle.kts").readText()
            assertTrue(m, gradle.contains("gradleProperty(\"horizon.versionName\")"))
            assertTrue(m, gradle.contains("gradleProperty(\"horizon.versionCode\")"))
        }
    }

    /**
     * No user-facing literal or resource in any module still says an old product name: the first
     * names (TV Launcher, Orbit, Get Apps...), the short-lived Mintaka / M02 or the
     * never-shipped Zephivane / Z02. A line that must keep
     * an old name for compatibility (reading an old folder) says so with a `// legacy name` comment.
     */
    @Test fun noOldProductNameOnScreen() {
        val old = Regex("""TV Launcher|TV launcher|Launcher Helper|Launcher Weather|Get Apps|\bOrbit\b|Mintaka|MINTAKA|\bM02|Zephivane|ZEPHIVANE|\bZ02""")
        val literal = Regex(""""(?:[^"\\]|\\.)*"""")
        val hits = ArrayList<String>()
        for (m in listOf("src/main", "../helper/src/main", "../store/src/main", "../browser/src/main", "../weather/src/main", "../link/src/main")) {
            File(m).walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml", "html", "txt") }.forEach { f ->
                // XML / HTML comments (also multi-line ones) are not shown; blank them but keep line numbers.
                val text = if (f.extension == "xml" || f.extension == "html") f.readText().replace(Regex("<!--[\\s\\S]*?-->")) { c -> c.value.replace(Regex("[^\n]"), " ") }
                else f.readText()
                text.lines().forEachIndexed { i, line ->
                    val t = line.trim()
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                    if (f.extension == "kt" && line.trimEnd().endsWith("// legacy name")) return@forEachIndexed
                    val parts = if (f.extension == "kt") literal.findAll(line).map { it.value }.toList() else listOf(line)
                    parts.filter { old.containsMatchIn(it) }.forEach { hits += "${f.path}:${i + 1}: $it" }
                }
            }
        }
        assertEquals(emptyList<String>(), hits)
    }
}

/** H02 Store and H02 Browser artwork is the generated H02 look (fonts/companion_art.py), never the old marks. */
class CompanionArtTest {
    @Test fun storeAndBrowserDrawTheH02Mark() {
        val art = listOf(
            "../store/src/main/res/drawable/banner.xml", "../store/src/main/res/drawable/ic_launcher_foreground.xml",
            "../store/src/main/res/drawable/wordmark.xml", "../store/src/main/res/drawable/ic_launcher_legacy.xml",
            "../browser/src/main/res/drawable/banner_browser.xml", "../browser/src/main/res/drawable/brand_mark.xml",
            "../browser/src/main/res/drawable/brand_wordmark.xml", "../browser/src/main/res/drawable/ic_launcher_fg.xml",
            "../browser/src/main/res/mipmap-anydpi/ic_launcher.xml"
        )
        for (f in art) {
            val xml = File(f).readText()
            assertTrue(f, xml.contains("companion_art.py") && xml.contains("Unbounded Medium"))
            assertFalse(f, Regex("Get Apps|Orbit|M02|Mintaka|Z02|Zephivane").containsMatchIn(xml))
        }
        val gen = File("../fonts/companion_art.py").readText()
        for (name in listOf("H02 Store", "H02 Browser", "H02 Weather", "H02 Helper", "H02 Link")) assertTrue(name, gen.contains(name))
        for (f in listOf("../weather/src/main/res/drawable/banner_weather.xml", "../helper/src/main/res/drawable/banner.xml",
                "../link/src/main/res/drawable/banner.xml")) {
            assertTrue(f, File(f).readText().contains("companion_art.py"))
        }
        assertTrue(File("src/main/res/drawable/tv_banner.xml").readText().contains("HORIZON caption"))
    }
}

class BrandFontTest {
    private val asset = File("src/main/assets/${BrandFont.ASSET}")

    @Test fun assetIsASmallRealTtf() {
        assertTrue("missing ${asset.absolutePath}", asset.isFile)
        val magic = asset.inputStream().use { s -> ByteArray(4).also { b -> s.read(b) } }
        assertTrue(magic.contentEquals(byteArrayOf(0, 1, 0, 0)))
        assertTrue("size ${asset.length()}", asset.length() in 4_000L until 40_000L)
    }

    @Test fun assetIsThePinnedBuild() {
        // fonts/build-brand-font.sh output (Unbounded wght 500, wordmark subset).
        assertEquals("562aaee867f28f5981dea7e7ab926ef5c89ced36f6bf2ee3a1f90f2294a89273", sha256(asset.readBytes()))
    }

    @Test fun licenseIsVerbatimOfficialText() {
        val f = File("src/main/res/raw/brand_font_license_ofl.txt")
        // google/fonts @ 47d54ca1ebb14fd864695f1d39dc734f39017c33, ofl/unbounded/OFL.txt
        assertEquals("31e5d4e83955e7103c34570dd49b0570ef490800bd65b42923c0dd02445263b3", sha256(f.readBytes()))
        val text = f.readText()
        assertTrue(text.contains("SIL OPEN FONT LICENSE"))
        assertTrue(text.contains("Copyright 2022 The Unbounded Project Authors"))
        assertFalse("no Reserved Font Name", text.contains("with Reserved Font Name"))
    }

    @Test fun noticeIsBundled() {
        assertTrue(FontNotices.BRAND.contains("Copyright 2022 The Unbounded Project Authors (https://github.com/googlefonts/unbounded)"))
        assertTrue(FontNotices.BRAND.contains("SIL Open Font License"))
        val notices = File("src/main/res/raw/third_party_notices.txt").readText()
        assertTrue(notices.contains("Unbounded typeface"))
        assertTrue(notices.contains("brand_font_license_ofl.txt"))
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
