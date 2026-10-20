package dev.tvlauncher.splash

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.launch.LaunchStyles
import dev.tvlauncher.ui.LaunchOverlay
import dev.tvlauncher.ui.RevealTargets
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/**
 * Settings > Appearance > Start-up and loading: the start-up animation, when it plays, the loading
 * indicator, the app launch animation and the pace, plus a live preview that plays the chosen ones
 * over this page. Every change is saved at once; the home screen re-reads them when it starts.
 */
object StartupLoadingPage {
    const val TITLE = "Start-up and loading"
    const val NOTE = "The TV's own logo at power-on belongs to the TV; this controls what H02 shows."

    val SPEC = PageSpec(
        "appearance.startupPage",
        listOf("startup.style", "startup.when", "startup.loader", "startup.launch", "startup.pace", "startup.preview")
    )

    val STARTUP_CHOICES = StartupStyle.values().toList()
    val WHEN_CHOICES = StartupWhen.values().toList()
    val LOADER_CHOICES = LoaderStyle.values().toList()
    val LAUNCH_CHOICES = LaunchStyle.values().toList()
    val PACE_CHOICES = MotionPace.values().toList()

    /** The pace row's subtitle: honest about the short caps on a LITE TV. */
    fun paceSubtitle(full: Boolean, animate: Boolean): String = when {
        !animate -> "Animations are off in Appearance"
        full -> "Start-up up to ${MotionCaps.FULL_STARTUP_MS / 100 / 10.0} s, app launch up to ${MotionCaps.FULL_LAUNCH_MS} ms"
        else -> "Kept short on this TV: start-up up to ${MotionCaps.LITE_STARTUP_MS} ms, app launch up to ${MotionCaps.LITE_LAUNCH_MS} ms"
    }

    fun build(context: Context, nav: SettingsNav): View {
        val prefs = MotionStylePrefs(context)
        val s = prefs.load()
        val page = SettingsPage(context, nav.style, SPEC)
        page.row("startup.style", "Start-up animation",
            RowKind.Choice(STARTUP_CHOICES.map { it.label }, STARTUP_CHOICES.indexOf(s.startup)) { i ->
                prefs.update { it.copy(startup = STARTUP_CHOICES[i]) }
            }, subtitle = "What H02 shows while it gets ready", iconRes = R.drawable.ic_set_appearance)
        page.row("startup.when", "Play start-up",
            RowKind.Choice(WHEN_CHOICES.map { it.label }, WHEN_CHOICES.indexOf(s.startupWhen)) { i ->
                prefs.update { it.copy(startupWhen = WHEN_CHOICES[i]) }
            }, subtitle = "After standby it plays once when the home screen comes back", iconRes = R.drawable.ic_set_motion)
        page.row("startup.loader", "Loading indicator",
            RowKind.Choice(LOADER_CHOICES.map { it.label }, LOADER_CHOICES.indexOf(s.loader)) { i ->
                prefs.update { it.copy(loader = LOADER_CHOICES[i]) }
            }, subtitle = "Shown when an app is slow to open", iconRes = R.drawable.ic_set_motion)
        page.row("startup.launch", "App launch animation",
            RowKind.Choice(LAUNCH_CHOICES.map { it.label }, LAUNCH_CHOICES.indexOf(s.launch)) { i ->
                prefs.update { it.copy(launch = LAUNCH_CHOICES[i]) }
            }, subtitle = "From the tile to the app", iconRes = R.drawable.ic_set_tiles)
        page.row("startup.pace", "Duration",
            RowKind.Choice(PACE_CHOICES.map { it.label }, PACE_CHOICES.indexOf(s.pace)) { i ->
                prefs.update { it.copy(pace = PACE_CHOICES[i]) }
            }, subtitle = paceSubtitle(nav.style.full, nav.style.animate), iconRes = R.drawable.ic_set_clock)
        lateinit var previewRow: View
        previewRow = page.row("startup.preview", "Play preview",
            RowKind.Action(chevron = false) {
                if (!nav.style.animate) nav.notify("Animations are off in Appearance")
                else Preview.play(context, nav, prefs.load(), previewRow)
            }, subtitle = "Start-up, loading and app launch, right here", iconRes = R.drawable.ic_set_appearance)
        page.note(NOTE)
        page.doOnDetach { Preview.cancel(context) }
        return page
    }

    /** The live preview, drawn over the whole Settings window and gone by itself. Never takes focus. */
    private object Preview {
        private const val TAG = "startup.preview.host"
        private const val GAP_MS = 250L
        private const val LOADER_SHOWN_MS = 1400L

        fun cancel(context: Context) {
            val decor = activity(context)?.window?.decorView as? ViewGroup ?: return
            decor.findViewWithTag<View>(TAG)?.let { decor.removeView(it) }
        }

        fun play(context: Context, nav: SettingsNav, s: MotionStyleSettings, from: View) {
            val decor = activity(context)?.window?.decorView as? ViewGroup ?: return
            cancel(context)
            val full = nav.style.full
            val host = FrameLayout(context).apply {
                tag = TAG
                isFocusable = false
                isClickable = false
                setBackgroundColor(BACKDROP)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            // A stand-in for the home row, so the hand-over is visible.
            val row = View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = context.dp(Theme.RADIUS_TILE).toFloat()
                    setColor(Theme.SURFACE)
                }
            }
            host.addView(row, FrameLayout.LayoutParams(-1, context.dp(150), Gravity.BOTTOM).apply {
                val m = context.dp(64); setMargins(m, 0, m, m)
            })
            decor.addView(host, ViewGroup.LayoutParams(-1, -1))
            val targets = RevealTargets(row, row, row, row, row) { emptyList() }
            val player = StartupPlayers.create(context, s.startup, full, true, nav.style.accent, s.pace)
            var at = 0L
            if (player != null) {
                host.addView(player.view, FrameLayout.LayoutParams(-1, -1))
                player.prepare(targets)
                player.start()
                host.post { player.reveal(targets) }
                at = player.totalMs + GAP_MS
            }
            host.postDelayed({ if (host.parent != null) launch(context, nav, s, host, from) }, at)
        }

        private fun launch(context: Context, nav: SettingsNav, s: MotionStyleSettings, host: FrameLayout, from: View) {
            val overlay = LaunchOverlay(context)
            overlay.setLoaderStyle(s.loader)
            host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            val styled = LaunchStyles.plan(s.launch, nav.style.motion, 1f, "", warm = false, pace = s.pace)
            val rect = Rect().also { r ->
                val loc = IntArray(2); from.getLocationInWindow(loc)
                r.set(loc[0], loc[1], loc[0] + from.width, loc[1] + from.height)
            }
            // No app arrives, so the loader shows after its delay: what a slow app looks like.
            if (styled.animated) overlay.play(styled.plan, nav.style.accent, null, rect, styled.style) {}
            else overlay.show()
            host.postDelayed({
                overlay.hide()
                (host.parent as? ViewGroup)?.removeView(host)
            }, styled.plan.expandMs + LOADER_SHOWN_MS)
        }

        private fun activity(context: Context): Activity? {
            var c: Context? = context
            while (c is ContextWrapper) {
                if (c is Activity) return c
                c = c.baseContext
            }
            return null
        }

        private const val BACKDROP = 0xF20B0E14.toInt()
    }
}
