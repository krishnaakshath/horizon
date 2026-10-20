package dev.tvlauncher.link

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.ui.Glass
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/** H02 Link's state as its provider reports it (null fields when the companion is missing). */
data class LinkStatus(
    val installed: Boolean,
    val enabled: Boolean = false,
    val running: Boolean = false,
    val address: String = "",
    val pin: String = "",
    val pinExpiresMs: Long = 0L,
    val paired: Int = 0,
) {
    /** "http://192.168.1.20:8765" (the provider may report a bare IP). */
    val url: String get() = PhoneRemoteText.url(address)

    companion object {
        fun read(context: Context): LinkStatus = try {
            context.contentResolver.query(Uri.parse(LinkContract.URI_STATUS), null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use LinkStatus(true)
                fun s(col: String) = c.getColumnIndex(col).let { if (it < 0) "" else c.getString(it) ?: "" }
                fun l(col: String) = c.getColumnIndex(col).let { if (it < 0) 0L else c.getLong(it) }
                LinkStatus(true, l(LinkContract.COL_ENABLED) != 0L, l(LinkContract.COL_RUNNING) != 0L, s(LinkContract.COL_ADDRESS),
                    s(LinkContract.COL_PIN), l(LinkContract.COL_PIN_EXPIRES_MS), l(LinkContract.COL_PAIRED).toInt())
            } ?: LinkStatus(false)
        } catch (e: Exception) {
            LinkStatus(false)
        }

        fun call(context: Context, method: String, arg: String? = null): Bundle? = try {
            context.contentResolver.call(Uri.parse(LinkContract.URI_BASE), method, arg, null)
        } catch (e: Exception) {
            null
        }
    }
}

/** Words of the page (pure, tested). */
object PhoneRemoteText {
    fun url(address: String): String {
        val a = address.trim()
        if (a.isEmpty()) return ""
        if (a.startsWith("http://") || a.startsWith("https://")) return a.trimEnd('/')
        return if (a.contains(':')) "http://$a" else "http://$a:${LinkContract.PORT}"
    }

    fun pairUrl(address: String, pin: String): String = url(address) + "/#pair=" + pin

    fun status(s: LinkStatus): String = when {
        !s.installed -> "Not installed"
        s.running -> "Running"
        s.enabled -> "Starting"
        else -> "Stopped"
    }

    fun paired(n: Int): String = when (n) {
        0 -> "No phones yet"
        1 -> "1 phone"
        else -> "$n phones"
    }

    fun minutesLeft(expiresMs: Long, nowMs: Long): Int = (((expiresMs - nowMs).coerceAtLeast(0) + 59_999) / 60_000).toInt()
}

/** Draws a [QrCode] in black on a white quiet zone (phones read that best). */
class QrView(context: Context) : View(context) {
    var code: QrCode? = null
        set(v) { field = v; invalidate() }
    private val paint = Paint().apply { isAntiAlias = false }

    override fun onDraw(canvas: Canvas) {
        val q = code ?: return
        val cells = q.size + 8
        val cell = minOf(width, height) / cells.toFloat()
        val ox = (width - cell * cells) / 2f
        val oy = (height - cell * cells) / 2f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(ox, oy, ox + cell * cells, oy + cell * cells, paint)
        paint.color = 0xFF000000.toInt()
        for (y in 0 until q.size) for (x in 0 until q.size) {
            if (!q[x, y]) continue
            val l = ox + (x + 4) * cell
            val t = oy + (y + 4) * cell
            canvas.drawRect(l, t, l + cell, t + cell, paint)
        }
    }
}

/**
 * Settings > System > Phone remote: use a phone on the same Wi-Fi as a remote and keyboard.
 * Reads and drives H02 Link through its provider (signature permission); the launcher itself
 * never touches the network.
 */
object PhoneRemoteSection : SettingsSection, SpecifiedPage {
    const val ID = "phone_remote"
    override val id = ID
    override val title = "Phone remote"
    override val iconRes = R.drawable.ic_set_remote
    override val group = SettingsGroup.SYSTEM
    override val description = "Use a phone on the same Wi-Fi as a remote and keyboard."

    const val STATUS = "phone.status"
    const val ADDRESS = "phone.address"
    const val PAIR = "phone.pair"
    const val PAIRED = "phone.paired"
    const val FORGET = "phone.forget"

    override val spec = PageSpec(ID, listOf(STATUS, ADDRESS, PAIR, PAIRED, FORGET))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val s = LinkStatus.read(context)
        val page = SettingsPage(context, nav.style, spec)
        if (!s.installed) {
            page.row(STATUS, "Phone remote", RowKind.Info(PhoneRemoteText.status(s)),
                subtitle = "Install H02 Link to use a phone as a remote", iconRes = R.drawable.ic_set_about)
            return page
        }
        page.row(STATUS, "Phone remote", RowKind.Toggle(s.enabled) { on ->
            LinkStatus.call(context, LinkContract.METHOD_SET_ENABLED, if (on) "1" else "0")
            page.postDelayed({ nav.refresh() }, 600)
        }, subtitle = PhoneRemoteText.status(s), iconRes = R.drawable.ic_set_check)
        page.row(ADDRESS, "Address", RowKind.Info(s.url.ifEmpty { "Connect to Wi-Fi" }),
            subtitle = "Open it in the phone's browser", iconRes = R.drawable.ic_set_home)
        page.row(PAIR, "Pair a phone", RowKind.Action {
            nav.push("Pair a phone") { c, n -> pairPage(c, n) }
        }, subtitle = "Shows a code for 10 minutes", iconRes = R.drawable.ic_set_tune)
        page.row(PAIRED, "Paired phones", RowKind.Info(PhoneRemoteText.paired(s.paired)), iconRes = R.drawable.ic_set_about)
        page.row(FORGET, "Forget all phones", RowKind.Action(chevron = false) {
            LinkStatus.call(context, LinkContract.METHOD_FORGET_PHONES)
            nav.refresh()
        }, subtitle = "Every phone must pair again", iconRes = R.drawable.ic_set_reset)
        page.note("Home and Settings work fully. In other apps, Home and volume work; the rest needs H02 Helper.")
        return page
    }

    private fun pairPage(context: Context, nav: SettingsNav): View {
        val pin = LinkStatus.call(context, LinkContract.METHOD_OPEN_PAIRING)?.getString(LinkContract.EXTRA_PIN).orEmpty()
        val s = LinkStatus.read(context)
        val page = SettingsPage(context, nav.style)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = context.dp(20)
            setPadding(p, p, p, p)
            background = Glass.item(context)
            isFocusable = true
        }
        val qr = QrView(context)
        if (pin.isNotEmpty() && s.address.isNotEmpty()) qr.code = QrCode.encode(PhoneRemoteText.pairUrl(s.address, pin))
        card.addView(qr, LinearLayout.LayoutParams(context.dp(180), context.dp(180)))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(24), 0, 0, 0) }
        fun line(t: String, sp: Float, color: Int) = TextView(context).apply {
            text = t; textSize = sp; setTextColor(color); typeface = Theme.medium
        }.also { col.addView(it) }
        if (pin.isEmpty()) {
            line("Pairing could not start", 20f, Theme.TEXT)
            line("Check that the phone remote is on.", 15f, Theme.TEXT_2)
        } else {
            line("Scan with the phone's camera", 15f, Theme.TEXT_2)
            line(pin.chunked(3).joinToString(" "), 40f, Theme.TEXT)
            line("Or open ${s.url} and enter this code.", 15f, Theme.TEXT_2)
            line("The code works for 10 minutes.", 15f, Theme.TEXT_2)
        }
        card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        page.add(card)
        card.post { card.requestFocus() }
        page.doOnDetach { LinkStatus.call(context, LinkContract.METHOD_CLOSE_PAIRING) }
        return page
    }
}
