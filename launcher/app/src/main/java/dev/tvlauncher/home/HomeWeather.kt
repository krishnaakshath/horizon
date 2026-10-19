package dev.tvlauncher.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.tvlauncher.ambient.WeatherCompanion
import dev.tvlauncher.ambient.WeatherReader
import dev.tvlauncher.ui.TopBar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Feeds the top bar's weather chip from the weather companion's content provider. Reads run on
 * one background thread through the shared bounded source (WeatherCompanion.source, 3 s timeout
 * and breaker), never on the main thread. Each read also asks the companion to refresh; that
 * broadcast is throttled to once per 15 min by WeatherCompanion.
 *
 * Lifecycle: [resume] in onResume reads at once and then every 10 min; [pause] in onPause stops
 * the schedule and drops in-flight results; [release] in onDestroy ends the thread.
 */
class HomeWeather(context: Context, private val topBar: TopBar) {
    private val app = context.applicationContext
    private val prefs = HomeExtrasPrefs(app)
    private val main = Handler(Looper.getMainLooper())
    private var exec: ExecutorService? = null
    private var resumed = false
    private var generation = 0
    private val tick = Runnable { read() }

    fun resume() {
        resumed = true
        generation++
        main.removeCallbacks(tick)
        if (!prefs.weatherInTopBar) return topBar.showWeather(WeatherChipModel.HIDDEN)
        read()
    }

    fun pause() {
        resumed = false
        generation++
        main.removeCallbacks(tick)
    }

    fun release() {
        pause()
        exec?.shutdownNow()
        exec = null
    }

    private fun read() {
        if (!resumed) return
        val gen = generation
        val e = exec ?: Executors.newSingleThreadExecutor { r -> Thread(r, "home-weather").apply { isDaemon = true } }.also { exec = it }
        try {
            e.execute {
                WeatherCompanion.requestRefresh(app)
                val read = WeatherReader(WeatherCompanion.source(app)).readState()
                val chip = WeatherChipModel.from(read, System.currentTimeMillis(), enabled = true)
                main.post { if (gen == generation && resumed) topBar.showWeather(chip) }
            }
        } catch (t: RuntimeException) {
            // Executor shut down: nothing to show.
        }
        main.postDelayed(tick, READ_EVERY_MS)
    }

    private companion object {
        const val READ_EVERY_MS = 10 * 60_000L
    }
}
