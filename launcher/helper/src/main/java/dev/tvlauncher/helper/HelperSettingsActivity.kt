package dev.tvlauncher.helper

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class HelperSettingsActivity : Activity() {
    private lateinit var prefs: HelperPrefs
    private lateinit var content: LinearLayout
    private var learning = false
    private var learnMessage: String? = null

    private val bg = Color.parseColor("#0B0D12")
    private val surface = Color.parseColor("#151922")
    private val focusFill = Color.parseColor("#262E45")
    private val accent = Color.parseColor("#6EA8FF")
    private val text = Color.parseColor("#F5F7FA")
    private val muted = Color.parseColor("#9AA3B2")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HelperPrefs(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(40), dp(64), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(-1, -2))
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun isServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return ServiceList.contains(flat) &&
            Settings.Secure.getString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)?.trim() != "0"
    }

    private fun render() {
        val s = prefs.load()
        val focusIdx = (0 until content.childCount).firstOrNull { content.getChildAt(it).hasFocus() } ?: 3
        content.removeAllViews()
        content.addView(label(getString(R.string.app_name), 28f, text, true))
        content.addView(label("Remote shortcuts that work in every app.", 15f, muted, false))
        content.addView(spacer())

        val route = EnableRoutes.pick(isServiceEnabled(), resolves(accessibilityIntent()), canWriteSecure(), resolves(setupIntent()))
        when (route) {
            EnableRoute.ALREADY_ON -> content.addView(row("Remote shortcuts", "On \u2713") { })
            EnableRoute.SELF_ENABLE -> content.addView(row("Turn on double-Back and power-on help", "Select to turn on") { selfEnable() })
            EnableRoute.OPEN_SCREEN -> content.addView(row("Remote shortcuts: off", "Select, then turn on " + getString(R.string.app_name)) {
                start(accessibilityIntent())
            })
            EnableRoute.FINISH_SETUP -> content.addView(row("Finish setup to enable", "Opens the launcher's setup") { start(setupIntent()) })
            EnableRoute.NONE -> content.addView(label("Finish setup to enable.", 15f, muted, false))
        }
        learnMessage?.takeIf { route != EnableRoute.ALREADY_ON && !learning && it == ENABLE_FAILED }?.let {
            content.addView(label(it, 14f, muted, false))
        }
        content.addView(spacer())

        content.addView(
            row("Double-Back to leave app", if (s.doubleBackEnabled) "On" else "Off") {
                prefs.update { it.copy(doubleBackEnabled = !it.doubleBackEnabled) }
                render()
            },
        )
        content.addView(
            row("Double-Back window", "${s.windowMs} ms (select to change)") {
                prefs.update {
                    val next = if (it.windowMs + 100 > DoubleBackDetector.MAX_MS) DoubleBackDetector.MIN_MS else it.windowMs + 100
                    it.copy(windowMs = next)
                }
                render()
            },
        )
        content.addView(spacer())

        val keyValue = when {
            learning -> "Press a key now... (Back cancels)"
            learnMessage != null -> learnMessage!!
            s.launcherKey != 0 -> "Set in the launcher (Settings › Spotlight): " + KeyEvent.keyCodeToString(s.launcherKey) +
                if (s.launcherDouble) ", press twice" else ""
            s.shortcutKey != 0 -> KeyEvent.keyCodeToString(s.shortcutKey)
            else -> "None"
        }
        content.addView(
            row("Shortcut key (opens launcher Spotlight)", keyValue) {
                learning = true
                learnMessage = null
                render()
            },
        )
        content.addView(
            row("Clear shortcut key", "Disable the shortcut") {
                learning = false
                learnMessage = null
                prefs.update { it.copy(shortcutKey = 0) }
                SpotlightSync.sendState(this, hello = false)
                render()
            },
        )
        content.addView(spacer())

        content.addView(label("Apps where Double-Back is ignored", 16f, text, true))
        content.addView(label(ExclusionList.format(s.exclusions), 14f, muted, false).apply { setPadding(dp(4), dp(6), 0, 0) })
        content.addView(spacer())
        content.addView(label("Runs on this TV only. Does not read screen content. No internet access.", 14f, muted, false))

        content.getChildAt(focusIdx.coerceAtMost(content.childCount - 1))?.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (learning) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                learning = false
                learnMessage = "Cancelled"
            } else if (ShortcutKeys.isAllowed(keyCode)) {
                prefs.update { it.copy(shortcutKey = keyCode) }
                SpotlightSync.sendState(this, hello = false)
                learning = false
                learnMessage = "Saved: ${KeyEvent.keyCodeToString(keyCode)}"
            } else {
                learnMessage = "${KeyEvent.keyCodeToString(keyCode)} cannot be used. Press another key (Back cancels)."
            }
            render()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    private fun setupIntent() = Intent(SETUP_ACTION).setPackage("dev.tvlauncher")

    /** Never fires an intent the TV cannot handle. */
    private fun resolves(i: Intent): Boolean = try { packageManager.resolveActivity(i, 0) != null } catch (e: Exception) { false }

    private fun start(i: Intent) {
        if (!resolves(i)) { render(); return }
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { render() }
    }

    private fun canWriteSecure(): Boolean =
        checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Appends the service to the enabled list (other services stay) and turns accessibility on. */
    private fun selfEnable() {
        learnMessage = try {
            val cr = contentResolver
            val flat = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ServiceList.add(flat))
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            if (isServiceEnabled()) null else ENABLE_FAILED
        } catch (e: Exception) {
            ENABLE_FAILED
        }
        render()
    }

    private companion object {
        const val SETUP_ACTION = "dev.tvlauncher.action.SETUP"
        const val ENABLE_FAILED = "Not switched on yet. Finish setup in the launcher to enable."
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun spacer() = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(20)) }

    private fun label(t: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun row(title: String, value: String, onClick: () -> Unit): View {
        val fn = { c: Int, stroke: Int ->
            GradientDrawable().apply {
                setColor(c)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(2), stroke)
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isFocusable = true
            isClickable = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), fn(focusFill, accent))
                addState(intArrayOf(), fn(surface, surface))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            addView(label(title, 18f, text, true))
            addView(label(value, 15f, muted, false))
            setOnClickListener { onClick() }
        }
    }
}
