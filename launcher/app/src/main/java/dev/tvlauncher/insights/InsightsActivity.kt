package dev.tvlauncher.insights

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp
import java.util.Calendar
import java.util.Locale

/** "Your viewing today": local, rule-based summary of foreground time. Nothing leaves the device. */
class InsightsActivity : Activity() {
    private lateinit var content: LinearLayout
    private var worker: Thread? = null
    @Volatile private var destroyed = false
    private var generation = 0 // main thread only; the latest load wins

    private data class Loaded(
        val today: DayUsage, val yesterday: DayUsage?, val week: List<DayUsage>,
        val labels: Map<String, String>, val icons: Map<String, Bitmap?>,
        val lateNightShare: Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Theme.BASE)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Theme.MARGIN_H), dp(Theme.MARGIN_V), dp(Theme.MARGIN_H), dp(Theme.MARGIN_V))
        }
        scroll.addView(content, MATCH_PARENT, WRAP_CONTENT)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
    }

    private fun refresh() {
        if (!UsageAccess.hasAccess(this)) { showNoAccess(); return }
        showMessage("Loading…")
        val app = applicationContext
        val gen = ++generation
        worker = Thread {
            val loaded = try { load(app) } catch (_: Throwable) { null }
            runOnUiThread {
                if (destroyed || isFinishing || gen != generation) return@runOnUiThread
                if (!UsageAccess.hasAccess(this)) { showNoAccess(); return@runOnUiThread }
                if (loaded == null) showMessage("Couldn't read viewing data.") else showData(loaded)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun load(ctx: android.content.Context): Loaded {
        val src = UsageStatsSource(ctx)
        val now = System.currentTimeMillis()
        val tz = java.util.TimeZone.getDefault()
        val starts = DayBounds.starts(now, 7, tz) // oldest first
        fun endOf(i: Int) = if (i == starts.lastIndex) now else starts[i + 1]
        val eventsPerDay = starts.mapIndexed { i, start -> src.events(start, endOf(i)) }
        val week = starts.mapIndexed { i, start ->
            DayUsage(start, UsageAggregator.aggregate(eventsPerDay[i], start, endOf(i), DEFAULT_IGNORE))
        }
        // Yesterday only up to the same time of day, so the comparison is like for like.
        val yesterday = InsightsModel.sameTimeYesterday(
            eventsPerDay[starts.size - 2], starts[starts.size - 2], now - starts.last(), DEFAULT_IGNORE
        )
        val late = InsightsModel.lateNightShare(eventsPerDay.last(), starts.last(), now, DEFAULT_IGNORE, tz)
        val pm = ctx.packageManager
        val labels = HashMap<String, String>()
        val icons = HashMap<String, Bitmap?>()
        for ((pkg, _) in InsightsModel.topApps(week.last(), 5)) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                labels[pkg] = pm.getApplicationLabel(info).toString()
                icons[pkg] = iconBitmap(pm.getApplicationIcon(info), ctx.dp(40))
            } catch (_: Throwable) {
                labels[pkg] = pkg.substringAfterLast('.')
            }
        }
        return Loaded(week.last(), yesterday, week, labels, icons, late)
    }

    private fun iconBitmap(d: android.graphics.drawable.Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return b
    }

    private fun tintFor(pkg: String, icon: Bitmap?): Int {
        BrandColors.known(pkg)?.let { return BrandColors.backdropAccent(it) }
        val px = icon?.let {
            val small = Bitmap.createScaledBitmap(it, 16, 16, true)
            IntArray(256).also { a -> small.getPixels(a, 0, 16, 0, 0, 16, 16) }
        }
        return BrandColors.backdropAccent(BrandColors.forApp(pkg, px))
    }

    // ---- rendering -------------------------------------------------------------------------

    private fun header() = TextView(this).apply {
        text = "Your viewing today"
        style(Theme.SP_HERO * 0.7f, Theme.light)
    }

    private fun reset() {
        content.removeAllViews()
        content.addView(header())
    }

    private fun showMessage(msg: String) {
        reset()
        content.addView(TextView(this).apply {
            text = msg
            style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
            setPadding(0, dp(Theme.S5), 0, 0)
        })
    }

    private fun showNoAccess() {
        reset()
        content.addView(TextView(this).apply {
            text = "Insights need usage access"
            style(22f, Theme.medium)
            setPadding(0, dp(Theme.S5), 0, dp(Theme.S2))
        })
        content.addView(TextView(this).apply {
            text = "Viewing time is calculated on this device from Android's usage data. " +
                "Nothing is sent anywhere. Allow usage access for this launcher to see it."
            style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
            setLineSpacing(0f, 1.2f)
        })
        if (UsageAccess.settingsIntent(this) == null) content.addView(TextView(this).apply {
            text = dev.tvlauncher.setup.SetupCopy.HELPER_LINE
            style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
            setPadding(0, dp(Theme.S5), 0, dp(Theme.S2))
        })
        val btn = Button(this).apply {
            text = "Open usage access settings"
            transformationMethod = null // no Button all-caps; avoids the API 28 all-caps accessor
            style(Theme.SP_BUTTON, Theme.medium)
            isFocusable = true
            isFocusableInTouchMode = true
            background = focusBg()
            setPadding(dp(Theme.S5), dp(Theme.S3), dp(Theme.S5), dp(Theme.S3))
            setOnClickListener {
                if (!UsageAccess.openSettings(this@InsightsActivity))
                    Toast.makeText(context, "Not available on this TV. Use the command above.", Toast.LENGTH_LONG).show()
            }
        }
        content.addView(btn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(Theme.S5) })
        btn.requestFocus()
    }

    private fun showData(d: Loaded) {
        reset()
        val now = System.currentTimeMillis()
        content.addView(TextView(this).apply {
            text = SummaryText.formatClock(d.today.totalMs)
            style(72f, Theme.light)
            setPadding(0, dp(Theme.S4), 0, 0)
        })
        content.addView(TextView(this).apply {
            text = SummaryText.build(d.today, d.yesterday, { d.labels[it] ?: it.substringAfterLast('.') }, now, d.lateNightShare)
            style(Theme.SP_SUB + 2, Theme.regular, Theme.TEXT_2)
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(Theme.S2), 0, dp(Theme.S5))
        })

        if (d.week.all { it.totalMs == 0L }) {
            content.addView(TextView(this).apply {
                text = "Nothing recorded yet \u2014 usage appears after you watch something."
                style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
                setPadding(0, 0, 0, dp(Theme.S4))
            })
        }
        val top = InsightsModel.topApps(d.today, 5)
        val max = top.firstOrNull()?.second ?: 1L
        for ((pkg, ms) in top) content.addView(appRow(pkg, ms, max, d))

        content.addView(TextView(this).apply {
            text = "Last 7 days"
            style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2)
            setPadding(0, dp(Theme.S6), 0, dp(Theme.S2))
        })
        val chart = WeekChartView(this)
        chart.setData(InsightsModel.weekTotals(d.week), d.week.map { dayLetter(it.dayStartMs) })
        content.addView(chart, LinearLayout.LayoutParams(MATCH_PARENT, dp(190)))
    }

    private fun appRow(pkg: String, ms: Long, max: Long, d: Loaded): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            background = focusBg()
            setPadding(dp(Theme.S3), dp(Theme.S2), dp(Theme.S3), dp(Theme.S2))
        }
        val icon = ImageView(this).apply {
            d.icons[pkg]?.let { setImageBitmap(it) }
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(Theme.S4) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        line.addView(TextView(this).apply {
            text = d.labels[pkg] ?: pkg.substringAfterLast('.')
            style(Theme.SP_SUB, Theme.medium)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        line.addView(TextView(this).apply {
            text = SummaryText.formatDuration(ms)
            style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
        })
        col.addView(line)
        val bar = BarView(this).apply {
            fraction = ms.toFloat() / max
            tint = tintFor(pkg, d.icons[pkg])
        }
        col.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(8)).apply { topMargin = dp(Theme.S2) })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return row
    }

    private fun dayLetter(startMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = startMs }
        return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
    }

    private fun round(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun focusBg() = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
            setColor(0x26FFFFFF); setStroke(dp(2), Theme.TEXT); cornerRadius = dp(14).toFloat()
        })
        addState(intArrayOf(), GradientDrawable().apply {
            setColor(Color.TRANSPARENT); cornerRadius = dp(14).toFloat()
        })
    }

    // BACK closes by default (Activity.onBackPressed); nothing to intercept.
}
