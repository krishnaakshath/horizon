package dev.tvlauncher.remotecheck

import android.content.Context
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.Glass
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * Raw key events for the remote check page. AppSettingsActivity hands every key event here
 * first; a listener returning true takes the key (the page keeps D-pad, OK and Back for itself).
 */
object RemoteCapture {
    @Volatile var listener: ((KeyEvent) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean = try {
        listener?.invoke(event) ?: false
    } catch (e: RuntimeException) {
        false
    }
}

/** The state of the check survives leaving the live page while Settings is open (the summary row). */
object RemoteCheckSession {
    val model = RemoteCheckModel()
}

/**
 * The live remote check: a big glass card with the last key (name, code, scan code, where it came
 * from) above a map of the common remote keys that light up once seen, then keys outside the map.
 * While it is shown it takes every key except volume (which still changes the volume); Back
 * twice leaves via [onLeave].
 */
class RemoteCheckView(context: Context, private val accent: Int, private val onLeave: () -> Unit) : LinearLayout(context) {
    private val model = RemoteCheckSession.model
    private val lastName = TextView(context).apply { style(34f, Theme.semibold); gravity = Gravity.CENTER }
    private val lastDetail = TextView(context).apply {
        style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val summary = TextView(context).apply { style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2) }
    private val flow = Flow(context)
    private val others = TextView(context).apply {
        style(Theme.SP_LABEL, Theme.regular, Theme.TEXT)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val chips = HashMap<RemoteKeys.Key, TextView>()

    private val capture: (KeyEvent) -> Boolean = { e -> onKey(e) }

    init {
        orientation = VERTICAL
        isFocusable = true
        isFocusableInTouchMode = false
        contentDescription = "Remote check"
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            val p = context.dp(Theme.S4)
            setPadding(p, p, p, p)
            background = Glass.item(context, 18)
            addView(lastName, LayoutParams(-1, -2))
            addView(lastDetail, LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S1) })
        }
        addView(card, LayoutParams(-1, -2))
        addView(summary, LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S3) })
        for (g in RemoteKeys.GROUPS) {
            flow.addView(TextView(context).apply {
                text = g.title
                style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2)
                setPadding(context.dp(Theme.S2), context.dp(6), context.dp(Theme.S1), context.dp(6))
                tag = Flow.BREAK
            })
            for (key in g.keys) {
                val chip = TextView(context).apply {
                    text = key.label
                    style(12f, Theme.medium, Theme.TEXT)
                    maxLines = 1
                    setPadding(context.dp(10), context.dp(5), context.dp(10), context.dp(5))
                }
                chips[key] = chip
                flow.addView(chip)
            }
        }
        addView(flow, LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S1) })
        addView(others, LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S2) })
        addView(TextView(context).apply {
            text = RemoteCheckModel.NOTE + " " + RemoteCheckModel.LEAVE + "."
            style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
            maxLines = 3
        }, LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S2) })
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        RemoteCapture.listener = capture
    }

    override fun onDetachedFromWindow() {
        if (RemoteCapture.listener === capture) RemoteCapture.listener = null
        super.onDetachedFromWindow()
    }

    private fun onKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (e.action == KeyEvent.ACTION_DOWN) {
            val dev = e.device
            val source = KeySource.label(e.source, dev?.name, dev?.isVirtual == true || e.deviceId == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)
            val leave = model.onKeyDown(code, e.scanCode, source, e.repeatCount, SystemClock.uptimeMillis(), KeyEvent.keyCodeToString(code))
            render()
            if (leave) { post { onLeave() }; return true }
        }
        return !model.passThrough(code)
    }

    private fun render() {
        val p = model.last
        if (p == null) {
            lastName.text = RemoteCheckModel.WAITING
            lastDetail.text = RemoteCheckModel.LEAVE
        } else {
            lastName.text = p.name
            lastDetail.text = buildString {
                append("Code ").append(p.code)
                if (p.scanCode > 0) append(" · scan ").append(p.scanCode)
                append(" · ").append(p.source)
                if (p.repeat > 0) append(" · held")
            }
        }
        summary.text = model.summary()
        for ((key, chip) in chips) {
            val seen = model.seen(key)
            val lastKey = p != null && p.code in key.codes
            chip.background = Glass.rect(context,
                if (seen) (accent and 0x00FFFFFF) or (if (lastKey) 0xE6000000.toInt() else 0x66000000) else Theme.GLASS_CHIP,
                999, if (lastKey) Theme.TEXT else Theme.STROKE, if (lastKey) 2 else 1)
            chip.setTextColor(if (seen) Theme.TEXT else Theme.TEXT_2)
        }
        val o = model.otherKeys
        others.visibility = if (o.isEmpty()) View.GONE else View.VISIBLE
        others.text = "Other keys: " + o.entries.joinToString(" · ") { (c, n) -> "$n ($c)" }
    }

    /** Lays children left to right and wraps; a child tagged [BREAK] starts a new line. */
    private class Flow(context: Context) : ViewGroup(context) {
        private val gapH = context.dp(6)
        private val gapV = context.dp(6)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxW = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: Int.MAX_VALUE
            var x = 0
            var y = 0
            var lineH = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val brk = c.tag == BREAK && i > 0
                if (brk || (x > 0 && x + c.measuredWidth > maxW)) {
                    y += lineH + gapV
                    x = 0
                    lineH = 0
                }
                x += c.measuredWidth + gapH
                lineH = maxOf(lineH, c.measuredHeight)
            }
            setMeasuredDimension(if (maxW == Int.MAX_VALUE) x else maxW, y + lineH)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val maxW = r - l
            var x = 0
            var y = 0
            var lineH = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                val brk = c.tag == BREAK && i > 0
                if (brk || (x > 0 && x + c.measuredWidth > maxW)) {
                    y += lineH + gapV
                    x = 0
                    lineH = 0
                }
                c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
                x += c.measuredWidth + gapH
                lineH = maxOf(lineH, c.measuredHeight)
            }
        }

        companion object {
            const val BREAK = "flow:break"
        }
    }
}
