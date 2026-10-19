package dev.tvlauncher.discover

import dev.tvlauncher.nowplaying.NowPlayingSettings

/**
 * Declarative rows for the "Apps & media" Settings page (the controller hosts it). Render each
 * [Row] by [Kind]; TOGGLE → [toggle]/[toggleNowPlaying], CHOICE "region" → [stepRegion],
 * ACTION "openDiscover" → show [DiscoverPanel], ACTION "restoreHidden" → [restoreHidden],
 * CARD "nowPlayingAccess" → host `dev.tvlauncher.ambient.NotificationAccessCard` (same listener,
 * same one-time grant as ambient notifications). Then save via [DiscoverStore].
 */
object DiscoverSettingsRows {
    enum class Kind { ACTION, TOGGLE, CHOICE, CARD, HEADER }

    data class Row(
        val key: String,
        val title: String,
        val kind: Kind,
        val value: String = "",
        val visible: Boolean = true,
        val hint: String? = null
    )

    /** Region choices for the stepper; null = automatic. */
    val REGIONS: List<String?> = listOf(null, "IN", "US", "GB", "CA", "AU", "DE", "FR", "ES", "IT", "BR", "MX", "JP")

    const val ADB_ALLOW = "adb shell cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService"
    const val ACCESS_HINT =
        "Now Playing needs the same one-time permission as ambient notifications."

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    fun rows(d: DiscoverSettings, n: NowPlayingSettings, accessGranted: Boolean, detectedRegion: String?): List<Row> {
        val auto = RegionGuess.normalize(detectedRegion)
        return listOf(
            Row("discoverHeader", "Discover apps", Kind.HEADER),
            Row("openDiscover", "Browse popular apps", Kind.ACTION, hint = "Opens the Play Store page of the app you pick."),
            Row("showGetMoreAppsTile", "\"Get more apps\" tile at the end of the shelf", Kind.TOGGLE, onOff(d.showGetMoreAppsTile)),
            Row("region", "Region for suggestions", Kind.CHOICE,
                d.region ?: if (auto != null) "Automatic ($auto)" else "Automatic"),
            Row("restoreHidden", "Show hidden suggestions again", Kind.ACTION, "${d.hiddenCatalogIds.size} hidden",
                visible = d.hiddenCatalogIds.isNotEmpty()),
            Row("nowPlayingHeader", "Now Playing", Kind.HEADER),
            Row("nowPlayingEnabled", "Show what's playing (Spotify, YouTube Music…)", Kind.TOGGLE, onOff(n.nowPlayingEnabled),
                hint = "Title, artist and album art stay in memory only; never saved or logged."),
            Row("nowPlayingAccess", "Notification access", Kind.CARD,
                visible = n.nowPlayingEnabled && !accessGranted, hint = ACCESS_HINT),
            Row("nowPlayingShowOnHome", "Show on Home", Kind.TOGGLE, onOff(n.showOnHome),
                visible = n.nowPlayingEnabled && accessGranted),
            Row("nowPlayingShowOnAmbient", "Show on the ambient screen", Kind.TOGGLE, onOff(n.showOnAmbient),
                visible = n.nowPlayingEnabled && accessGranted)
        )
    }

    fun toggle(d: DiscoverSettings, key: String): DiscoverSettings = when (key) {
        "showGetMoreAppsTile" -> d.copy(showGetMoreAppsTile = !d.showGetMoreAppsTile)
        else -> d
    }

    fun toggleNowPlaying(n: NowPlayingSettings, key: String): NowPlayingSettings = when (key) {
        "nowPlayingEnabled" -> n.copy(nowPlayingEnabled = !n.nowPlayingEnabled)
        "nowPlayingShowOnHome" -> n.copy(showOnHome = !n.showOnHome)
        "nowPlayingShowOnAmbient" -> n.copy(showOnAmbient = !n.showOnAmbient)
        else -> n
    }

    fun stepRegion(d: DiscoverSettings, delta: Int): DiscoverSettings {
        val i = REGIONS.indexOf(d.region).let { if (it < 0) 0 else it }
        return d.copy(region = REGIONS[(i + delta).coerceIn(0, REGIONS.size - 1)])
    }

    fun hide(d: DiscoverSettings, catalogId: String): DiscoverSettings =
        if (d.hiddenCatalogIds.size >= DiscoverSettings.MAX_HIDDEN) d else d.copy(hiddenCatalogIds = d.hiddenCatalogIds + catalogId)

    fun restoreHidden(d: DiscoverSettings): DiscoverSettings = d.copy(hiddenCatalogIds = emptySet())
}
