package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FdroidIndexParserTest {

    private fun hash(c: Char) = c.toString().repeat(64)

    private fun version(code: Long, name: String, abis: List<String>?, sha: String, minSdk: Int = 21,
                        extra: String = "", features: String = "") = """
        "$sha": {
          "added": 1, "file": {"name": "/$name", "sha256": "$sha", "size": ${code * 10}},
          "manifest": {
            ${abis?.let { "\"nativecode\": [" + it.joinToString(",") { a -> "\"$a\"" } + "]," } ?: ""}
            "versionName": "v$code", "versionCode": $code,
            "usesSdk": {"minSdkVersion": $minSdk, "targetSdkVersion": 34},
            "signer": {"sha256": ["${hash('5')}"]},
            ${if (features.isNotEmpty()) "\"features\": [$features]," else ""}
            "usesPermission": [{"name": "android.permission.INTERNET"}]
          }
          $extra
        }
    """

    private val index = """
    {
      "repo": {"name": {"en-US": "F-Droid"}, "timestamp": 1},
      "packages": {
        "org.videolan.vlc": {
          "metadata": {"name": {"de": "VLC-de", "en-US": "VLC"}, "summary": {"en-US": "Media player"},
                       "categories": ["Multimedia"], "license": "GPL-3.0", "description": {"en-US": "${"long ".repeat(200)}"}},
          "versions": {
            ${version(108, "org.videolan.vlc_108.apk", listOf("x86_64"), hash('a'))},
            ${version(106, "org.videolan.vlc_106.apk", listOf("arm64-v8a"), hash('b'))},
            ${version(105, "org.videolan.vlc_105.apk", listOf("armeabi-v7a"), hash('c'))}
          }
        },
        "com.example.tvapp": {
          "versions": {
            ${version(3, "com.example.tvapp_3.apk", null, hash('d'), extra = ", \"releaseChannels\": [\"Beta\"]")},
            ${version(2, "com.example.tvapp_2.apk", null, hash('e'), extra = ", \"antiFeatures\": {\"NonFreeNet\": {}}")}
          },
          "metadata": {"name": {"fr": "Télé"}, "tvBanner": {"en-US": {"name": "/x.png"}}, "categories": ["Games"]}
        },
        "com.example.phoneonly": {
          "metadata": {"name": {"en-US": "Phone Only"}, "summary": "plain string summary"},
          "versions": { ${version(9, "com.example.phoneonly_9.apk", null, hash('f'))} }
        },
        "com.example.newsdk": {
          "metadata": {"name": {"en-US": "Too New"}},
          "versions": { ${version(1, "com.example.newsdk_1.apk", null, hash('1'), minSdk = 33)} }
        },
        "com.example.leanbackfeature": {
          "metadata": {"name": {"en-US": "Leanback Feature"}},
          "versions": { ${version(4, "com.example.leanbackfeature_4.apk", null, hash('2'),
                features = "{\"name\": \"android.software.leanback\"}")} }
        },
        "com.example.evil": {
          "metadata": {"name": {"en-US": "Traversal"}},
          "versions": { ${version(1, "../../etc/passwd.apk", null, hash('3'))} }
        },
        "com.example.badhash": {
          "metadata": {"name": {"en-US": "Bad hash"}},
          "versions": { ${version(1, "com.example.badhash_1.apk", null, "nothex")} }
        }
      }
    }
    """

    private val tvArm64 = FdroidIndexParser.Config(
        abis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
        sdkInt = 28,
        curated = setOf("org.videolan.vlc"),
    )

    private fun parse(cfg: FdroidIndexParser.Config = tvArm64, json: String = index) =
        FdroidIndexParser(cfg).parse(StringJsonTokenSource(json))

    @Test fun picksHighestCompatibleVersionForDeviceAbi() {
        val vlc = parse().apps.first { it.packageName == "org.videolan.vlc" }
        assertEquals(106L, vlc.versionCode)
        assertEquals("org.videolan.vlc_106.apk", vlc.apkName)
        assertEquals("https://f-droid.org/repo/org.videolan.vlc_106.apk", vlc.apkUrl)
        assertEquals(hash('b'), vlc.sha256)
        assertEquals(hash('5'), vlc.signerSha256)
        assertEquals("VLC", vlc.name)
        assertEquals("Media player", vlc.summary)
        assertTrue(vlc.curated)
        assertEquals(1060L, vlc.size)
    }

    @Test fun thirtyTwoBitDeviceGetsV7Build() {
        val vlc = parse(tvArm64.copy(abis = listOf("armeabi-v7a", "armeabi"))).apps.first { it.packageName == "org.videolan.vlc" }
        assertEquals(105L, vlc.versionCode)
    }

    @Test fun metadataAfterVersionsAndNonEnglishNamesAreHandled() {
        val tv = parse().apps.first { it.packageName == "com.example.tvapp" }
        assertEquals("Télé", tv.name)
        assertTrue(tv.tvReady)
        assertEquals(listOf("Games"), tv.categories)
    }

    @Test fun betaReleaseChannelSkippedAndAntiFeaturesKept() {
        val tv = parse().apps.first { it.packageName == "com.example.tvapp" }
        assertEquals(2L, tv.versionCode)
        assertEquals(listOf("NonFreeNet"), tv.antiFeatures)
        val beta = parse(tvArm64.copy(includeBeta = true)).apps.first { it.packageName == "com.example.tvapp" }
        assertEquals(3L, beta.versionCode)
    }

    @Test fun leanbackFeatureMarksTvReady() {
        assertTrue(parse().apps.first { it.packageName == "com.example.leanbackfeature" }.tvReady)
        assertFalse(parse().apps.first { it.packageName == "com.example.phoneonly" }.tvReady)
    }

    @Test fun incompatibleUnsafeAndMalformedEntriesAreDropped() {
        val ids = parse().apps.map { it.packageName }.toSet()
        assertFalse("minSdk too high", "com.example.newsdk" in ids)
        assertFalse("path traversal apk name", "com.example.evil" in ids)
        assertFalse("bad hash", "com.example.badhash" in ids)
        assertEquals(7, parse().packagesSeen)
    }

    @Test fun plainStringLocalisedFieldIsAccepted() {
        assertEquals("plain string summary", parse().apps.first { it.packageName == "com.example.phoneonly" }.summary)
    }

    @Test fun tvOnlyModeKeepsTvCuratedAndQueryMatches() {
        val cfg = tvArm64.copy(keepAllCompatible = false)
        assertEquals(setOf("org.videolan.vlc", "com.example.tvapp", "com.example.leanbackfeature"),
            parse(cfg).apps.map { it.packageName }.toSet())
        val q = parse(cfg.copy(query = "phone")).apps.map { it.packageName }.toSet()
        assertTrue("com.example.phoneonly" in q)
    }

    @Test fun byteBudgetBoundsNonPriorityEntries() {
        val r = parse(tvArm64.copy(maxBytes = 1))
        val ids = r.apps.map { it.packageName }.toSet()
        assertTrue("org.videolan.vlc" in ids)
        assertTrue("com.example.tvapp" in ids)
        assertFalse("com.example.phoneonly" in ids)
        assertTrue(r.truncated)
    }

    @Test fun entryCapIsHard() {
        val r = parse(tvArm64.copy(maxEntries = 1))
        assertEquals(1, r.apps.size)
        assertTrue(r.truncated)
    }

    @Test fun truncatedStreamReturnsPartialResultWithErrorAndNeverThrows() {
        val cut = index.substring(0, index.indexOf("com.example.phoneonly"))
        val r = parse(json = cut)
        assertNotNull(r.error)
        assertTrue(r.apps.any { it.packageName == "org.videolan.vlc" })
    }

    @Test fun junkNeverThrows() {
        for (junk in listOf("", "[]", "42", "{\"packages\": 5}", "{\"packages\": {\"a\": 7}}",
            "{\"packages\": {\"a\": {\"versions\": [1,2]}}}", "{\"packages\": {\"a\": {\"versions\": {\"x\": {\"file\": 3}}}}}",
            "{\"packages\": {\"a\": {\"metadata\": {\"name\": 3}, \"versions\": {\"x\": {\"manifest\": {\"versionCode\": \"abc\"}}}}}}",
            "{{{{")) {
            val r = parse(json = junk)
            assertTrue(r.apps.isEmpty())
        }
    }

    @Test fun cancellationStopsEarly() {
        var n = 0
        val r = FdroidIndexParser(tvArm64).parse(StringJsonTokenSource(index)) { ++n > 1 }
        assertEquals("cancelled", r.error)
        assertTrue(r.packagesSeen <= 1)
    }

    @Test fun catalogRoundTripsThroughCodec() {
        val apps = parse().apps
        val json = FdroidCatalogCodec.encode(apps, savedAtMillis = 1234L)
        val back = FdroidCatalogCodec.decode(json)!!
        assertEquals(1234L, back.savedAtMillis)
        assertEquals(apps, back.apps)
        assertNull(FdroidCatalogCodec.decode("garbage"))
    }

    @Test fun catalogStaleness() {
        val day = 24L * 3600 * 1000
        assertFalse(FdroidCatalogCodec.isStale(savedAt = 0, now = day - 1))
        assertTrue(FdroidCatalogCodec.isStale(savedAt = 0, now = day))
        assertTrue(FdroidCatalogCodec.isStale(savedAt = day * 2, now = day)) // clock went backwards
    }

    @Test fun searchMatchesNameAndPackageIdCaseInsensitive() {
        val apps = parse().apps
        assertEquals(listOf("org.videolan.vlc"), FdroidCatalogCodec.search(apps, "vlc").map { it.packageName })
        assertEquals(listOf("org.videolan.vlc"), FdroidCatalogCodec.search(apps, "ORG.VIDEOLAN").map { it.packageName })
        // empty query: TV-ready / curated first
        val all = FdroidCatalogCodec.search(apps, "")
        assertTrue(all.take(3).all { it.tvReady || it.curated })
        assertFalse(all.any { it.packageName == "com.example.phoneonly" })
    }
}
