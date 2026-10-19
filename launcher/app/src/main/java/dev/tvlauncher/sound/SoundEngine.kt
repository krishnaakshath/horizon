package dev.tvlauncher.sound

/** Platform seam: one cached, ready-to-start static sound. */
interface PcmTrack {
    /** Loads the PCM; false or throwing means failure (the engine then releases the track). */
    fun write(data: ShortArray): Boolean
    fun start(volume: Float)
    fun isPlaying(): Boolean
    fun release()
}

interface TrackFactory {
    /** Creates an empty track. May throw. */
    fun create(sampleRate: Int, sizeBytes: Int): PcmTrack
}

class Pcm(val samples: ShortArray, val sampleRate: Int)

interface PcmSource {
    /** Heavy (synthesis / file IO); only called on the builder thread. Null = nothing to play. */
    fun load(event: SoundEvent, s: SoundSettings): Pcm?

    /** Cheap stat of the custom file for [event] (mtime:length). Builder thread only. */
    fun customSignature(event: SoundEvent): String
}

/**
 * Platform-independent core of [SoundPlayer] (JVM testable).
 *
 * Threading: [play], [preview], [prewarm], [release] are callable from any thread and never throw.
 * [play] only does cache lookups on the caller thread; all synthesis, file IO, stats and track
 * creation run through [submit] (a single builder thread in production). [settings] is also
 * called from the builder thread, so it must be thread-safe (@Volatile / AtomicReference).
 * Cache state is guarded by [lock]; tracks are always released outside the lock.
 * After [release] nothing is cached and any in-flight build releases its own track.
 */
class SoundEngine(
    private val factory: TrackFactory,
    private val source: PcmSource,
    private val settings: () -> SoundSettings,
    private val budget: Int,
    private val clock: () -> Long,
    private val submit: (() -> Unit) -> Unit,
    private val scheduleRelease: (Long, () -> Unit) -> Unit
) {
    private class Entry(val key: String, val sig: String, val track: PcmTrack?, val bytes: Int)

    private val lock = Any()
    private val cache = HashMap<SoundEvent, Entry>()
    private val pending = HashSet<SoundEvent>()
    private val retiring = ArrayList<PcmTrack>()
    private val lastStat = HashMap<SoundEvent, Long>()
    private var cachedBytes = 0
    @Volatile private var released = false
    private val moveLimiter = RateLimiter(MOVE_MIN_INTERVAL_MS, clock)

    fun prewarm() = run {
        val s = settings()
        for (e in SoundEvent.values()) build(e, s, false)
    }

    fun play(event: SoundEvent) {
        try {
            val s = settings()
            if (released || !s.enabled || !s.isEventOn(event) || s.volume <= 0) return
            if (event == SoundEvent.MOVE && !moveLimiter.tryAcquire()) return
            val key = keyOf(event, s)
            var needBuild = false
            synchronized(lock) {
                val e = cache[event]
                if (e == null || e.key != key) {
                    // Miss: skip this sound, build in background.
                    needBuild = pending.add(event)
                } else {
                    e.track?.start(s.volume / 100f * HEADROOM)
                    if (s.useCustomFolder) {
                        // The full signature (wav mtime/size) needs a stat: verify on the builder
                        // thread, at most once per STAT_THROTTLE_MS per event.
                        val now = clock()
                        val last = lastStat[event]
                        if (last == null || now - last >= STAT_THROTTLE_MS) {
                            lastStat[event] = now
                            needBuild = pending.add(event)
                        }
                    }
                }
            }
            if (needBuild) submit {
                synchronized(lock) { pending.remove(event) }
                // Stale snapshot (settings changed since queued): drop.
                if (keyOf(event, settings()) == key) build(event, s, false)
            }
        } catch (e: Exception) {
        }
    }

    /** Plays regardless of toggles (settings previews); builds if needed, off-thread. */
    fun preview(event: SoundEvent) = run {
        val s = settings()
        val forced = s.copy(
            enabled = true,
            perEvent = s.perEvent + (event to true),
            volume = if (s.volume <= 0) 50 else s.volume
        )
        build(event, forced, false)
        synchronized(lock) {
            if (!released) cache[event]?.track?.start(forced.volume / 100f * HEADROOM)
        }
    }

    fun release() {
        val all = ArrayList<PcmTrack>()
        synchronized(lock) {
            released = true
            cache.values.forEach { it.track?.let(all::add) }
            all.addAll(retiring)
            cache.clear(); retiring.clear(); pending.clear(); cachedBytes = 0
        }
        all.forEach { runCatching { it.release() } }
    }

    private fun run(block: () -> Unit) {
        try {
            submit { if (!released) try { block() } catch (e: Throwable) { } }
        } catch (e: Exception) {
        }
    }

    private fun keyOf(event: SoundEvent, s: SoundSettings) = "${s.themeIdFor(event)}|${s.useCustomFolder}"

    private fun build(event: SoundEvent, s: SoundSettings, @Suppress("UNUSED_PARAMETER") unused: Boolean) {
        if (released) return
        val key = keyOf(event, s)
        val sig = if (s.useCustomFolder) "$key|${source.customSignature(event)}" else key
        synchronized(lock) { if (cache[event]?.sig == sig) return }

        var entry = Entry(key, sig, null, 0)
        var track: PcmTrack? = null
        try {
            val pcm = source.load(event, s)
            if (pcm != null && pcm.samples.isNotEmpty()) {
                val bytes = pcm.samples.size * 2
                if (bytes <= budget) {
                    val t = factory.create(pcm.sampleRate, bytes)
                    track = t
                    if (t.write(pcm.samples)) entry = Entry(key, sig, t, bytes)
                    else { track = null; runCatching { t.release() } }
                }
            }
        } catch (e: Exception) {
            track?.let { runCatching { it.release() } }
            entry = Entry(key, sig, null, 0)
        }

        val toRelease = ArrayList<PcmTrack>()
        synchronized(lock) {
            if (released) {
                entry.track?.let(toRelease::add)
            } else {
                cache.remove(event)?.let { cachedBytes -= it.bytes; retire(it.track, toRelease) }
                if (entry.track != null && cachedBytes + entry.bytes > budget) {
                    for (k in cache.keys.toList()) {
                        if (cachedBytes + entry.bytes <= budget) break
                        val o = cache[k] ?: continue
                        if (o.track?.isPlaying() == true) continue
                        cache.remove(k); cachedBytes -= o.bytes; retire(o.track, toRelease)
                    }
                    if (cachedBytes + entry.bytes > budget) {
                        toRelease.add(entry.track!!)
                        entry = Entry(key, sig, null, 0)
                    }
                }
                cache[event] = entry
                cachedBytes += entry.bytes
            }
        }
        toRelease.forEach { runCatching { it.release() } }
    }

    /** Call with [lock] held. Idle tracks go to [out]; sounding ones are tracked in [retiring]. */
    private fun retire(t: PcmTrack?, out: MutableList<PcmTrack>) {
        if (t == null) return
        if (runCatching { t.isPlaying() }.getOrDefault(false)) {
            retiring.add(t)
            scheduleRelease(RETIRE_DELAY_MS) {
                val mine = synchronized(lock) { retiring.remove(t) }
                if (mine) runCatching { t.release() }
            }
        } else out.add(t)
    }

    companion object {
        const val HEADROOM = 0.6f
        const val MOVE_MIN_INTERVAL_MS = 45L
        const val STAT_THROTTLE_MS = 2000L
        const val RETIRE_DELAY_MS = 600L
    }
}
