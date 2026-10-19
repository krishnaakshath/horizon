package dev.tvlauncher.sound

enum class SoundEvent { MOVE, SELECT, BACK, LAUNCH, MENU_OPEN, ERROR }

data class SoundTheme(val id: String, val name: String, val events: Map<SoundEvent, ToneSpec>)

/**
 * Built-in synthesized themes. No audio assets ship in the APK. [all] and [byId] are static
 * metadata (raw specs, placeholder gains) and never render; use [spec] for playable gains.
 */
object SoundThemes {
    // Loudness targets (RMS, fraction of full scale). Every audible event of every theme is
    // calibrated to its target so switching themes does not change perceived loudness (+-2 dB).
    // Navigation is deliberately quiet; launch is the most prominent sound.
    private fun target(e: SoundEvent) = when (e) {
        SoundEvent.MOVE -> 0.035        // very short, quiet, soft-attack (>= 4 ms), nothing above ~6 kHz
        SoundEvent.SELECT -> 0.08
        SoundEvent.BACK -> 0.07
        SoundEvent.LAUNCH -> 0.095      // ascending two-note motif with a short tail
        SoundEvent.MENU_OPEN -> 0.07
        SoundEvent.ERROR -> 0.08        // gentle descending pair, never a buzzer
    }

    fun targetRms(e: SoundEvent): Double = target(e)

    private fun rms(a: ShortArray): Double =
        if (a.isEmpty()) 0.0 else Math.sqrt(a.sumOf { it.toDouble() * it } / a.size) / Short.MAX_VALUE

    private val gains = java.util.concurrent.ConcurrentHashMap<String, Float>()

    /** Renders to find the gain that hits the event target RMS. Expensive: builder thread only. */
    private fun calibrateGain(e: SoundEvent, spec: ToneSpec): Float {
        var s = spec.copy(gain = 0.3f)
        repeat(5) {
            val r = rms(ToneSynth.render(s))
            if (r > 0.0) s = s.copy(gain = (s.gain * (target(e) / r)).toFloat().coerceIn(0.01f, 0.95f))
        }
        return s.gain
    }

    /**
     * The playable, loudness-calibrated spec. Calibration is lazy (per theme/event, cached) and
     * renders, so call only from the sound-builder thread, never the UI thread.
     */
    fun spec(themeId: String, e: SoundEvent): ToneSpec {
        val t = byId(themeId)
        val raw = t.events.getValue(e)
        if (raw.gain <= 0f) return raw
        return raw.copy(gain = gains.computeIfAbsent("${t.id}/${e.name}") { calibrateGain(e, raw) })
    }

    internal fun resetCalibrationForTest() = gains.clear()

    private val SILENT = ToneSpec(440f, durationMs = 20, wave = Wave.SINE, attackMs = 4, decayMs = 10, gain = 0f)

    private fun n(f: Float, fe: Float = f, ms: Int, w: Wave = Wave.SINE, a: Int, d: Int,
                  h: List<Pair<Int, Float>> = emptyList(), fm: FmSpec? = null,
                  tail: List<Pair<Int, Float>>? = null, second: SecondNote? = null) =
        ToneSpec(f, fe, ms, w, a, d, 0.3f, h, fm, tail?.let { TailSpec(it) }, second)

    private fun theme(id: String, name: String, events: Map<SoundEvent, ToneSpec>) =
        SoundTheme(id, name, SoundEvent.values().associateWith { e -> events[e] ?: SILENT })

    private val soft = theme("soft", "Soft", mapOf(
        SoundEvent.MOVE to n(480f, ms = 48, a = 6, d = 28, h = listOf(2 to 0.15f)),
        SoundEvent.SELECT to n(620f, 700f, 150, a = 8, d = 95, tail = listOf(55 to 0.28f)),
        SoundEvent.BACK to n(520f, 420f, 150, a = 8, d = 95, tail = listOf(55 to 0.25f)),
        SoundEvent.LAUNCH to n(392f, ms = 340, a = 10, d = 200, tail = listOf(70 to 0.3f, 140 to 0.15f),
            second = SecondNote(120, 587.3f)),
        SoundEvent.MENU_OPEN to n(560f, 660f, 170, a = 10, d = 110, tail = listOf(60 to 0.25f)),
        SoundEvent.ERROR to n(330f, ms = 300, a = 10, d = 170, tail = listOf(70 to 0.2f),
            second = SecondNote(130, 262f))
    ))

    private val crisp = theme("crisp", "Crisp", mapOf(
        SoundEvent.MOVE to n(1100f, ms = 32, w = Wave.TRIANGLE, a = 4, d = 14),
        SoundEvent.SELECT to n(1000f, 1200f, 90, Wave.TRIANGLE, a = 3, d = 55),
        SoundEvent.BACK to n(900f, 700f, 90, Wave.TRIANGLE, a = 3, d = 55),
        SoundEvent.LAUNCH to n(784f, ms = 230, w = Wave.TRIANGLE, a = 3, d = 130,
            tail = listOf(45 to 0.2f), second = SecondNote(80, 1175f)),
        SoundEvent.MENU_OPEN to n(1100f, 1400f, 110, Wave.TRIANGLE, a = 3, d = 65),
        SoundEvent.ERROR to n(440f, ms = 250, w = Wave.TRIANGLE, a = 4, d = 140,
            second = SecondNote(100, 349f))
    ))

    // Premium: FM shimmer on a clean sine with a soft two-tap tail.
    private val glass = theme("glass", "Glass", mapOf(
        SoundEvent.MOVE to n(1568f, ms = 50, a = 5, d = 26, fm = FmSpec(1.5f, 0.4f, 20)),
        SoundEvent.SELECT to n(1318.5f, 1396.9f, 210, a = 4, d = 130, fm = FmSpec(3.5f, 1.2f, 90),
            tail = listOf(48 to 0.3f, 96 to 0.15f)),
        SoundEvent.BACK to n(1174.7f, 987.8f, 200, a = 4, d = 120, fm = FmSpec(3.5f, 1.0f, 80),
            tail = listOf(48 to 0.28f, 96 to 0.14f)),
        SoundEvent.LAUNCH to n(880f, ms = 420, a = 5, d = 250, fm = FmSpec(3.5f, 1.4f, 120),
            tail = listOf(60 to 0.3f, 120 to 0.18f), second = SecondNote(110, 1318.5f)),
        SoundEvent.MENU_OPEN to n(1100f, 1480f, 240, a = 4, d = 150, fm = FmSpec(3.5f, 1.1f, 100),
            tail = listOf(55 to 0.28f, 110 to 0.14f)),
        SoundEvent.ERROR to n(523.3f, ms = 340, a = 6, d = 200, fm = FmSpec(2.76f, 0.6f, 90),
            tail = listOf(60 to 0.2f), second = SecondNote(130, 392f))
    ))

    private val retro = theme("retro", "Retro", mapOf(
        SoundEvent.MOVE to n(440f, ms = 30, w = Wave.SQUARE_SOFT, a = 4, d = 16),
        SoundEvent.SELECT to n(660f, 990f, 110, Wave.SQUARE_SOFT, a = 3, d = 70),
        SoundEvent.BACK to n(660f, 330f, 110, Wave.SQUARE_SOFT, a = 3, d = 70),
        SoundEvent.LAUNCH to n(330f, ms = 280, w = Wave.SQUARE_SOFT, a = 4, d = 160,
            second = SecondNote(110, 660f, 1320f)),
        SoundEvent.MENU_OPEN to n(500f, 800f, 140, Wave.SQUARE_SOFT, a = 3, d = 90),
        SoundEvent.ERROR to n(300f, ms = 260, w = Wave.SQUARE_SOFT, a = 4, d = 150,
            second = SecondNote(120, 200f))
    ))

    // Airy FM bell: rising sweeps for SELECT/LAUNCH, whisper-soft MOVE.
    private val aurora = theme("aurora", "Aurora", mapOf(
        SoundEvent.MOVE to n(1000f, ms = 55, a = 8, d = 30, fm = FmSpec(2f, 0.4f, 25)),
        SoundEvent.SELECT to n(700f, 1400f, 270, a = 12, d = 170, fm = FmSpec(1.5f, 1.6f, 150),
            tail = listOf(60 to 0.3f, 120 to 0.18f)),
        SoundEvent.BACK to n(1200f, 800f, 210, a = 10, d = 120, fm = FmSpec(1.5f, 1.0f, 100),
            tail = listOf(55 to 0.26f, 110 to 0.14f)),
        SoundEvent.LAUNCH to n(660f, 1000f, 440, a = 15, d = 280, fm = FmSpec(1.5f, 1.8f, 200),
            tail = listOf(70 to 0.3f, 140 to 0.2f, 210 to 0.12f),
            second = SecondNote(150, 1000f, 1760f)),
        SoundEvent.MENU_OPEN to n(900f, 1300f, 290, a = 12, d = 160, fm = FmSpec(1.5f, 1.2f, 120),
            tail = listOf(60 to 0.28f, 120 to 0.15f)),
        SoundEvent.ERROR to n(440f, 400f, 330, a = 10, d = 190, fm = FmSpec(1.5f, 0.5f, 80),
            tail = listOf(65 to 0.2f), second = SecondNote(130, 350f, 320f))
    ))

    // Low, warm thumps; minimal and unobtrusive.
    private val pulse = theme("pulse", "Pulse", mapOf(
        SoundEvent.MOVE to n(150f, ms = 40, a = 6, d = 22),
        SoundEvent.SELECT to n(180f, 110f, 140, a = 4, d = 80),
        SoundEvent.BACK to n(140f, 90f, 140, a = 4, d = 80),
        SoundEvent.LAUNCH to n(98f, ms = 290, a = 8, d = 170, tail = listOf(70 to 0.25f),
            second = SecondNote(110, 147f)),
        SoundEvent.MENU_OPEN to n(130f, 170f, 150, a = 6, d = 90),
        SoundEvent.ERROR to n(110f, ms = 270, a = 8, d = 150, second = SecondNote(120, 82f))
    ))

    // Only SELECT / BACK / LAUNCH are audible; MOVE, MENU_OPEN and ERROR are silent.
    private val minimal = theme("minimal", "Minimal", mapOf(
        SoundEvent.SELECT to n(880f, ms = 70, a = 4, d = 40),
        SoundEvent.BACK to n(660f, 550f, 70, a = 4, d = 40),
        SoundEvent.LAUNCH to n(660f, ms = 210, a = 5, d = 120, second = SecondNote(70, 880f))
    ))

    // Gentle bell chimes (inharmonic FM).
    private val bell = theme("bell", "Bell", mapOf(
        SoundEvent.MOVE to n(1318.5f, ms = 55, a = 8, d = 28, fm = FmSpec(2.76f, 0.3f, 22)),
        SoundEvent.SELECT to n(1568f, ms = 300, a = 2, d = 200, fm = FmSpec(3.5f, 1.5f, 150),
            tail = listOf(70 to 0.25f, 140 to 0.12f)),
        SoundEvent.BACK to n(1174.7f, ms = 280, a = 2, d = 190, fm = FmSpec(3.5f, 1.3f, 140),
            tail = listOf(70 to 0.22f, 140 to 0.1f)),
        SoundEvent.LAUNCH to n(1046.5f, ms = 440, a = 2, d = 260, fm = FmSpec(3.5f, 1.6f, 180),
            tail = listOf(80 to 0.28f, 160 to 0.15f), second = SecondNote(130, 1568f)),
        SoundEvent.MENU_OPEN to n(1396.9f, ms = 300, a = 3, d = 190, fm = FmSpec(3.5f, 1.2f, 130),
            tail = listOf(70 to 0.22f, 140 to 0.1f)),
        SoundEvent.ERROR to n(698.5f, ms = 380, a = 4, d = 220, fm = FmSpec(2.76f, 0.5f, 100),
            tail = listOf(80 to 0.2f), second = SecondNote(140, 554.4f))
    ))

    val all: List<SoundTheme> = listOf(soft, crisp, glass, retro, aurora, pulse, minimal, bell)

    fun byId(id: String): SoundTheme = all.firstOrNull { it.id == id } ?: glass
}
