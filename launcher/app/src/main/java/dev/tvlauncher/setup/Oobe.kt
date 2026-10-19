package dev.tvlauncher.setup

import dev.tvlauncher.brand.Brand

/**
 * The launcher's own out-of-box experience: one page at a time, Skip on every page, resumable,
 * re-runnable. [OobePlan] decides which pages THIS TV needs (pages already satisfied here are
 * left out) and where to resume. Pure.
 */
enum class OobePage { WELCOME, RESTORE, NETWORK, TIME, ACCOUNT, PERMISSIONS, HOME, APPS, LOOK, DONE }

/** What the Android account service lets the launcher see. Never guessed. */
enum class AccountState { SIGNED_IN, NONE_VISIBLE, UNREADABLE }

data class OobeProbe(
    /** A configuration backup was found on a USB stick or on this TV. */
    val backupFound: Boolean = false,
    val online: Boolean = false,
    val account: AccountState = AccountState.UNREADABLE,
    /** The TV has an accounts screen to open. */
    val accountScreen: Boolean = false,
    /** Any permission step is still one press away (see [SetupPlan]). */
    val permissionsPending: Boolean = true,
    val isHome: Boolean = false,
    val favoritesEmpty: Boolean = true
)

data class OobeMemory(
    val done: Set<OobePage> = emptySet(),
    val skipped: Set<OobePage> = emptySet(),
    /** The page shown when the flow was left (resume point). */
    val current: OobePage? = null
)

object OobePlan {
    /** Pages for this TV, in order. Pages always shown once: WELCOME, TIME, LOOK, DONE. */
    fun pages(p: OobeProbe): List<OobePage> = OobePage.values().filter { page ->
        when (page) {
            OobePage.RESTORE -> p.backupFound
            OobePage.NETWORK -> !p.online
            OobePage.ACCOUNT -> p.account != AccountState.SIGNED_IN && (p.account == AccountState.NONE_VISIBLE || p.accountScreen)
            OobePage.PERMISSIONS -> p.permissionsPending
            OobePage.HOME -> !p.isHome
            OobePage.APPS -> p.favoritesEmpty
            else -> true
        }
    }

    /** Where to (re)start: the remembered page if still needed, else the first page not finished. */
    fun resume(pages: List<OobePage>, m: OobeMemory): OobePage {
        m.current?.let { c -> if (c in pages && c !in m.done && c !in m.skipped) return c }
        return pages.firstOrNull { it !in m.done && it !in m.skipped && it != OobePage.DONE } ?: OobePage.DONE
    }

    /** The page after [from] that still needs doing, else DONE. */
    fun next(pages: List<OobePage>, from: OobePage, m: OobeMemory): OobePage {
        val i = pages.indexOf(from)
        return pages.drop(if (i < 0) 0 else i + 1).firstOrNull { it !in m.done && it !in m.skipped } ?: OobePage.DONE
    }

    /** The page before [from] (Back), or null on the first page. */
    fun previous(pages: List<OobePage>, from: OobePage): OobePage? {
        val i = pages.indexOf(from)
        return if (i > 0) pages[i - 1] else null
    }

    /** "Step 3 of 8" (DONE is not counted). */
    fun position(pages: List<OobePage>, page: OobePage): String {
        val counted = pages.filter { it != OobePage.DONE }
        val i = counted.indexOf(page)
        return if (i < 0) "" else "Step ${i + 1} of ${counted.size}"
    }

    fun finish(m: OobeMemory, page: OobePage) = m.copy(done = m.done + page, skipped = m.skipped - page)
    fun skip(m: OobeMemory, page: OobePage) = m.copy(skipped = m.skipped + page)
    /** Re-running from Settings starts clean (choices already made stay, since they live in their own stores). */
    fun restart() = OobeMemory()
}

/** Page titles and one-line leads: plain words, no jargon. */
object OobeCopy {
    fun title(p: OobePage) = when (p) {
        OobePage.WELCOME -> "Welcome to ${Brand.SHORT}"
        OobePage.RESTORE -> "Restore your setup"
        OobePage.NETWORK -> "Get connected"
        OobePage.TIME -> "Time and place"
        OobePage.ACCOUNT -> "Your account"
        OobePage.PERMISSIONS -> "A few permissions"
        OobePage.HOME -> "Home button"
        OobePage.APPS -> "Pick your apps"
        OobePage.LOOK -> "Look and feel"
        OobePage.DONE -> "${Brand.SHORT} is ready"
    }

    fun lead(p: OobePage) = when (p) {
        OobePage.WELCOME -> "Let's set up ${Brand.SHORT}. It takes about a minute, and every step can be skipped."
        OobePage.RESTORE -> "A saved setup was found. Restore it to get your apps, look and settings back."
        OobePage.NETWORK -> "Connect to Wi-Fi or a cable for apps, weather and updates."
        OobePage.TIME -> "Your clock and city, for weather and lighting that follows the sun."
        OobePage.ACCOUNT -> "Your account lives in the TV's own settings. Here is what the launcher can see."
        OobePage.PERMISSIONS -> "Each one is optional and explained in one line."
        OobePage.HOME -> "Make the Home button open ${Brand.SHORT}."
        OobePage.APPS -> "Favorites go first on your home screen."
        OobePage.LOOK -> "Choose colors, tile size, lighting, screensaver and sounds."
        OobePage.DONE -> "Here is what is set up. You can change anything later in Settings."
    }

    fun account(state: AccountState, count: Int) = when (state) {
        AccountState.SIGNED_IN -> if (count == 1) "Signed in" else "Signed in ($count accounts)"
        AccountState.NONE_VISIBLE -> "No account is visible to the launcher"
        AccountState.UNREADABLE -> "Managed by the TV"
    }

    const val CONTINUE = "Continue"
    const val BACK = "Back"
}
