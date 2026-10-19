package dev.tvlauncher.ui

import android.content.pm.ApplicationInfo

/** Hero subtitle text. Only media/entertainment categories are named; system apps get none. */
object HeroText {
    const val OPEN = "Press OK to open"
    const val OPTIONS = "Press Down for options"
    private const val SEP = "   ·   "

    fun categoryWord(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_AUDIO -> "Music"
        ApplicationInfo.CATEGORY_GAME -> "Game"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        else -> null
    }

    fun subtitle(word: String?): String = listOfNotNull(word, OPEN, OPTIONS).joinToString(SEP)
}
