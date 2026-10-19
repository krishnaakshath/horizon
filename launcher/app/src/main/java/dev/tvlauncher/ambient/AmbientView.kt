package dev.tvlauncher.ambient

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.ui.StatusIcons
import dev.tvlauncher.ui.StatusModel.BtIcon
import dev.tvlauncher.ui.StatusModel.NetIcon
import dev.tvlauncher.ui.Theme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Full-screen ambient / screensaver view (programmatic). Layers, bottom to top: wallpaper,
 * legibility scrims, content (clock, date, weather + forecast, network row, notification chips)
 * and a black dim overlay. Burn-in protection moves the content with [PixelShift] every minute and
 * dims it after 2 min (and at night when enabled). Everything starts on attach and stops on detach
 * (callbacks removed, receivers unregistered, wallpaper bitmaps recycled).
 */
class AmbientView(
    context: Context,
    private val profile: DeviceProfile,
    private var settings: WallpaperSettings,
    private val weatherSource: WeatherSource = TimeoutWeatherSource(ContentProviderWeatherSource(context)),
    private val feed: NotificationFeed = NotificationFeed.shared,
    accent: Int = BuiltInWallpapers.DEFAULT_ACCENT
) : FrameLayout(context) {
    private val lite = profile.effectsMode == EffectsMode.LITE
    private val dm = resources.displayMetrics
    private val density = dm.density
    private val model = AmbientLayoutModel.compute(dm.widthPixels, dm.heightPixels, density, lite)
    private val main = Handler(Looper.getMainLooper())

    private val wallpaper = WallpaperView(context, lite)
    private val wallpaperController = WallpaperController(context, wallpaper, profile, accent)
    private val content = FrameLayout(context)
    private val dimOverlay = View(context)

    private val clockBlock = LinearLayout(context)
    private val clock = TextView(context)
    private val date = TextView(context)

    private val weatherBlock = LinearLayout(context)
    private val wxIcon = ImageView(context)
    private val wxTemp = TextView(context)
    private val wxCondition = TextView(context)
    private val wxHighLow = TextView(context)
    private val wxLocation = TextView(context)
    private val wxFresh = TextView(context)
    private val forecast = LinearLayout(context)

    private val netRow = LinearLayout(context)
    private val netIcon = ImageView(context)
    private val netLabel = TextView(context)
    private val btIcon = ImageView(context)
    private val chips = LinearLayout(context)

    private var clockFormat = timeFormat(settings.clock24h)
    private val dateFormat = SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val shift = PixelShift(AmbientLayoutModel.SHIFT_DP, seed = System.currentTimeMillis())
    private var status: StatusIcons? = null
    private var shownAt = 0L
    private var weather: WeatherSnapshot? = null
    private var attachedGen = 0

    private val minuteTick = object : Runnable {
        override fun run() {
            updateClock()
            stepShift()
            updateDim(animate = true)
            updateFreshness()
            main.postDelayed(this, msToNextMinute())
        }
    }
    private val weatherTick = object : Runnable {
        override fun run() {
            loadWeather()
            main.postDelayed(this, WEATHER_INTERVAL_MS)
        }
    }
    private val idleDim = Runnable { updateDim(animate = true) }
    private val feedListener: () -> Unit = { main.post { renderChips() } }

    init {
        setBackgroundColor(Theme.BASE)
        clipChildren = true
        addView(wallpaper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addScrims()
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        dimOverlay.setBackgroundColor(0xFF000000.toInt())
        dimOverlay.alpha = 0f
        addView(dimOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        buildClock()
        buildWeather()
        buildNetRow()
        buildChips()
        placeBlocks()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun updateSettings(s: WallpaperSettings) {
        settings = s
        clockFormat = timeFormat(s.clock24h)
        placeBlocks()
        updateClock()
        if (isAttachedToWindow) {
            wallpaperController.start(s)
            syncFeedListener()
            updateDim(animate = false)
        }
    }

    // --- construction -------------------------------------------------------------------------

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)
    private fun px(dp: Float) = (dp * density + 0.5f).toInt()

    private fun TextView.ambient(spSize: Float, face: Typeface, color: Int = Theme.TEXT) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)
        typeface = face
        setTextColor(color)
        includeFontPadding = false
        setShadowLayer(px(10f).toFloat(), 0f, px(1f).toFloat(), 0x66000000)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun addScrims() {
        val bottom = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xB3000000.toInt(), 0x59000000, 0x00000000))
        }
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, (model.heightPx * 0.6f).toInt(), Gravity.BOTTOM))
        val top = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x73000000, 0x00000000))
        }
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, (model.heightPx * 0.25f).toInt(), Gravity.TOP))
    }

    private fun buildClock() {
        clockBlock.orientation = LinearLayout.VERTICAL
        clock.ambient(model.clockSp, Theme.light)
        clock.letterSpacing = -0.02f
        date.ambient(model.dateSp, Theme.regular, 0xE6F5F7FA.toInt())
        clockBlock.addView(clock)
        clockBlock.addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4f) })
    }

    private fun buildWeather() {
        weatherBlock.orientation = LinearLayout.VERTICAL
        weatherBlock.gravity = Gravity.END
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(wxIcon, LinearLayout.LayoutParams(model.weatherIconPx, model.weatherIconPx).apply { marginEnd = px(10f) })
        wxTemp.ambient(model.tempSp, Theme.light)
        row.addView(wxTemp)
        weatherBlock.addView(row)
        wxCondition.ambient(model.conditionSp, Theme.medium)
        wxHighLow.ambient(model.metaSp + 1f, Theme.regular, Theme.TEXT_2)
        wxLocation.ambient(model.metaSp, Theme.regular, Theme.TEXT_2)
        wxFresh.ambient(model.metaSp - 2f, Theme.regular, 0x99A7B0C0.toInt())
        for (t in listOf(wxCondition, wxHighLow, wxLocation, wxFresh)) {
            t.gravity = Gravity.END
            t.maxWidth = model.weatherBlockWidthPx
            weatherBlock.addView(t, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(3f) })
        }
        forecast.orientation = LinearLayout.HORIZONTAL
        forecast.visibility = GONE
        weatherBlock.addView(forecast, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(14f) })
        renderWeather()
    }

    private fun buildNetRow() {
        netRow.orientation = LinearLayout.HORIZONTAL
        netRow.gravity = Gravity.CENTER_VERTICAL
        netRow.addView(netIcon, LinearLayout.LayoutParams(model.netIconPx, model.netIconPx))
        netLabel.ambient(model.metaSp, Theme.medium, Theme.TEXT_2)
        netRow.addView(netLabel, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(6f) })
        btIcon.visibility = GONE
        netRow.addView(btIcon, LinearLayout.LayoutParams(model.netIconPx, model.netIconPx).apply { marginStart = px(14f) })
        netRow.alpha = 0.85f
    }

    private fun buildChips() {
        chips.orientation = LinearLayout.HORIZONTAL
        chips.visibility = GONE
    }

    private fun placeBlocks() {
        content.removeAllViews()
        fun lp(g: Int) = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, g).apply {
            leftMargin = model.contentLeft
            rightMargin = model.widthPx - model.contentRight
            topMargin = model.contentTop
            bottomMargin = model.heightPx - model.contentBottom
        }
        val clockGravity: Int
        val chipsGravity: Int
        when (settings.clockPosition) {
            ClockPosition.CENTER -> { clockGravity = Gravity.CENTER; chipsGravity = Gravity.TOP or Gravity.START }
            ClockPosition.TOP_LEFT -> { clockGravity = Gravity.TOP or Gravity.START; chipsGravity = Gravity.BOTTOM or Gravity.START }
            ClockPosition.BOTTOM_LEFT -> { clockGravity = Gravity.BOTTOM or Gravity.START; chipsGravity = Gravity.TOP or Gravity.START }
        }
        clockBlock.gravity = if (settings.clockPosition == ClockPosition.CENTER) Gravity.CENTER_HORIZONTAL else Gravity.START
        content.addView(clockBlock, lp(clockGravity))
        content.addView(weatherBlock, lp(Gravity.BOTTOM or Gravity.END).apply { width = LayoutParams.WRAP_CONTENT })
        content.addView(netRow, lp(Gravity.TOP or Gravity.END))
        content.addView(chips, lp(chipsGravity))
    }

    // --- lifecycle ------------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedGen++
        shownAt = SystemClock.uptimeMillis()
        wallpaperController.start(settings)
        updateClock()
        updateDim(animate = false)
        main.postDelayed(minuteTick, msToNextMinute())
        main.post(weatherTick)
        main.postDelayed(idleDim, AmbientDimming.IDLE_DIM_AFTER_MS + 50)
        WeatherCompanion.requestRefresh(context)
        status = StatusIcons(context) { net, bt -> renderNetwork(net, bt) }.also { it.start() }
        syncFeedListener()
        content.alpha = 0f
        content.animate().alpha(1f).setDuration(if (lite) 250L else 600L).start()
    }

    override fun onDetachedFromWindow() {
        attachedGen++
        main.removeCallbacks(minuteTick)
        main.removeCallbacks(weatherTick)
        main.removeCallbacks(idleDim)
        status?.stop(); status = null
        feed.removeListener(feedListener)
        content.animate().cancel()
        dimOverlay.animate().cancel()
        wallpaperController.stop()
        wallpaper.release()
        super.onDetachedFromWindow()
    }

    // --- clock / dim / shift ---------------------------------------------------------------------

    private fun timeFormat(h24: Boolean) = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault())

    private fun msToNextMinute(): Long {
        val now = System.currentTimeMillis()
        return 60_000L - now % 60_000L + 30L
    }

    private fun updateClock() {
        val now = Date()
        clock.text = clockFormat.format(now)
        date.text = dateFormat.format(now)
    }

    private fun stepShift() {
        shift.step()
        val dx = shift.dxPx(density).toFloat()
        val dy = shift.dyPx(density).toFloat()
        if (lite) {
            content.translationX = dx; content.translationY = dy
        } else {
            content.animate().translationX(dx).translationY(dy).setDuration(1_200L).start()
        }
    }

    private fun updateDim(animate: Boolean) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val elapsed = if (shownAt == 0L) 0L else SystemClock.uptimeMillis() - shownAt
        val target = 1f - AmbientDimming.contentAlpha(elapsed, hour, settings.nightDim)
        if (!animate || lite) {
            dimOverlay.animate().cancel(); dimOverlay.alpha = target
        } else if (dimOverlay.alpha != target) {
            dimOverlay.animate().alpha(target).setDuration(2_000L).start()
        }
    }

    // --- weather ---------------------------------------------------------------------------------

    private fun loadWeather() {
        val gen = attachedGen
        weatherExecutor.execute {
            val snap = WeatherReader(weatherSource).read()
            main.post { if (gen == attachedGen) { weather = snap; renderWeather() } }
        }
    }

    private fun updateFreshness() {
        val w = weather
        if (w == null || !WeatherFreshness.isUsable(w.current.updatedMs, System.currentTimeMillis())) renderWeather()
        else wxFresh.text = WeatherFreshness.label(w.current.updatedMs, System.currentTimeMillis())
    }

    private fun renderWeather() {
        val w = weather
        val d = WeatherDisplay.from(w, System.currentTimeMillis())
        fun TextView.show(s: String?) { visibility = if (s == null) GONE else VISIBLE; text = s ?: "" }
        if (d.icon == null) {
            wxIcon.visibility = GONE
        } else {
            wxIcon.visibility = VISIBLE
            wxIcon.setImageResource(WeatherCompanion.drawable(d.icon))
        }
        wxTemp.show(d.temp)
        wxCondition.show(d.condition)
        wxCondition.setTextColor(if (d.available) Theme.TEXT else Theme.TEXT_2)
        wxHighLow.show(d.highLow)
        wxLocation.show(d.location)
        wxFresh.show(d.freshness)
        if (d.available && w != null) renderForecast(w.daily, w.current.unit) else forecast.visibility = GONE
    }

    private fun renderForecast(days: List<WeatherDay>, unit: String) {
        forecast.removeAllViews()
        if (!model.showForecast || days.isEmpty()) { forecast.visibility = GONE; return }
        forecast.visibility = VISIBLE
        days.forEachIndexed { i, d ->
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val name = TextView(context).apply { ambient(model.metaSp - 1f, Theme.medium, Theme.TEXT_2); text = dayFormat.format(Date(d.dayMs)) }
            val icon = ImageView(context).apply { setImageResource(WeatherCompanion.drawable(WeatherFormat.iconFor(d.code, true))) }
            val temps = TextView(context).apply {
                ambient(model.metaSp - 1f, Theme.regular)
                text = "${WeatherFormat.temp(d.highC, unit)} ${WeatherFormat.temp(d.lowC, unit)}"
            }
            col.addView(name)
            col.addView(icon, LinearLayout.LayoutParams(model.forecastIconPx, model.forecastIconPx).apply { topMargin = px(4f); bottomMargin = px(4f) })
            col.addView(temps)
            forecast.addView(col, LinearLayout.LayoutParams(px(56f), -2).apply { if (i > 0) marginStart = px(6f) })
        }
    }

    // --- network / notifications -----------------------------------------------------------------

    private fun renderNetwork(net: NetIcon, bt: BtIcon) {
        netIcon.setImageResource(StatusIcons.drawable(net))
        netLabel.text = when (net) {
            NetIcon.ETHERNET -> "Ethernet"
            NetIcon.WIFI_OFF -> "Not connected"
            NetIcon.OFFLINE -> "Offline"
            else -> "Wi-Fi"
        }
        if (bt == BtIcon.HIDDEN) {
            btIcon.visibility = GONE
        } else {
            btIcon.visibility = VISIBLE
            btIcon.setImageResource(StatusIcons.drawable(bt))
            btIcon.alpha = if (bt == BtIcon.CONNECTED) 1f else 0.55f
        }
    }

    private fun syncFeedListener() {
        // Opted out: stop feeding the in-memory feed and clear it right away.
        if (feed === NotificationFeed.shared) feed.setEnabled(settings.notificationsEnabled)
        val on = settings.notificationsEnabled && NotificationAccess.isEnabled(context)
        if (on) feed.addListener(feedListener) else feed.removeListener(feedListener)
        renderChips()
    }

    private fun renderChips() {
        chips.removeAllViews()
        val on = settings.notificationsEnabled && NotificationAccess.isEnabled(context)
        val list = if (on) feed.chips(settings.privacyAppNameOnly, settings.showNotificationText, model.maxChips) else emptyList()
        if (list.isEmpty()) { chips.visibility = GONE; return }
        chips.visibility = VISIBLE
        list.forEachIndexed { i, c ->
            val t = TextView(context).apply {
                ambient(model.chipSp, Theme.medium)
                setShadowLayer(0f, 0f, 0f, 0)
                maxWidth = model.chipMaxWidthPx
                setPadding(px(14f), px(7f), px(14f), px(7f))
                background = GradientDrawable().apply {
                    cornerRadius = px(999f).toFloat()
                    setColor(0x59101218)
                    setStroke(px(1f), Theme.STROKE)
                }
                val sb = SpannableStringBuilder(c.app)
                if (c.title != null) {
                    val start = sb.length
                    sb.append("  ").append(c.title)
                    sb.setSpan(ForegroundColorSpan(Theme.TEXT_2), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (c.time.isNotEmpty()) {
                    val start = sb.length
                    sb.append("  ").append(c.time)
                    sb.setSpan(ForegroundColorSpan(0x99A7B0C0.toInt()), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                text = sb
            }
            chips.addView(t, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = px(8f) })
        }
    }

    private companion object {
        const val WEATHER_INTERVAL_MS = 10 * 60_000L
        val weatherExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ambient-weather").apply { isDaemon = true }
        }
    }
}
