package dev.tvlauncher.ui

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroTextTest {
    @Test fun mediaCategories_haveWords() {
        assertEquals("Video", HeroText.categoryWord(ApplicationInfo.CATEGORY_VIDEO))
        assertEquals("Music", HeroText.categoryWord(ApplicationInfo.CATEGORY_AUDIO))
        assertEquals("Game", HeroText.categoryWord(ApplicationInfo.CATEGORY_GAME))
        assertEquals("News", HeroText.categoryWord(ApplicationInfo.CATEGORY_NEWS))
        assertEquals("Social", HeroText.categoryWord(ApplicationInfo.CATEGORY_SOCIAL))
    }

    @Test fun otherCategories_haveNoWord() {
        assertNull(HeroText.categoryWord(ApplicationInfo.CATEGORY_PRODUCTIVITY))
        assertNull(HeroText.categoryWord(ApplicationInfo.CATEGORY_IMAGE))
        assertNull(HeroText.categoryWord(ApplicationInfo.CATEGORY_MAPS))
        assertNull(HeroText.categoryWord(ApplicationInfo.CATEGORY_UNDEFINED))
        assertNull(HeroText.categoryWord(-1))
    }

    @Test fun subtitle_withAndWithoutCategory() {
        assertEquals("Video   ·   Press OK to open   ·   Press Down for options", HeroText.subtitle("Video"))
        assertEquals("Press OK to open   ·   Press Down for options", HeroText.subtitle(null))
    }
}
