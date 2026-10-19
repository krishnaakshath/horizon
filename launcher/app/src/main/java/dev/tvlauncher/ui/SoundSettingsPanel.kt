package dev.tvlauncher.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.sound.SoundEvent
import dev.tvlauncher.sound.SoundPlayer
import dev.tvlauncher.sound.SoundPrefs
import dev.tvlauncher.sound.SoundSettings
import dev.tvlauncher.sound.SoundThemes
import dev.tvlauncher.ui.Theme.style

/**
 * D-pad friendly sound settings. Rows: Enable, Theme (OK cycles + preview), Volume (left/right +-10),
 * per-event toggles, custom-files toggle (shows folder and file names), Preview.
 * Persists through [prefs] and invokes [onChanged] with the new settings after each edit.
 */
class SoundSettingsPanel(
    context: Context,
    private val prefs: SoundPrefs,
    private val player: SoundPlayer?,
    private val folderPath: String,
    private val onChanged: (SoundSettings) -> Unit = {}
) : LinearLayout(context) {

    private var s = prefs.load()
    private val refreshers = ArrayList<() -> Unit>()
    private val previewRunnables = ArrayList<Runnable>()
    private var firstRow: View? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { firstRow?.requestFocus() }
    }

    override fun onDetachedFromWindow() {
        previewRunnables.forEach { removeCallbacks(it) }
        previewRunnables.clear()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.BASE)
        setPadding(dp(Theme.MARGIN_H), dp(Theme.MARGIN_V), dp(Theme.MARGIN_H), dp(Theme.MARGIN_V))

        addView(TextView(context).apply { text = "Sounds"; style(30f, Theme.medium) })

        row("Sound effects", { if (s.enabled) "On" else "Off" }, onOk = {
            update(s.copy(enabled = !s.enabled)); player?.preview(SoundEvent.SELECT)
        })
        fun cycle(current: String, d: Int): String {
            val all = SoundThemes.all
            val i = all.indexOfFirst { it.id == current }.coerceAtLeast(0)
            return all[(i + d + all.size) % all.size].id
        }
        val nav = { d: Int ->
            update(s.copy(navThemeId = cycle(s.navThemeId, d))); player?.preview(SoundEvent.SELECT)
        }
        val launch = { d: Int ->
            update(s.copy(launchThemeId = cycle(s.launchThemeId, d))); player?.preview(SoundEvent.LAUNCH)
        }
        row("Navigation sounds (OK or left / right)", { SoundThemes.byId(s.navThemeId).name },
            onOk = { nav(1) }, onHorizontal = { d -> nav(d) })
        row("Launch sounds (OK or left / right)", { SoundThemes.byId(s.launchThemeId).name },
            onOk = { launch(1) }, onHorizontal = { d -> launch(d) })
        row("Volume", { "${s.volume}%   (left / right)" }, onOk = { player?.preview(SoundEvent.SELECT) },
            onHorizontal = { d ->
                update(s.copy(volume = (s.volume + d * 10).coerceIn(0, 100)))
                player?.preview(SoundEvent.SELECT)
            })
        for (e in SoundEvent.values()) {
            row("  " + label(e), { if (s.isEventOn(e)) "On" else "Off" }, onOk = {
                update(s.copy(perEvent = s.perEvent + (e to !s.isEventOn(e))))
                player?.preview(e)
            })
        }
        row("Use my own sound files", { if (s.useCustomFolder) "On" else "Off" }, onOk = {
            update(s.copy(useCustomFolder = !s.useCustomFolder))
        })
        addView(TextView(context).apply {
            text = "Folder: $folderPath\nFiles: " +
                SoundEvent.values().joinToString(", ") { it.name.lowercase() + ".wav" } +
                "\n16-bit PCM WAV, mono or stereo, 8-48 kHz, under 500 KB"
            style(14f, Theme.regular, Theme.TEXT_2)
            setPadding(dp(Theme.S4), dp(Theme.S2), dp(Theme.S4), dp(Theme.S3))
        })
        addView(TextView(context).apply {
            text = "Sound lab: OK plays that sound with the selected theme"
            style(16f, Theme.regular, Theme.TEXT_2)
            setPadding(dp(Theme.S4), dp(Theme.S4), dp(Theme.S4), 0)
        })
        for (e in SoundEvent.values()) {
            row("  Play " + label(e), { SoundThemes.byId(s.themeIdFor(e)).name }, onOk = { player?.preview(e) })
        }
        row("Preview", { "OK to play all" }, onOk = {
            SoundEvent.values().forEachIndexed { i, e ->
                val r = Runnable { player?.preview(e) }
                previewRunnables += r
                postDelayed(r, i * 450L)
            }
        })
        refresh()
    }

    private fun label(e: SoundEvent) = when (e) {
        SoundEvent.MOVE -> "Navigation move"
        SoundEvent.SELECT -> "Select"
        SoundEvent.BACK -> "Back"
        SoundEvent.LAUNCH -> "App launch"
        SoundEvent.MENU_OPEN -> "Menu open"
        SoundEvent.ERROR -> "Error"
    }

    private fun update(n: SoundSettings) {
        s = n
        prefs.save(n)
        refresh()
        onChanged(n)
    }

    private fun refresh() = refreshers.forEach { it() }

    private fun row(
        title: String,
        value: () -> String,
        onOk: () -> Unit,
        onHorizontal: ((Int) -> Unit)? = null
    ) {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(Theme.S3).toFloat(); setColor(Theme.SURFACE)
        }
        val tv = TextView(context).apply {
            style(20f, Theme.medium)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.S5), dp(Theme.S3), dp(Theme.S5), dp(Theme.S3))
            isFocusable = true
            isClickable = true
            background = bg
            setOnClickListener { onOk() }
            setOnFocusChangeListener { _, f ->
                bg.setColor(if (f) Theme.TEXT else Theme.SURFACE)
                setTextColor(if (f) Theme.TEXT_DARK else Theme.TEXT)
            }
            setOnKeyListener { _: View, code: Int, ev: KeyEvent ->
                if (ev.action == KeyEvent.ACTION_DOWN && onHorizontal != null &&
                    (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)
                ) {
                    onHorizontal(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1); true
                } else false
            }
        }
        if (firstRow == null) firstRow = tv
        refreshers += { tv.text = "$title:  ${value()}" }
        addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(Theme.S2)
        })
    }
}
