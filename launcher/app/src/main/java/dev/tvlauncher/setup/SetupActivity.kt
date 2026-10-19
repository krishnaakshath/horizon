package dev.tvlauncher.setup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.ambient.NotificationAccess
import dev.tvlauncher.ambient.UserWallpapers
import dev.tvlauncher.ambient.WeatherCompanion
import dev.tvlauncher.appsettings.AddOns
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.safemode.SafeMode
import dev.tvlauncher.ui.Glass
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp
import java.util.concurrent.Executors

/**
 * First-run checklist, 100% remote: a short list with live ticks on the right and ONE primary
 * action on the left (the next step's), plus Skip and "Go to Home". Re-reads the TV's state
 * whenever it comes back to the front, so returning from a system screen ticks the step.
 * Never blocks Home: BACK or "Go to Home" leaves at any moment and marks setup as seen.
 */
class SetupActivity : Activity() {
    private lateinit var store: SetupStore
    private lateinit var list: LinearLayout
    private lateinit var primary: TextView
    private lateinit var skip: TextView
    private lateinit var progress: TextView
    private lateinit var hint: TextView
    private var full = true
    private var steps: List<SetupStep> = emptyList()
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var gen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Safe Mode rules: while the crash guard is tripped nothing optional runs.
        if (try { SafeMode.guard(this).isTripped } catch (e: Throwable) { false }) { finish(); return }
        store = SetupStore(this)
        full = try { DeviceProfileReader.read(this).effectsMode == EffectsMode.FULL } catch (e: Throwable) { false }
        setContentView(build())
        primary.requestFocus()
    }

    private fun build(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.BASE) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        root.addView(row, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(dp(Theme.MARGIN_H + 24), dp(Theme.MARGIN_V + 12), dp(Theme.MARGIN_H + 24), dp(Theme.MARGIN_V + 12))
        })

        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(this).apply { style(34f, Theme.semibold); text = SetupCopy.SCREEN_TITLE })
        left.addView(TextView(this).apply {
            style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2); text = SetupCopy.SCREEN_LEAD; setLineSpacing(0f, 1.2f)
        }, lp(top = Theme.S3))
        progress = TextView(this).apply { style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2) }
        left.addView(progress, lp(top = Theme.S5))
        hint = TextView(this).apply { style(Theme.SP_SUB, Theme.regular, Theme.TEXT); setLineSpacing(0f, 1.2f) }
        left.addView(hint, lp(top = Theme.S2))
        primary = pill(primary = true) { runPrimary() }
        left.addView(primary, wrap(top = Theme.S5))
        val minor = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        skip = pill(primary = false) { skipCurrent() }.apply { text = SetupCopy.SKIP }
        minor.addView(skip, LinearLayout.LayoutParams(-2, -2))
        minor.addView(pill(primary = false) { finishSetup() }.apply { text = SetupCopy.FINISH },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(Theme.S3) })
        left.addView(minor, wrap(top = Theme.S3))
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(Theme.S6) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(Theme.S3), dp(Theme.S3), dp(Theme.S3), dp(Theme.S3)) }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            background = Glass.surface(this@SetupActivity, full, 24)
            addView(list, FrameLayout.LayoutParams(-1, -2))
        }
        row.addView(scroll, LinearLayout.LayoutParams(0, -2, 1.15f))
        return root
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh() // back from a permission dialog or a system screen
    }

    /** Applies the automatic part, then re-probes off the main thread and re-renders. */
    private fun refresh() {
        val g = ++gen
        val app = applicationContext
        try {
            worker.execute {
                SetupStatus.autoConfigure(app)
                val probe = try { SetupProber.probe(this, store) } catch (e: Throwable) { SetupProbe() }
                val s = SetupPlan.steps(probe, store.memory())
                runOnUiThread { if (g == gen && !isDestroyed) render(s) }
            }
        } catch (e: Throwable) {
        }
    }

    private fun render(s: List<SetupStep>) {
        val focusedId = (currentFocus?.tag as? StepId)
        steps = s
        progress.text = SetupPlan.progress(s)
        val next = SetupPlan.next(s)
        hint.text = next?.detail ?: SetupCopy.ALL_SET
        primary.text = next?.actionLabel ?: SetupCopy.FINISH
        skip.visibility = if (next != null) View.VISIBLE else View.GONE
        list.removeAllViews()
        for (step in s) list.addView(stepRow(step), lp(top = 0).apply { bottomMargin = dp(Theme.S1) })
        if (focusedId != null) (0 until list.childCount).map { list.getChildAt(it) }.firstOrNull { it.tag == focusedId }?.requestFocus()
        else if (!primary.hasFocus() && !skip.hasFocus()) primary.requestFocus()
    }

    private fun stepRow(step: SetupStep): View = LinearLayout(this).apply {
        tag = step.id
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(dp(Theme.S4), dp(Theme.S3), dp(Theme.S4), dp(Theme.S3))
        val mark = TextView(context).apply {
            gravity = Gravity.CENTER
            style(Theme.SP_LABEL + 1f, Theme.semibold, if (step.done) Theme.TEXT_DARK else Theme.TEXT_2)
            text = when (step.state) {
                StepState.DONE -> "✓"
                StepState.SKIPPED -> "–"
                else -> ""
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (step.done) setColor(Theme.TEXT) else setStroke(dp(2), 0x59FFFFFF)
            }
        }
        addView(mark, LinearLayout.LayoutParams(dp(24), dp(24)))
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply { style(Theme.SP_BUTTON + 1f, Theme.medium); text = step.title })
        texts.addView(TextView(context).apply {
            style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2); text = step.detail; maxLines = 2
        }, lp(top = 2))
        addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(Theme.S4) })
        fun paint(f: Boolean) {
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(if (f) Theme.GLASS_ROW_FOCUS else 0) }
        }
        paint(false)
        onFocusChangeListener = View.OnFocusChangeListener { _, f -> paint(f) }
        setOnClickListener { run(step) }
    }

    private fun pill(primary: Boolean, onClick: () -> Unit) = object : TextView(this) {
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            paintPill(this, primary, gainFocus)
        }
    }.apply {
        style(Theme.SP_BUTTON + 1f, Theme.medium)
        isFocusable = true
        isClickable = true
        gravity = Gravity.CENTER
        setPadding(dp(if (primary) 28 else 20), dp(12), dp(if (primary) 28 else 20), dp(12))
        paintPill(this, primary, false)
        setOnClickListener { onClick() }
    }

    private fun paintPill(v: TextView, primary: Boolean, focused: Boolean) {
        v.background = GradientDrawable().apply {
            cornerRadius = dp(Theme.RADIUS_PILL).toFloat()
            setColor(when { focused -> Theme.TEXT; primary -> 0x33FFFFFF; else -> 0x14FFFFFF })
        }
        v.setTextColor(if (focused) Theme.TEXT_DARK else Theme.TEXT)
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun wrap(top: Int) = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top) }

    private fun runPrimary() {
        val next = SetupPlan.next(steps)
        if (next == null) finishSetup() else run(next)
    }

    private fun skipCurrent() {
        SetupPlan.next(steps)?.let { store.skip(it.id) }
        refresh()
    }

    /** One press on a step: its action (a skipped step comes back). Done or helper-only steps just stay. */
    private fun run(step: SetupStep) {
        store.unskip(step.id)
        val ok = when (step.action) {
            StepAction.NONE -> true
            StepAction.CHOOSE_HOME -> SetupProber.homeChooser(this)?.let { startForResult(it) } ?: false
            StepAction.AUTO_ENABLE -> { store.markAutoTried(step.id); true }
            StepAction.OPEN_LISTENER_SCREEN -> NotificationAccess.openSettings(this)
            StepAction.OPEN_ACCESSIBILITY -> SystemActions.start(this, SetupProber.accessibilityIntent())
            StepAction.REQUEST_LOCATION -> ask(Manifest.permission.ACCESS_FINE_LOCATION)
            StepAction.REQUEST_PICTURES -> ask(UserWallpapers.PERMISSION)
            StepAction.OPEN_WEATHER -> WeatherCompanion.settingsIntent(this)?.let { SystemActions.start(this, it) }
                ?.also { if (it) store.markVisited(StepId.WEATHER) } ?: false
            StepAction.OPEN_GET_APPS -> SystemActions.launchPackage(this, AddOns.STORE_PACKAGE)
            StepAction.OPEN_USAGE_SCREEN -> UsageAccess.openSettings(this)
        }
        if (!ok) hint.text = SetupCopy.HELPER_LINE
        refresh()
    }

    private fun startForResult(i: Intent): Boolean = try { startActivityForResult(i, REQ_HOME); true } catch (e: Throwable) { false }

    private fun ask(permission: String): Boolean = try {
        store.markAsked(permission)
        requestPermissions(arrayOf(permission), REQ_PERMISSION)
        true
    } catch (e: Throwable) {
        false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    @Deprecated("Activity result of the home chooser")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refresh()
    }

    private fun finishSetup() {
        store.markCompleted()
        finish()
    }

    @Deprecated("BACK leaves setup; it never blocks Home")
    override fun onBackPressed() = finishSetup()

    override fun onDestroy() {
        gen++
        worker.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val REQ_HOME = 41
        const val REQ_PERMISSION = 42
    }
}
