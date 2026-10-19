package dev.tvlauncher.link

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.ha.HaRepository
import dev.tvlauncher.link.ha.HaUrls
import dev.tvlauncher.link.update.UpdateCheck
import dev.tvlauncher.link.update.Updates
import java.util.concurrent.Executors

/**
 * Link's only screen (opened from the launcher's Settings > System > Phone remote): Home Assistant
 * connection and update-check options, typed with the TV's on-screen keyboard.
 */
class LinkSettingsActivity : Activity() {
    private val pad = dev.tvlauncher.input.GamepadInput(this)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = pad.dispatch(event) { super.dispatchKeyEvent(it) }
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = pad.motion(ev) || super.dispatchGenericMotionEvent(ev)

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var store: LinkStore
    private lateinit var status: TextView
    @Volatile private var destroyed = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LinkStore(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(40), dp(64), dp(40))
        }
        col.addView(text(LinkBrand.APP_NAME, 30f, LinkBrand.TEXT))
        status = text("", 15f, LinkBrand.TEXT_DIM)
        col.addView(status)

        col.addView(text("Home Assistant", 20f, LinkBrand.TEXT).apply { setPadding(0, dp(24), 0, dp(8)) })
        val url = field("Address, for example homeassistant.local:8123", store.haUrl, InputType.TYPE_TEXT_VARIATION_URI)
        val token = field(if (store.haToken.isNotEmpty()) "Token saved (type to replace)" else "Long-lived access token", "",
            InputType.TYPE_TEXT_VARIATION_PASSWORD)
        col.addView(url); col.addView(token)
        col.addView(button("Connect") {
            val u = url.text.toString(); val t = token.text.toString().trim().ifEmpty { null }
            say("Connecting…")
            io.execute {
                val s = HaRepository.configure(this, u, t)
                ui.post { if (!destroyed) say(haText(s)) }
            }
        })
        col.addView(button("Forget Home Assistant") { HaRepository.forget(this); url.setText(""); token.setText(""); say("Home Assistant removed") })
        col.addView(text("Pick favourites on the phone page.", 14f, LinkBrand.TEXT_DIM))

        col.addView(text("Updates", 20f, LinkBrand.TEXT).apply { setPadding(0, dp(24), 0, dp(8)) })
        val repo = field("Release project (owner/name)", store.updateRepo, InputType.TYPE_CLASS_TEXT)
        col.addView(repo)
        val toggle = button(if (store.updateEnabled) "Update check: On" else "Update check: Off") {}
        toggle.setOnClickListener {
            val r = repo.text.toString().trim()
            if (!store.updateEnabled && !UpdateCheck.validRepo(r)) { say("Type the release project first"); return@setOnClickListener }
            store.updateRepo = r
            store.updateEnabled = !store.updateEnabled
            store.updateCheckedMs = 0L
            (toggle as TextView).text = if (store.updateEnabled) "Update check: On" else "Update check: Off"
        }
        col.addView(toggle)
        col.addView(button("Check now") {
            store.updateRepo = repo.text.toString().trim()
            say("Checking…")
            io.execute {
                val ok = Updates.check(this, force = true)
                ui.post {
                    if (destroyed) return@post
                    say(when {
                        !store.updateEnabled -> "Switch the update check on first"
                        !ok -> "Couldn't check for updates"
                        store.updateAvailable -> "Update available: ${store.updateLatest}. Install it from ${LinkBrand.STORE}."
                        else -> "Up to date"
                    })
                }
            }
        })
        col.addView(text("Nothing is checked or downloaded unless this is on.", 14f, LinkBrand.TEXT_DIM))

        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(LinkBrand.BG) })
        say(summary())
    }

    override fun onDestroy() {
        destroyed = true
        io.shutdownNow()
        super.onDestroy()
    }

    private fun summary(): String {
        val addr = LinkRuntime.address()
        return when {
            !store.enabled -> "Phone remote is off. Switch it on in the launcher's Settings."
            addr.isEmpty() -> "Phone remote is on; this TV is not on a home network."
            else -> "Phone remote: $addr" + if (store.haConfigured) " · Home Assistant: ${HaUrls.displayHost(store.haUrl)}" else ""
        }
    }

    private fun haText(s: String) = when (s) {
        LinkContract.HA_OK -> "Connected to Home Assistant"
        LinkContract.HA_AUTH -> "Home Assistant refused the token"
        else -> "Can't reach Home Assistant"
    }

    private fun say(s: String) { status.text = s }

    private fun text(s: String, sp: Float, color: Int) = TextView(this).apply {
        text = s; textSize = sp; setTextColor(color)
    }

    private fun field(hint: String, value: String, variation: Int) = EditText(this).apply {
        this.hint = hint
        setText(value)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or variation
        setTextColor(LinkBrand.TEXT)
        setHintTextColor(LinkBrand.TEXT_DIM)
        background = states()
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }

    private fun button(label: String, onClick: () -> Unit): View = TextView(this).apply {
        text = label
        textSize = 17f
        setTextColor(LinkBrand.TEXT)
        isFocusable = true
        isClickable = true
        background = states()
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun states() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rect(LinkBrand.SURFACE_FOCUS, LinkBrand.ACCENT))
        addState(intArrayOf(), rect(LinkBrand.SURFACE, 0))
    }

    private fun rect(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(14).toFloat()
        if (stroke != 0) setStroke(dp(2), stroke)
    }
}
