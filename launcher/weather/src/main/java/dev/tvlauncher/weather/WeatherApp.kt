package dev.tvlauncher.weather

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Screen-on and unlock cannot be declared in the manifest on current Android, so while this
 * process is alive (it starts whenever the launcher reads the forecast) they are listened for
 * here. A killed process simply misses them; the wake broadcast and the launcher cover that.
 */
class WeatherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            val r = TriggerReceiver()
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(r, filter)
        } catch (e: RuntimeException) {
        }
    }
}
