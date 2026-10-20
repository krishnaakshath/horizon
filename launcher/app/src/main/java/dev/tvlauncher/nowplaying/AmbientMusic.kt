package dev.tvlauncher.nowplaying

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.room.RoomTheme
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.GlassBackdrop
import dev.tvlauncher.ui.Theme

/**
 * "Screensaver follows the music": while a song plays, Ambient becomes that song. [backdrop] is a
 * full-bleed picture made from the art in the chosen [NowPlayingStyle] (the host puts it above the
 * wallpaper and under the scrims and the dim overlay, so the 2-minute dim and night dim apply);
 * [panel] holds the large sharp cover, title, artist, a progress thread and the app's icon (the
 * host puts it into the drifting content layer, so burn-in protection moves it). Each track change
 * crossfades in 350 ms (none on LITE). Nothing playing, or paused for 10 minutes: both hide and
 * [onActive] tells the host to bring the normal clock back. Main thread only.
 */
class AmbientMusic(context: Context, private val lite: Boolean) {
    private val app = context.applicationContext
    private val density = context.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private var settings = NowPlayingSettings()
    private var enabled = false
    private var item: NowPlaying? = null
    private var art: Bitmap? = null
    private var shown = false
    private var token = 0
    private var iconPkg: String? = null

    /** The host hides its own big clock and calendar while the song is shown. */
    var onActive: ((Boolean) -> Unit)? = null

    val backdrop = FrameLayout(context)
    private val bgA = ImageView(context)
    private val bgB = ImageView(context)
    private var bgFront = bgA

    val panel = LinearLayout(context)
    private val cover = ImageView(context)
    private val title = TextView(context)
    private val artist = TextView(context)
    private val progress = Thread(context)
    private val appIcon = ImageView(context)
    private val appLabel = TextView(context)
    private val credit = TextView(context)
    /** The host's big clock hides while the song shows; a small one stays here. */
    private val clock = TextView(context)
    private val timeFormat = android.text.format.DateFormat.getTimeFormat(context)

    private val recheck = Runnable { evaluate() }
    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            if (shown) main.postDelayed(this, 1_000L)
        }
    }

    init {
        for (v in listOf(bgA, bgB)) {
            v.scaleType = ImageView.ScaleType.CENTER_CROP
            backdrop.addView(v, FrameLayout.LayoutParams(-1, -1))
        }
        backdrop.setBackgroundColor(Theme.BASE)
        backdrop.visibility = View.GONE
        backdrop.auditTag(AuditTags.FULLBLEED, AuditTags.LAYER)

        panel.orientation = LinearLayout.HORIZONTAL
        panel.gravity = Gravity.CENTER_VERTICAL
        panel.visibility = View.GONE
        panel.auditTag(AuditTags.CONTENT)
        panel.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        cover.scaleType = ImageView.ScaleType.CENTER_CROP
        cover.clipToOutline = true
        cover.background = GradientDrawable().apply { cornerRadius = px(18f).toFloat(); setColor(0x33FFFFFF) }
        cover.elevation = px(12f).toFloat()
        panel.addView(cover, LinearLayout.LayoutParams(px(COVER_DP), px(COVER_DP)))

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        clock.text(20f, Theme.medium, Theme.TEXT_2, 1)
        col.addView(clock, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = px(18f) })
        title.text(40f, Theme.semibold, Theme.TEXT, 2)
        artist.text(24f, Theme.regular, Theme.TEXT_2, 1)
        col.addView(title, LinearLayout.LayoutParams(-1, -2))
        col.addView(artist, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6f) })
        col.addView(progress, LinearLayout.LayoutParams(-1, px(4f)).apply { topMargin = px(22f) })
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(appIcon, LinearLayout.LayoutParams(px(24f), px(24f)).apply { marginEnd = px(8f) })
        appLabel.text(15f, Theme.regular, Theme.TEXT_2, 1)
        row.addView(appLabel, LinearLayout.LayoutParams(-2, -2))
        col.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(14f) })
        credit.text(12f, Theme.regular, Theme.TEXT_2, 1)
        credit.text = SpotifyArtPolicy.CREDIT
        credit.visibility = View.GONE
        col.addView(credit, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8f) })
        panel.addView(col, LinearLayout.LayoutParams(px(560f), -2).apply { marginStart = px(48f) })
    }

    private fun px(dp: Float) = (dp * density + 0.5f).toInt()

    private fun TextView.text(sp: Float, face: android.graphics.Typeface, color: Int, lines: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = face
        setTextColor(color)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        setShadowLayer(px(10f).toFloat(), 0f, px(1f).toFloat(), 0x99000000.toInt())
    }

    /** True when the tracker must run for this (Settings: Screensaver follows the music, plus access). */
    fun start(): Boolean {
        settings = DiscoverStore(app).loadNowPlaying()
        enabled = settings.followAmbient(NowPlayingAccess.isGranted(app))
        if (enabled && settings.style == NowPlayingStyle.BANNER) SpotifyArtClient.refresh(app)
        evaluate()
        return enabled
    }

    fun stop() {
        enabled = false
        token++
        SpotifyArtClient.cancel()
        main.removeCallbacks(recheck)
        main.removeCallbacks(tick)
        item = null; art = null
        setShown(false)
        bgA.setImageDrawable(null); bgB.setImageDrawable(null); cover.setImageDrawable(null)
    }

    /** From the ambient tracker (item null = nothing). */
    fun update(n: NowPlaying?, bmp: Bitmap?) {
        val newTrack = AmbientMusicPlan.isNewTrack(item, n)
        val newArt = bmp !== art
        item = n
        art = bmp
        if (!enabled) return
        evaluate()
        if (n == null || !shown) return
        if (newTrack) bindText(n)
        if (newTrack || newArt) bindArt(n, bmp, newTrack)
    }

    private fun evaluate() {
        main.removeCallbacks(recheck)
        val d = AmbientMusicPlan.decide(enabled, item, SystemClock.elapsedRealtime())
        if (d.showSong && !shown) {
            setShown(true)
            item?.let { bindText(it); bindArt(it, art, true) }
        } else if (!d.showSong && shown) setShown(false)
        d.recheckInMs?.let { main.postDelayed(recheck, it + 50) }
    }

    private fun setShown(on: Boolean) {
        if (shown == on) return
        shown = on
        val fade = AmbientMusicPlan.fadeMs(lite, false)
        for (v in listOf(backdrop, panel)) {
            v.animate().cancel()
            if (on) {
                v.visibility = View.VISIBLE
                if (fade > 0) { v.alpha = 0f; v.animate().alpha(1f).setDuration(fade).start() } else v.alpha = 1f
            } else {
                v.visibility = View.GONE
            }
        }
        main.removeCallbacks(tick)
        if (on) main.post(tick)
        onActive?.invoke(on)
    }

    private fun bindText(n: NowPlaying) {
        val t = NowPlayingText.title(n)
        val a = NowPlayingText.artist(n)
        title.text = t.ifEmpty { a }
        artist.text = if (t.isEmpty()) "" else a
        artist.visibility = if (artist.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        appLabel.text = n.appLabel
        progress.accent = BrandColors.backdropAccent(NowPlayingAccent.forPackage(n.pkg))
        if (n.pkg != iconPkg) {
            iconPkg = n.pkg
            val d = try { app.packageManager.getApplicationIcon(n.pkg) } catch (e: Exception) { null }
            appIcon.setImageDrawable(d)
            appIcon.visibility = if (d != null) View.VISIBLE else View.GONE
        }
        updateProgress()
    }

    private fun bindArt(n: NowPlaying, bmp: Bitmap?, newTrack: Boolean) {
        val t = ++token
        cover.setImageBitmap(bmp)
        cover.visibility = if (bmp != null) View.VISIBLE else View.GONE
        credit.visibility = View.GONE
        val style = settings.style
        if (SpotifyArtPolicy.wanted(style, SpotifyArtClient.knownConfigured(), n)) {
            SpotifyArtClient.request(app, n, style) { spotify ->
                if (t != token || !shown) return@request
                if (spotify != null) { credit.visibility = View.VISIBLE; render(spotify, style, t, sharp = true) }
                else render(bmp, NowPlayingStyle.BLURRED, t, sharp = false)
            }
            if (newTrack) render(bmp, NowPlayingStyle.BLURRED, t, sharp = false)
            return
        }
        render(bmp, style, t, sharp = style == NowPlayingStyle.COVER)
    }

    /** Builds the backdrop picture on the glass worker and crossfades it in. */
    private fun render(src: Bitmap?, style: NowPlayingStyle, t: Int, sharp: Boolean) {
        val dm = backdrop.resources.displayMetrics
        val (sw, sh) = MediaArtMath.size(dm.widthPixels, dm.heightPixels, full = !sharp && !lite)
        val target = MediaArtMath.targetLuminance(RoomTheme.palette, Theme.TEXT_2)
        try {
            GlassBackdrop.executor.execute {
                val out: Bitmap? = try {
                    when {
                        src == null || src.isRecycled -> wash(BrandColors.NEUTRAL, sw, sh, target)
                        style == NowPlayingStyle.WASH -> MediaBackdrop.wash(src, sw, sh, target)?.first
                        else -> MediaBackdrop.process(src, sw, sh, target, full = !sharp && !lite)?.first
                    }
                } catch (e: Throwable) { null }
                main.post { if (t == token && shown && out != null) crossfade(out) }
            }
        } catch (e: RuntimeException) {
        }
    }

    private fun wash(color: Int, sw: Int, sh: Int, target: Double): Bitmap {
        val px = IntArray(sw * sh) { Theme.BASE }
        MediaArtMath.accentWash(px, sw, sh, BrandColors.backdropAccent(color))
        MediaArtMath.clampContrast(px, target)
        return Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
    }

    private fun crossfade(next: Bitmap) {
        val back = if (bgFront === bgA) bgB else bgA
        val old = bgFront
        back.setImageBitmap(next)
        back.bringToFront()
        bgFront = back
        val fade = AmbientMusicPlan.fadeMs(lite, false)
        back.animate().cancel()
        if (fade > 0) {
            back.alpha = 0f
            back.animate().alpha(1f).setDuration(fade).withEndAction { if (bgFront !== old) old.setImageDrawable(null) }.start()
        } else {
            back.alpha = 1f
            old.setImageDrawable(null)
        }
    }

    private fun updateProgress() {
        clock.text = timeFormat.format(java.util.Date())
        val n = item ?: return
        progress.visibility = if (n.durationMs > 0) View.VISIBLE else View.INVISIBLE
        progress.fraction = ProgressMath.fraction(n.positionAt(SystemClock.elapsedRealtime()), n.durationMs)
    }

    /** A thin progress thread: dim track, accent fill. */
    private class Thread(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var accent: Int = Theme.TEXT
        var fraction: Float = 0f
            set(v) { if (field != v) { field = v; invalidate() } }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            paint.color = 0x40FFFFFF
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
            paint.color = accent or 0xFF000000.toInt()
            canvas.drawRoundRect(0f, 0f, width * fraction.coerceIn(0f, 1f), height.toFloat(), r, r, paint)
        }
    }

    companion object {
        const val COVER_DP = 360f
    }
}
