package dev.tvlauncher.ui

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/** Clock (self-updating each minute), network status and an Inputs shortcut. */
class TopBar(context: Context, onInputs: () -> Unit) : LinearLayout(context) {
    private val clock = TextClock(context).apply {
        format12Hour = "h:mm a"
        format24Hour = "HH:mm"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
    }
    private val network = TextView(context).apply {
        setTextColor(0xFFCCCCCC.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(0, 0, context.dp(24), 0)
    }
    val inputsButton = FocusButton(context, "Inputs", onInputs)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(clock, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(network)
        addView(inputsButton)
        refreshNetwork()
    }

    fun refreshNetwork() {
        network.text = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi connected"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Online"
            }
        } catch (e: Exception) {
            "Offline"
        }
        visibility = View.VISIBLE
    }
}
