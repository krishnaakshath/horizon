package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbiPickerTest {

    private val arm64Tv = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private val arm32Tv = listOf("armeabi-v7a", "armeabi")

    @Test fun detectsAbiTokensInFileNames() {
        assertEquals("arm64-v8a", AbiPicker.abiOf("app-arm64-v8a-release.apk"))
        assertEquals("arm64-v8a", AbiPicker.abiOf("App_ARM64.apk"))
        assertEquals("arm64-v8a", AbiPicker.abiOf("app-aarch64.apk"))
        assertEquals("armeabi-v7a", AbiPicker.abiOf("app-armeabi-v7a-release.apk"))
        assertEquals("armeabi-v7a", AbiPicker.abiOf("app-armv7.apk"))
        assertEquals("x86_64", AbiPicker.abiOf("app-x86_64.apk"))
        assertEquals("x86", AbiPicker.abiOf("app-x86-release.apk"))
        assertEquals(AbiPicker.UNIVERSAL, AbiPicker.abiOf("app-universal-release.apk"))
        assertNull(AbiPicker.abiOf("app-release.apk"))
    }

    @Test fun prefersPrimaryAbiOfDevice() {
        val names = listOf("a-x86.apk", "a-armeabi-v7a.apk", "a-arm64-v8a.apk", "a-universal.apk")
        assertEquals("a-arm64-v8a.apk", AbiPicker.rank(names, arm64Tv) { it }.first())
    }

    @Test fun thirtyTwoBitTvGetsV7() {
        val names = listOf("a-arm64-v8a.apk", "a-armeabi-v7a.apk", "a-universal.apk")
        val ranked = AbiPicker.rank(names, arm32Tv) { it }
        assertEquals(listOf("a-armeabi-v7a.apk", "a-universal.apk"), ranked)
    }

    @Test fun universalBeatsUnmarkedAndIncompatibleAreDropped() {
        val names = listOf("a-release.apk", "a-universal.apk", "a-x86_64.apk")
        assertEquals(listOf("a-universal.apk", "a-release.apk"), AbiPicker.rank(names, arm64Tv) { it })
    }

    @Test fun debugAndUnsignedAreDeprioritisedOrDropped() {
        val names = listOf("a-debug.apk", "a-release.apk", "a-release-unsigned.apk")
        assertEquals(listOf("a-release.apk", "a-debug.apk"), AbiPicker.rank(names, arm64Tv) { it })
    }

    @Test fun nonApkFilesAreIgnored() {
        val names = listOf("a.aab", "a.apk.sha256", "notes.txt", "a.apk")
        assertEquals(listOf("a.apk"), AbiPicker.rank(names, arm64Tv) { it })
    }

    @Test fun emptyAbiListStillReturnsUniversalCandidates() {
        val names = listOf("a-arm64-v8a.apk", "a.apk")
        assertEquals(listOf("a.apk"), AbiPicker.rank(names, emptyList()) { it })
    }

    @Test fun isApkNameIsCaseInsensitive() {
        assertEquals(listOf("A.APK"), AbiPicker.rank(listOf("A.APK"), arm64Tv) { it })
    }
}
