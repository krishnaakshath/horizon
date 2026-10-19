package dev.tvlauncher.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OobePlanTest {
    private val fresh = OobeProbe(account = AccountState.NONE_VISIBLE)

    @Test fun freshTv_getsEveryPage_inOrder() {
        assertEquals(listOf(OobePage.WELCOME, OobePage.NETWORK, OobePage.TIME, OobePage.ACCOUNT, OobePage.PERMISSIONS,
            OobePage.HOME, OobePage.APPS, OobePage.LOOK, OobePage.DONE), OobePlan.pages(fresh))
    }

    @Test fun satisfiedPages_areLeftOut() {
        val p = OobePlan.pages(OobeProbe(online = true, account = AccountState.SIGNED_IN, permissionsPending = false,
            isHome = true, favoritesEmpty = false))
        assertEquals(listOf(OobePage.WELCOME, OobePage.TIME, OobePage.LOOK, OobePage.DONE), p)
    }

    @Test fun restore_onlyWhenABackupIsFound_rightAfterWelcome() {
        assertFalse(OobePage.RESTORE in OobePlan.pages(fresh))
        assertEquals(OobePage.RESTORE, OobePlan.pages(fresh.copy(backupFound = true))[1])
    }

    @Test fun account_isNeverFaked() {
        // Unreadable and no screen to open: nothing honest to show, so no page.
        assertFalse(OobePage.ACCOUNT in OobePlan.pages(fresh.copy(account = AccountState.UNREADABLE)))
        assertTrue(OobePage.ACCOUNT in OobePlan.pages(fresh.copy(account = AccountState.UNREADABLE, accountScreen = true)))
        assertEquals("Managed by the TV", OobeCopy.account(AccountState.UNREADABLE, 0))
        assertEquals("Signed in (2 accounts)", OobeCopy.account(AccountState.SIGNED_IN, 2))
    }

    @Test fun resume_rememberedPage_elseFirstUnfinished() {
        val pages = OobePlan.pages(fresh)
        assertEquals(OobePage.WELCOME, OobePlan.resume(pages, OobeMemory()))
        assertEquals(OobePage.APPS, OobePlan.resume(pages, OobeMemory(current = OobePage.APPS)))
        val m = OobeMemory(done = setOf(OobePage.WELCOME, OobePage.NETWORK), skipped = setOf(OobePage.TIME))
        assertEquals(OobePage.ACCOUNT, OobePlan.resume(pages, m))
        // A remembered page that is no longer needed here (now online): first unfinished instead.
        val online = OobePlan.pages(fresh.copy(online = true))
        assertEquals(OobePage.WELCOME, OobePlan.resume(online, OobeMemory(current = OobePage.NETWORK)))
        // Everything done: the summary.
        assertEquals(OobePage.DONE, OobePlan.resume(pages, OobeMemory(done = pages.toSet())))
    }

    @Test fun next_previous_skip_finish() {
        val pages = OobePlan.pages(fresh)
        var m = OobeMemory()
        m = OobePlan.finish(m, OobePage.WELCOME)
        assertEquals(OobePage.NETWORK, OobePlan.next(pages, OobePage.WELCOME, m))
        m = OobePlan.skip(m, OobePage.TIME)
        assertEquals(OobePage.ACCOUNT, OobePlan.next(pages, OobePage.NETWORK, m))
        assertEquals(OobePage.DONE, OobePlan.next(pages, OobePage.LOOK, m))
        assertNull(OobePlan.previous(pages, OobePage.WELCOME))
        assertEquals(OobePage.WELCOME, OobePlan.previous(pages, OobePage.NETWORK))
        // Finishing a skipped page un-skips it.
        assertFalse(OobePage.TIME in OobePlan.finish(m, OobePage.TIME).skipped)
        assertEquals("Step 1 of 8", OobePlan.position(pages, OobePage.WELCOME))
        assertEquals("", OobePlan.position(pages, OobePage.DONE))
    }

    @Test fun copy_isPlainWords() {
        val banned = Regex("""\badb\b|developer|command|shell""", RegexOption.IGNORE_CASE)
        for (p in OobePage.values()) {
            assertFalse(banned.containsMatchIn(OobeCopy.title(p) + OobeCopy.lead(p)))
        }
    }
}
