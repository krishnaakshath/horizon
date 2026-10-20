package dev.tvlauncher.home

import android.content.Context
import android.os.Bundle
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.link.LinkStatus
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.nowplaying.SpotifyArtClient
import dev.tvlauncher.nowplaying.SpotifyArtPolicy
import dev.tvlauncher.system.TextEntrySheet
import dev.tvlauncher.system.contentRoot
import dev.tvlauncher.system.lite

/**
 * Settings > Home screen > Now Playing > Spotify artwork: the optional Spotify app key for the
 * "Artist banner" style. The key goes straight to M02 Link (which owns the network) through its
 * signature-protected provider; the launcher never stores, backs up or shows the secret.
 */
object SpotifyKeyPage {
    const val ID = "home.spotifyKey"
    const val STATUS = "sp.status"
    const val CLIENT_ID = "sp.id"
    const val SECRET = "sp.secret"
    const val TEST = "sp.test"
    const val REMOVE = "sp.remove"
    val SPEC = PageSpec(ID, listOf(STATUS, CLIENT_ID, SECRET, TEST, REMOVE))

    const val LEAD = "Optional. Without a key, covers come from the music app. With a free app key from Spotify's online " +
        "dashboard, the Artist banner style shows the artist's picture."

    fun build(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SPEC)
        page.lead(LEAD)
        if (!LinkStatus.read(context).installed) {
            page.row(STATUS, "Key status", RowKind.Info("Needs M02 Link"),
                subtitle = "Install M02 Link: the launcher itself has no internet access", iconRes = R.drawable.ic_set_about)
            return page
        }
        // A local provider query (no network), like the Smart home page's.
        val (configured, state, hint) = SpotifyArtClient.status(context)
        rows(context, nav, page, configured, state, hint)
        return page
    }

    private fun rows(context: Context, nav: SettingsNav, page: SettingsPage, configured: Boolean, state: String?, hint: String) {
        page.row(STATUS, "Key status", RowKind.Info(SpotifyArtPolicy.stateText(configured, state)), iconRes = R.drawable.ic_set_check)
        page.row(CLIENT_ID, "Client ID", RowKind.Action(if (configured) hint else "Not set") { enter(context, nav) },
            subtitle = "Or paste it on the phone remote's More page", iconRes = R.drawable.ic_set_tune)
        page.row(SECRET, "Client secret", RowKind.Action(if (configured) "••••••••" else "Not set") { enter(context, nav) },
            subtitle = "Kept only in M02 Link on this TV; never backed up", iconRes = R.drawable.ic_sys_lock)
        if (configured) {
            page.row(TEST, "Test key", RowKind.Action(chevron = false) {
                nav.task({ SpotifyArtClient.call(context, LinkContract.METHOD_SPOTIFY_TEST, null)?.getString(LinkContract.EXTRA_STATE) }) { s ->
                    nav.notify(SpotifyArtPolicy.stateText(true, s)); nav.refreshCurrent()
                }
            }, iconRes = R.drawable.ic_set_about)
            page.row(REMOVE, "Remove key", RowKind.Action(chevron = false) {
                nav.task({ SpotifyArtClient.call(context, LinkContract.METHOD_SPOTIFY_REMOVE, null); SpotifyArtClient.status(context) }) { _ ->
                    nav.notify("Spotify key removed"); nav.refreshCurrent()
                }
            }, subtitle = "Deletes the key and the saved pictures", iconRes = R.drawable.ic_set_reset)
        }
    }

    /** Client ID, then the secret (masked), with the TV keyboard; saved and tested in M02 Link. */
    private fun enter(context: Context, nav: SettingsNav) {
        val host = contentRoot(context) ?: return
        val idSheet = TextEntrySheet(context, nav.lite())
        idSheet.show(host, TextEntrySheet.Request("Client ID", "From your app in Spotify's online dashboard", TextEntrySheet.Kind.TEXT, "Next",
            hint = "32 letters and numbers", icon = R.drawable.ic_set_tune) { id ->
            val i = id.trim()
            if (!validCredential(i)) return@Request "That doesn't look like a Client ID."
            idSheet.dismiss()
            val secretSheet = TextEntrySheet(context, nav.lite())
            secretSheet.show(host, TextEntrySheet.Request("Client secret", "Shown once in your Spotify app's settings",
                TextEntrySheet.Kind.PASSWORD, "Save", hint = "Client secret", icon = R.drawable.ic_sys_lock) { secret ->
                val k = secret.trim()
                if (!validCredential(k)) return@Request "That doesn't look like a Client secret."
                secretSheet.dismiss()
                nav.notify("Checking the key…")
                nav.task({
                    SpotifyArtClient.call(context, LinkContract.METHOD_SPOTIFY_SET, i,
                        Bundle().apply { putString(LinkContract.EXTRA_VALUE, k) })?.getString(LinkContract.EXTRA_STATE)
                        .also { SpotifyArtClient.status(context) }
                }) { s -> nav.notify(SpotifyArtPolicy.stateText(true, s)); nav.refreshCurrent() }
                null
            })
            null
        })
    }

    /** Same rule as M02 Link: 16 to 64 letters and digits. */
    fun validCredential(s: String): Boolean = s.length in 16..64 && s.all { it.isLetterOrDigit() }
}
