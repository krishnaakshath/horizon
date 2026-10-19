package dev.tvlauncher.ambient

import kotlin.math.roundToLong

/** Binding contract with the companion weather app (the launcher itself has no INTERNET). */
object WeatherContract {
    const val PACKAGE = "dev.tvlauncher.weather"
    const val AUTHORITY = "dev.tvlauncher.weather"
    const val CURRENT_URI = "content://$AUTHORITY/current"
    const val DAILY_URI = "content://$AUTHORITY/daily"
    const val ACTION_SETTINGS = "dev.tvlauncher.weather.action.SETTINGS"
    const val ACTION_REFRESH = "dev.tvlauncher.weather.action.REFRESH"
    const val MAX_DAYS = 5
    val CURRENT_COLUMNS = listOf("location", "temp_c", "feels_c", "code", "condition", "is_day", "high_c", "low_c",
        "humidity", "wind_kmh", "updated_ms", "unit")
    val DAILY_COLUMNS = listOf("day_ms", "code", "condition", "high_c", "low_c")
}

data class WeatherCurrent(
    val location: String,
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val condition: String,
    val isDay: Boolean,
    /** NULL in the provider when today's daily entry is missing/malformed. */
    val highC: Double?,
    val lowC: Double?,
    val humidity: Int,
    val windKmh: Double,
    val updatedMs: Long,
    /** Display preference only; all values are Celsius. */
    val unit: String
)

data class WeatherDay(val dayMs: Long, val code: Int, val condition: String, val highC: Double, val lowC: Double)

data class WeatherSnapshot(val current: WeatherCurrent, val daily: List<WeatherDay>)

/** Rows as column-name -> value maps, so parsing is testable without a Cursor. */
interface WeatherSource {
    /** null when the provider is missing. */
    fun queryCurrent(): List<Map<String, Any?>>?
    fun queryDaily(): List<Map<String, Any?>>?
}

enum class WxIcon(val key: String) {
    CLEAR_DAY("ic_wx_clear_day"),
    CLEAR_NIGHT("ic_wx_clear_night"),
    PARTLY_DAY("ic_wx_partly_day"),
    PARTLY_NIGHT("ic_wx_partly_night"),
    CLOUDY("ic_wx_cloudy"),
    FOG("ic_wx_fog"),
    DRIZZLE("ic_wx_drizzle"),
    RAIN("ic_wx_rain"),
    SNOW("ic_wx_snow"),
    STORM("ic_wx_storm")
}

object WeatherFormat {
    /** "21°" rounded half-up in the display unit; never "-0°". */
    fun temp(tempC: Double?, unit: String?): String {
        if (tempC == null || tempC.isNaN() || tempC.isInfinite()) return "–"
        val v = if (unit.equals("F", ignoreCase = true)) tempC * 9.0 / 5.0 + 32.0 else tempC
        val r = v.roundToLong()
        return "${if (r == 0L) 0L else r}°"
    }

    /** "H 24°  L 13°", or null when either side is missing (never prints "null"). */
    fun highLow(highC: Double?, lowC: Double?, unit: String?): String? {
        if (highC == null || lowC == null || highC.isNaN() || lowC.isNaN()) return null
        return "H ${temp(highC, unit)}  L ${temp(lowC, unit)}"
    }

    fun iconFor(code: Int, isDay: Boolean): WxIcon = when (code) {
        0 -> if (isDay) WxIcon.CLEAR_DAY else WxIcon.CLEAR_NIGHT
        1, 2 -> if (isDay) WxIcon.PARTLY_DAY else WxIcon.PARTLY_NIGHT
        3 -> WxIcon.CLOUDY
        45, 48 -> WxIcon.FOG
        in 51..57 -> WxIcon.DRIZZLE
        in 61..67, in 80..82 -> WxIcon.RAIN
        in 71..77, 85, 86 -> WxIcon.SNOW
        in 95..99 -> WxIcon.STORM
        else -> WxIcon.CLOUDY
    }

    /** Short English text for a WMO code, used when the provider sends no condition. */
    fun conditionFor(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }
}

object WeatherFreshness {
    const val STALE_MS = 3 * 60 * 60_000L
    const val UNAVAILABLE = "Weather unavailable"

    fun isUsable(updatedMs: Long?, nowMs: Long): Boolean =
        updatedMs != null && updatedMs > 0 && nowMs - updatedMs <= STALE_MS

    fun label(updatedMs: Long?, nowMs: Long): String {
        if (!isUsable(updatedMs, nowMs)) return UNAVAILABLE
        val age = (nowMs - updatedMs!!).coerceAtLeast(0)
        val min = age / 60_000L
        return when {
            min < 1 -> "Updated just now"
            min < 60 -> "Updated $min min ago"
            else -> "Updated ${min / 60} h ago"
        }
    }
}

/** Tolerant row parsing: numbers may arrive as any Number or String; bad rows are skipped. */
object WeatherParser {
    fun current(row: Map<String, Any?>): WeatherCurrent? {
        val temp = dbl(row["temp_c"])
        if (temp.isNaN()) return null
        val code = int(row["code"], -1)
        val unit = str(row["unit"]).uppercase().takeIf { it == "C" || it == "F" } ?: "C"
        return WeatherCurrent(
            location = str(row["location"]),
            tempC = temp,
            feelsC = dbl(row["feels_c"]),
            code = code,
            condition = str(row["condition"]).ifBlank { WeatherFormat.conditionFor(code) },
            isDay = int(row["is_day"], 1) != 0,
            highC = dbl(row["high_c"]).takeUnless { it.isNaN() },
            lowC = dbl(row["low_c"]).takeUnless { it.isNaN() },
            humidity = int(row["humidity"], -1),
            windKmh = dbl(row["wind_kmh"]),
            updatedMs = lng(row["updated_ms"]),
            unit = unit
        )
    }

    fun daily(rows: List<Map<String, Any?>>): List<WeatherDay> = rows.mapNotNull { r ->
        val day = lng(r["day_ms"])
        val hi = dbl(r["high_c"])
        val lo = dbl(r["low_c"])
        if (day <= 0 || hi.isNaN() || lo.isNaN()) return@mapNotNull null
        val code = int(r["code"], -1)
        WeatherDay(day, code, str(r["condition"]).ifBlank { WeatherFormat.conditionFor(code) }, hi, lo)
    }.take(WeatherContract.MAX_DAYS)

    private fun str(v: Any?): String = (v as? String)?.trim() ?: ""
    private fun dbl(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull() ?: Double.NaN
        else -> Double.NaN
    }
    private fun lng(v: Any?): Long = when (v) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: 0L
        else -> 0L
    }
    private fun int(v: Any?, def: Int): Int = when (v) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull() ?: def
        else -> def
    }
}

/** Reads a snapshot from any [WeatherSource]. Never throws; null when nothing usable. Call off the main thread. */
class WeatherReader(private val source: WeatherSource) {
    fun read(): WeatherSnapshot? = try {
        val cur = source.queryCurrent()?.firstOrNull()?.let { WeatherParser.current(it) }
        if (cur == null) null else {
            val days = try { source.queryDaily()?.let { WeatherParser.daily(it) } } catch (e: Exception) { null }
            WeatherSnapshot(cur, days ?: emptyList())
        }
    } catch (e: Exception) {
        null
    }
}

/** Display strings for the weather block; null fields are hidden (pure, so testable). */
data class WeatherDisplay(
    val available: Boolean,
    val icon: WxIcon?,
    val temp: String?,
    val condition: String?,
    val highLow: String?,
    val location: String?,
    val freshness: String?
) {
    companion object {
        fun from(snap: WeatherSnapshot?, nowMs: Long): WeatherDisplay {
            val c = snap?.current
            if (c == null || !WeatherFreshness.isUsable(c.updatedMs, nowMs)) {
                return WeatherDisplay(false, null, null, WeatherFreshness.UNAVAILABLE, null, null, null)
            }
            return WeatherDisplay(
                available = true,
                icon = WeatherFormat.iconFor(c.code, c.isDay),
                temp = WeatherFormat.temp(c.tempC, c.unit),
                condition = c.condition.takeIf { it.isNotBlank() },
                highLow = WeatherFormat.highLow(c.highC, c.lowC, c.unit),
                location = c.location.takeIf { it.isNotBlank() },
                freshness = WeatherFreshness.label(c.updatedMs, nowMs)
            )
        }
    }
}

/**
 * Pure state machine for provider calls (no threads; the caller holds a lock).
 *
 * HEALTHY: one call at a time (RUN; a concurrent query is REJECT_BUSY).
 * A timeout opens the breaker for [breakerMs] (REJECT_BREAKER). A call that times out before the
 * worker picked it up (SUBMITTED) is cancelled for good: [tryStart] refuses it later.
 * When the breaker expires and the previous call is STILL running (a provider stuck in a binder
 * call ignores interrupts), that worker is abandoned and the query runs on a fresh worker
 * (RUN_ON_FRESH_WORKER). At most [maxAbandoned] abandoned threads may be alive; one more abandon
 * enters DEGRADED for the rest of the process (no more threads, callers show "unavailable").
 * Decision: success does NOT reset the counter (a stuck thread is still stuck); an abandoned call
 * that eventually returns frees its slot, because its thread is no longer leaked.
 */
class StuckProviderPolicy(private val breakerMs: Long = 60_000L, private val maxAbandoned: Int = 2) {
    enum class Call { IDLE, SUBMITTED, RUNNING }
    enum class Decision { RUN, RUN_ON_FRESH_WORKER, REJECT_BUSY, REJECT_BREAKER, DEGRADED }

    var call = Call.IDLE
        private set
    /** Ticket of the current call; stale tickets (abandoned/old calls) are ignored. */
    var generation = 0L
        private set
    var abandonedAlive = 0
        private set
    var isDegraded = false
        private set
    private var breakerUntil = NO_BREAKER
    private val abandoned = HashSet<Long>()

    fun isBreakerOpen(now: Long) = breakerUntil != NO_BREAKER && now < breakerUntil

    fun beforeQuery(now: Long): Decision {
        if (isDegraded) return Decision.DEGRADED
        if (isBreakerOpen(now)) return Decision.REJECT_BREAKER
        val afterTimeout = breakerUntil != NO_BREAKER
        if (call != Call.IDLE) {
            if (!afterTimeout) return Decision.REJECT_BUSY
            if (abandonedAlive >= maxAbandoned) { isDegraded = true; return Decision.DEGRADED }
            abandonedAlive++
            abandoned += generation
            begin()
            return Decision.RUN_ON_FRESH_WORKER
        }
        begin()
        return Decision.RUN
    }

    private fun begin() {
        generation++
        call = Call.SUBMITTED
        breakerUntil = NO_BREAKER
    }

    /** Worker picked the call up. False: it was cancelled/replaced meanwhile, do not call the provider. */
    fun tryStart(gen: Long): Boolean {
        if (gen != generation || call != Call.SUBMITTED) return false
        call = Call.RUNNING
        return true
    }

    fun onTimeout(gen: Long, now: Long) {
        if (gen != generation) return
        breakerUntil = now + breakerMs
        if (call == Call.SUBMITTED) call = Call.IDLE // never started: will never run
    }

    fun onFinished(gen: Long) {
        if (abandoned.remove(gen)) { abandonedAlive--; return }
        if (gen == generation && call == Call.RUNNING) call = Call.IDLE
    }

    private companion object {
        const val NO_BREAKER = Long.MIN_VALUE
    }
}

/**
 * Bounded provider access driven by [StuckProviderPolicy]: one single-thread worker (replaced
 * when a stuck call is abandoned, at most 2 times, so <= 3 threads ever), a [timeoutMs] wait per
 * query and a 60 s breaker. Rejected queries return the last good result for that table only if
 * it is younger than [WeatherFreshness.STALE_MS] (3 h); DEGRADED always returns null so the UI
 * shows "Weather unavailable". Never throws.
 */
class TimeoutWeatherSource(
    private val delegate: WeatherSource,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val threadFactory: java.util.concurrent.ThreadFactory = java.util.concurrent.ThreadFactory { r ->
        Thread(r, "weather-query").apply { isDaemon = true }
    }
) : WeatherSource {
    private val lock = Any()
    private val policy = StuckProviderPolicy()
    private val cache = HashMap<Int, Pair<Long, List<Map<String, Any?>>>>()
    private var worker = newWorker()

    val isInFlight: Boolean get() = synchronized(lock) { policy.call != StuckProviderPolicy.Call.IDLE }
    val isBreakerOpen: Boolean get() = synchronized(lock) { policy.isBreakerOpen(clock()) }
    val isDegraded: Boolean get() = synchronized(lock) { policy.isDegraded }

    override fun queryCurrent(): List<Map<String, Any?>>? = bounded(CURRENT) { delegate.queryCurrent() }
    override fun queryDaily(): List<Map<String, Any?>>? = bounded(DAILY) { delegate.queryDaily() }

    private fun newWorker() = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 30, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.LinkedBlockingQueue(), threadFactory
    ).apply { allowCoreThreadTimeOut(true) }

    private fun cached(kind: Int): List<Map<String, Any?>>? {
        val (at, rows) = cache[kind] ?: return null
        return if (clock() - at <= WeatherFreshness.STALE_MS) rows else null
    }

    private fun bounded(kind: Int, q: () -> List<Map<String, Any?>>?): List<Map<String, Any?>>? {
        val gen: Long
        val w: java.util.concurrent.ThreadPoolExecutor
        synchronized(lock) {
            when (policy.beforeQuery(clock())) {
                StuckProviderPolicy.Decision.DEGRADED -> return null
                StuckProviderPolicy.Decision.REJECT_BUSY, StuckProviderPolicy.Decision.REJECT_BREAKER -> return cached(kind)
                StuckProviderPolicy.Decision.RUN_ON_FRESH_WORKER -> {
                    worker.shutdownNow() // abandon the stuck thread
                    worker = newWorker()
                }
                StuckProviderPolicy.Decision.RUN -> Unit
            }
            gen = policy.generation
            w = worker
        }
        val f = try {
            w.submit(java.util.concurrent.Callable {
                if (!synchronized(lock) { policy.tryStart(gen) }) return@Callable null
                try { q() } finally { synchronized(lock) { policy.onFinished(gen) } }
            })
        } catch (e: Exception) {
            synchronized(lock) { policy.onTimeout(gen, clock()) }
            return synchronized(lock) { cached(kind) }
        }
        return try {
            val r = f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (r != null) synchronized(lock) { cache[kind] = clock() to r }
            r
        } catch (e: java.util.concurrent.TimeoutException) {
            synchronized(lock) { policy.onTimeout(gen, clock()) }
            f.cancel(true)
            synchronized(lock) { cached(kind) }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3_000L
        private const val CURRENT = 0
        private const val DAILY = 1
    }
}

/** At most one event per [intervalMs]; used for the REFRESH broadcast. */
class RefreshThrottle(private val intervalMs: Long) {
    private var last = Long.MIN_VALUE

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        if (last != Long.MIN_VALUE && nowMs - last < intervalMs) return false
        last = nowMs
        return true
    }
}
