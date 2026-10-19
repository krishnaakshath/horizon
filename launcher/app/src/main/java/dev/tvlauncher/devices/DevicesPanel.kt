package dev.tvlauncher.devices

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.tv.TvContract
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * Scrollable "Devices" list in the dark premium style. Call [setDevices] with
 * DeviceMonitor.devices() whenever it changes. Rows are D-pad focusable; HDMI rows switch to that
 * input on select. Section headers are not focusable.
 */
class DevicesPanel(context: Context) : ScrollView(context) {
    private val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(Theme.S5), context.dp(Theme.S5), context.dp(Theme.S5), context.dp(Theme.S5))
    }

    /** Called after a switch attempt; false means no handler could open the input. */
    var onSwitchResult: ((Boolean) -> Unit)? = null

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        isFocusable = false
        background = GradientDrawable().apply {
            setColor(Theme.SURFACE)
            cornerRadius = context.dp(24).toFloat()
            setStroke(context.dp(1), Theme.STROKE)
        }
        clipToOutline = true
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setDevices(emptyList())
    }

    fun setDevices(devices: List<ConnectedDevice>) {
        val focusedId = (findFocus()?.tag as? String)
        list.removeAllViews()
        list.addView(TextView(context).apply {
            style(24f, Theme.medium, Theme.TEXT)
            text = "Devices"
        })
        if (devices.isEmpty()) {
            list.addView(TextView(context).apply {
                style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
                text = "Nothing connected"
                setPadding(0, context.dp(Theme.S5), 0, 0)
            })
            return
        }
        var lastGroup: DeviceGroup? = null
        var focusTarget: View? = null
        var first: View? = null
        for (d in devices) {
            val g = d.kind.group()
            if (g != lastGroup) {
                lastGroup = g
                list.addView(TextView(context).apply {
                    style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2)
                    text = g.title.uppercase()
                    letterSpacing = 0.08f
                    setPadding(context.dp(4), context.dp(Theme.S5), 0, context.dp(Theme.S2))
                })
            }
            val row = row(d)
            if (first == null) first = row
            if (d.id == focusedId) focusTarget = row
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(Theme.S1) })
        }
        (focusTarget ?: if (focusedId != null) first else null)?.requestFocus()
    }

    private fun row(d: ConnectedDevice): View {
        val radius = context.dp(14).toFloat()
        val inputId = DeviceActions.passthroughInputId(d)
        val accent = DeviceAccent.of(d.kind)
        return LinearLayout(context).apply {
            tag = d.id
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = inputId != null
            setPadding(context.dp(12), context.dp(10), context.dp(16), context.dp(10))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                    setColor(0x26FFFFFF); cornerRadius = radius; setStroke(context.dp(2), 0x99FFFFFF.toInt())
                })
                addState(intArrayOf(), GradientDrawable().apply { setColor(0x0DFFFFFF); cornerRadius = radius })
            }
            val medal = ImageView(context).apply {
                setImageResource(DeviceIcons.res(d.kind))
                setColorFilter(Color.WHITE)
                setPadding(context.dp(9), context.dp(9), context.dp(9), context.dp(9))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor((accent and 0x00FFFFFF) or 0x55000000)
                    setStroke(context.dp(1), accent)
                }
            }
            addView(medal, LinearLayout.LayoutParams(context.dp(44), context.dp(44)))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    style(18f, Theme.medium, Theme.TEXT); text = d.name.ifBlank { DeviceCopy.label(d.kind) }; maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                val sub = DeviceCopy.subtitle(d, true)
                if (sub != d.name) addView(TextView(context).apply {
                    style(Theme.SP_LABEL + 1, Theme.regular, Theme.TEXT_2); text = sub; maxLines = 1
                })
            }
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(14) })
            if (inputId != null) {
                addView(TextView(context).apply {
                    style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2); text = "Switch to this input"
                    setPadding(context.dp(12), 0, context.dp(12), 0)
                })
            }
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(STATUS_ON) }
                contentDescription = "Connected"
            }, LinearLayout.LayoutParams(context.dp(8), context.dp(8)))
            if (inputId != null) setOnClickListener { switchTo(inputId) }
        }
    }

    private fun switchTo(inputId: String) {
        val ok = try {
            SystemActions.start(context, Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId)))
        } catch (e: RuntimeException) { false }
        onSwitchResult?.invoke(ok)
    }

    companion object {
        const val STATUS_ON = 0xFF22C55E.toInt()
    }
}
