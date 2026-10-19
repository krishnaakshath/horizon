package dev.tvlauncher.discover

import dev.tvlauncher.brand.Brand
import dev.tvlauncher.ui.BrandColors

enum class AppCategory(val title: String) {
    VIDEO("Video"),
    MUSIC("Music"),
    LIVE_TV("Live TV"),
    GAMES("Games"),
    KIDS("Kids"),
    NEWS("News"),
    SPORTS("Sports"),
    TOOLS("Tools"),
    REGIONAL("Regional")
}

/** Where an app can be installed from. */
sealed class Source {
    object Play : Source() { override fun toString() = "Play" }
    data class Fdroid(val packageName: String) : Source()
    data class Github(val repo: String) : Source()
}

/**
 * One curated catalog entry. [regions] are ISO 3166-1 alpha-2 codes; empty = everywhere.
 * [tvReady] is true only when the Play listing offered the TV form factor on the verification
 * date (see assets/popular_apps_verification.txt); never claimed otherwise. [onPlay] = the
 * package returned HTTP 200 on play.google.com at verification time.
 */
data class PopularApp(
    val id: String,
    val label: String,
    val packageName: String,
    val category: AppCategory,
    val brandColor: Int,
    val regions: Set<String>,
    val tvReady: Boolean,
    val source: Source,
    val note: String?,
    /** 0..100, higher first. A rough, hand-set popularity order, not live data. */
    val popularity: Int = 50,
    val onPlay: Boolean = true
)

/**
 * Curated, offline catalog of well-known apps. Every package id was checked against
 * play.google.com (HTTP 200) on [VERIFIED_ON]; ids that returned 404 were dropped (listed in the
 * verification asset). Brand colors reuse [BrandColors] where it knows the package.
 */
object PopularApps {
    const val VERIFIED_ON = "2026-10-20"

    private val NONE = emptySet<String>()
    private val IN = setOf("IN")
    private val US = setOf("US")
    private val GB = setOf("GB")

    private fun app(
        id: String, label: String, pkg: String, cat: AppCategory, color: Long, pop: Int,
        tv: Boolean = true, regions: Set<String> = NONE, source: Source = Source.Play, note: String? = null
    ) = PopularApp(id, label, pkg, cat, BrandColors.known(pkg) ?: color.toInt(), regions, tv, source, note, pop)

    private const val PHONE_LISTING = "Play listing showed no TV form factor when verified; may be a phone app on some TVs."

    val all: List<PopularApp> = listOf(
        // Video
        app("netflix", "Netflix", "com.netflix.ninja", AppCategory.VIDEO, 0xFFE50914, 100),
        app("youtube", "YouTube", "com.google.android.youtube.tv", AppCategory.VIDEO, 0xFFFF0000, 99),
        app("prime_video", "Prime Video", "com.amazon.amazonvideo.livingroom", AppCategory.VIDEO, 0xFF00A8E1, 95),
        app("disney_plus", "Disney+", "com.disney.disneyplus", AppCategory.VIDEO, 0xFF113CCF, 93),
        app("apple_tv", "Apple TV", "com.apple.atve.androidtv.appletv", AppCategory.VIDEO, 0xFF1C1C1E, 85),
        app("hbo_max", "HBO Max", "com.wbd.stream", AppCategory.VIDEO, 0xFF002BE7, 84),
        app("hulu", "Hulu", "com.hulu.livingroomplus", AppCategory.VIDEO, 0xFF1CE783, 83, regions = US),
        app("peacock", "Peacock", "com.peacocktv.peacockandroid", AppCategory.VIDEO, 0xFF1A1A1A, 75, regions = US),
        app("paramount_plus", "Paramount+", "com.cbs.ott", AppCategory.VIDEO, 0xFF0064FF, 78),
        app("crunchyroll", "Crunchyroll", "com.crunchyroll.crunchyroid", AppCategory.VIDEO, 0xFFF47521, 76),
        app("plex", "Plex", "com.plexapp.android", AppCategory.VIDEO, 0xFFE5A00D, 80),
        app("jellyfin", "Jellyfin", "org.jellyfin.androidtv", AppCategory.VIDEO, 0xFFAA5CC3, 66,
            note = "Also in ${Brand.STORE} via F-Droid."),
        app("vlc", "VLC", "org.videolan.vlc", AppCategory.VIDEO, 0xFFFF8800, 74),
        app("kodi", "Kodi", "org.xbmc.kodi", AppCategory.VIDEO, 0xFF17B2E7, 70,
            source = Source.Fdroid("org.xbmc.kodi"), note = "Also on Google Play."),
        app("mx_player", "MX Player", "com.mxtech.videoplayer.ad", AppCategory.VIDEO, 0xFF1F6FEB, 72, regions = IN),
        app("stremio", "Stremio", "com.stremio.one", AppCategory.VIDEO, 0xFF7B5BF5, 60),
        app("mubi", "MUBI", "com.mubi", AppCategory.VIDEO, 0xFF2B2B2B, 45),
        app("viki", "Viki", "com.viki.android", AppCategory.VIDEO, 0xFF00A6E0, 48),
        app("twitch", "Twitch", "tv.twitch.android.app", AppCategory.VIDEO, 0xFF9146FF, 68),
        app("ted", "TED", "com.ted.android.tv", AppCategory.VIDEO, 0xFFE62B1E, 40),
        // Live TV (free / streaming TV)
        app("pluto_tv", "Pluto TV", "tv.pluto.android", AppCategory.LIVE_TV, 0xFF2C2CFF, 72),
        app("tubi", "Tubi", "com.tubitv", AppCategory.LIVE_TV, 0xFF7408FF, 71),
        app("sling", "Sling TV", "com.sling", AppCategory.LIVE_TV, 0xFF2B7DE9, 55, regions = US),
        app("fubo", "Fubo", "com.fubo.firetv.screen", AppCategory.LIVE_TV, 0xFFFA4616, 52, regions = US),
        // Music
        app("spotify", "Spotify", "com.spotify.tv.android", AppCategory.MUSIC, 0xFF1DB954, 96),
        app("youtube_music", "YouTube Music", "com.google.android.youtube.tvmusic", AppCategory.MUSIC, 0xFFFF0000, 82),
        app("amazon_music", "Amazon Music", "com.amazon.music.tv", AppCategory.MUSIC, 0xFF25D1DA, 70),
        app("pandora", "Pandora", "com.pandora.android.atv", AppCategory.MUSIC, 0xFF3668FF, 58, regions = US),
        app("tidal", "TIDAL", "com.aspiro.tidal", AppCategory.MUSIC, 0xFF111111, 56),
        app("deezer", "Deezer", "deezer.android.app", AppCategory.MUSIC, 0xFFA238FF, 57),
        app("soundcloud", "SoundCloud", "com.soundcloud.android", AppCategory.MUSIC, 0xFFFF5500, 55),
        app("tunein", "TuneIn Radio", "tunein.player", AppCategory.MUSIC, 0xFF14D8CC, 50),
        app("jiosaavn", "JioSaavn", "com.jio.media.jiobeats", AppCategory.MUSIC, 0xFF2BC5B4, 68, regions = IN),
        app("gaana", "Gaana", "com.gaana", AppCategory.MUSIC, 0xFFE72C30, 60, tv = false, regions = IN, note = PHONE_LISTING),
        // Regional (India)
        app("jiohotstar", "JioHotstar", "in.startv.hotstar", AppCategory.REGIONAL, 0xFF0C1B5E, 98, tv = false,
            regions = IN, note = "$PHONE_LISTING JioCinema is now part of JioHotstar."),
        app("zee5", "ZEE5", "com.graymatrix.did", AppCategory.REGIONAL, 0xFF8230C6, 90, regions = IN),
        app("sonyliv", "Sony LIV", "com.sonyliv", AppCategory.REGIONAL, 0xFF2D3A8C, 88, regions = IN),
        app("airtel_xstream", "Airtel Xstream Play", "tv.accedo.airtel.wynk", AppCategory.REGIONAL, 0xFFE40000, 62,
            tv = false, regions = IN, note = PHONE_LISTING),
        app("jiotv", "JioTV", "com.jio.jioplay.tv", AppCategory.REGIONAL, 0xFF0F3CC9, 58, tv = false, regions = IN,
            note = PHONE_LISTING),
        // Games
        app("geforce_now", "GeForce NOW", "com.nvidia.geforcenow", AppCategory.GAMES, 0xFF76B900, 60),
        app("crossy_road", "Crossy Road", "com.yodo1.crossyroad", AppCategory.GAMES, 0xFF3DBE5A, 45),
        // Kids
        app("youtube_kids", "YouTube Kids", "com.google.android.apps.youtube.kids", AppCategory.KIDS, 0xFFFF0000, 70,
            tv = false, note = PHONE_LISTING),
        app("pbs_kids", "PBS KIDS Video", "org.pbskids.video", AppCategory.KIDS, 0xFF2638C4, 50, regions = US),
        // News
        app("cnn", "CNN", "com.cnn.mobile.android.tv", AppCategory.NEWS, 0xFFCC0000, 55),
        app("bloomberg", "Bloomberg", "com.bloomberg.btva", AppCategory.NEWS, 0xFF2800D7, 48),
        app("haystack", "Haystack News", "com.haystack.android", AppCategory.NEWS, 0xFFFF6A3D, 42, regions = US),
        app("bbc_iplayer", "BBC iPlayer", "bbc.iplayer.android", AppCategory.NEWS, 0xFFFF4C98, 60, tv = false,
            regions = GB, note = PHONE_LISTING),
        // Sports
        app("espn", "ESPN", "com.espn.score_center", AppCategory.SPORTS, 0xFFD00000, 60, regions = US),
        app("dazn", "DAZN", "com.dazn", AppCategory.SPORTS, 0xFFF7FF1A, 58),
        // Tools
        app("downloader", "Downloader", "com.esaba.downloader", AppCategory.TOOLS, 0xFFF68B1F, 50),
        app("send_files", "Send files to TV", "com.yablio.sendfilestotv", AppCategory.TOOLS, 0xFF2196F3, 48)
    )

    private val byPkg: Map<String, PopularApp> = all.associateBy { it.packageName }

    fun byPackage(pkg: String): PopularApp? = byPkg[pkg]

    fun byId(id: String): PopularApp? = all.firstOrNull { it.id == id }

    /** Global apps plus those tagged for [code]; an unknown/invalid code yields global apps only. */
    fun forRegion(code: String?): List<PopularApp> {
        val c = RegionGuess.normalize(code)
        return all.filter { it.regions.isEmpty() || (c != null && c in it.regions) }
    }

    /** Brand accent for [pkg]: [BrandColors] first, then this catalog, else null. */
    fun brandColorFor(pkg: String): Int? = BrandColors.known(pkg) ?: byPkg[pkg]?.brandColor
}
