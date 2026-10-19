package dev.tvlauncher.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper

class AppRepository(private val context: Context) {
    private var receiver: BroadcastReceiver? = null

    fun load(): List<AppEntry> {
        val pm = context.packageManager
        val merged = LinkedHashMap<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val ai = info.activityInfo ?: continue
                if (ai.packageName == context.packageName) continue
                if (merged.containsKey(ai.packageName)) continue
                merged[ai.packageName] = AppEntry(
                    ai.packageName,
                    info.loadLabel(pm).toString(),
                    ai.name
                )
            }
        }
        return merged.values.toList()
    }

    fun register(onChange: () -> Unit) {
        unregister()
        val handler = Handler(Looper.getMainLooper())
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (Looper.myLooper() == Looper.getMainLooper()) onChange() else handler.post { onChange() }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(r, filter)
        receiver = r
    }

    fun unregister() {
        val r = receiver ?: return
        receiver = null
        try {
            context.unregisterReceiver(r)
        } catch (_: IllegalArgumentException) {
        }
    }
}
