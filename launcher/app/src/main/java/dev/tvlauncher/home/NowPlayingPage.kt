package dev.tvlauncher.home

import android.content.Context
import android.view.View
import dev.tvlauncher.ambient.NotificationAccessCard
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.nowplaying.NowPlayingAccess
import dev.tvlauncher.nowplaying.NowPlayingPageRows
import dev.tvlauncher.setup.SetupStatus

/**
 * Settings > Home screen > Now Playing: the feature switch, where it shows (mini player, art
 * backdrop, ambient) and the notification-access status with the setup one-liner. Rows and texts:
 * [NowPlayingPageRows].
 */
object NowPlayingPage {
    val SPEC = PageSpec(NowPlayingPageRows.ID, NowPlayingPageRows.PRIMARY)

    fun build(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SPEC)
        val store = DiscoverStore(context)
        val s = store.loadNowPlaying()
        val granted = NowPlayingAccess.isGranted(context)
        page.lead(NowPlayingPageRows.LEAD)
        for (key in NowPlayingPageRows.rows(s)) {
            if (key == NowPlayingPageRows.ACCESS) {
                accessRow(context, nav, page, granted)
                continue
            }
            page.row(key, NowPlayingPageRows.title(key), RowKind.Toggle(NowPlayingPageRows.isOn(s, key)) { _ ->
                store.saveNowPlaying(NowPlayingPageRows.toggle(store.loadNowPlaying(), key))
                if (key == NowPlayingPageRows.ENABLED) nav.refresh()
            }, subtitle = NowPlayingPageRows.subtitle(key))
        }
        if (WaveTwoSections.accessCardShown(s.nowPlayingEnabled, granted)) {
            page.add(NotificationAccessCard(context, NotificationAccessCard.NOW_PLAYING_TITLE, NotificationAccessCard.NOW_PLAYING_BODY))
        }
        return page
    }

    private fun accessRow(context: Context, nav: SettingsNav, page: SettingsPage, granted: Boolean) {
        val title = NowPlayingPageRows.ACCESS_TITLE
        val value = NowPlayingPageRows.accessValue(granted)
        when {
            granted -> page.row(NowPlayingPageRows.ACCESS, title, RowKind.Info(value))
            SetupStatus.canWriteSecureSettings(context) -> page.row(NowPlayingPageRows.ACCESS, title, RowKind.Action(value) {
                nav.task({ SetupStatus.autoConfigure(context.applicationContext) }) { _ -> nav.refreshCurrent() }
            }, subtitle = "Press OK to allow it now")
            else -> page.row(NowPlayingPageRows.ACCESS, title, RowKind.Info(value),
                subtitle = SetupStatus.notificationAccessHint(settingsOpened = false))
        }
    }
}
