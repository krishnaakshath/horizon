package dev.tvlauncher.sound

enum class SoundEvent { MOVE, SELECT, BACK, LAUNCH, MENU_OPEN, ERROR }

data class SoundTheme(val id: String, val name: String, val events: Map<SoundEvent, ToneSpec>)

/** Built-in synthesized themes. No audio assets ship in the APK. */
object SoundThemes {
    private fun t(f: Float, fe: Float = f, ms: Int, w: Wave, a: Int, d: Int, g: Float,
                  h: List<Pair<Int, Float>> = emptyList()) = ToneSpec(f, fe, ms, w, a, d, g, h)

    private val soft = SoundTheme("soft", "Soft", mapOf(
        SoundEvent.MOVE to t(520f, ms = 40, w = Wave.SINE, a = 4, d = 25, g = 0.35f),
        SoundEvent.SELECT to t(660f, 740f, 120, Wave.SINE, 6, 80, 0.5f),
        SoundEvent.BACK to t(520f, 400f, 120, Wave.SINE, 6, 80, 0.45f),
        SoundEvent.LAUNCH to t(440f, 880f, 260, Wave.SINE, 10, 170, 0.5f),
        SoundEvent.MENU_OPEN to t(600f, 720f, 150, Wave.SINE, 8, 100, 0.45f),
        SoundEvent.ERROR to t(260f, 220f, 220, Wave.SINE, 6, 150, 0.5f)
    ))

    private val crisp = SoundTheme("crisp", "Crisp", mapOf(
        SoundEvent.MOVE to t(1400f, ms = 22, w = Wave.TRIANGLE, a = 1, d = 12, g = 0.4f),
        SoundEvent.SELECT to t(1100f, 1300f, 70, Wave.TRIANGLE, 2, 45, 0.5f),
        SoundEvent.BACK to t(1000f, 750f, 70, Wave.TRIANGLE, 2, 45, 0.45f),
        SoundEvent.LAUNCH to t(800f, 1600f, 180, Wave.TRIANGLE, 3, 110, 0.5f),
        SoundEvent.MENU_OPEN to t(1200f, 1500f, 90, Wave.TRIANGLE, 2, 55, 0.45f),
        SoundEvent.ERROR to t(300f, 240f, 160, Wave.TRIANGLE, 3, 100, 0.5f)
    ))

    private val glass = SoundTheme("glass", "Glass", mapOf(
        SoundEvent.MOVE to t(1760f, 1800f, 45, Wave.SINE, 2, 28, 0.3f, listOf(2 to 0.3f, 3 to 0.12f)),
        SoundEvent.SELECT to t(1320f, 1480f, 180, Wave.SINE, 3, 120, 0.45f, listOf(2 to 0.35f, 3 to 0.2f)),
        SoundEvent.BACK to t(1180f, 880f, 160, Wave.SINE, 3, 110, 0.4f, listOf(2 to 0.3f, 3 to 0.15f)),
        SoundEvent.LAUNCH to t(880f, 1760f, 340, Wave.SINE, 6, 230, 0.5f, listOf(2 to 0.4f, 3 to 0.25f)),
        SoundEvent.MENU_OPEN to t(1100f, 1500f, 220, Wave.SINE, 4, 150, 0.42f, listOf(2 to 0.35f, 3 to 0.18f)),
        SoundEvent.ERROR to t(420f, 330f, 260, Wave.SINE, 4, 180, 0.45f, listOf(2 to 0.25f))
    ))

    private val retro = SoundTheme("retro", "Retro", mapOf(
        SoundEvent.MOVE to t(880f, ms = 30, w = Wave.SQUARE_SOFT, a = 1, d = 18, g = 0.25f),
        SoundEvent.SELECT to t(660f, 990f, 110, Wave.SQUARE_SOFT, 2, 70, 0.3f),
        SoundEvent.BACK to t(660f, 330f, 110, Wave.SQUARE_SOFT, 2, 70, 0.3f),
        SoundEvent.LAUNCH to t(330f, 1320f, 280, Wave.SQUARE_SOFT, 4, 180, 0.3f),
        SoundEvent.MENU_OPEN to t(500f, 800f, 140, Wave.SQUARE_SOFT, 3, 90, 0.28f),
        SoundEvent.ERROR to t(200f, 150f, 240, Wave.SQUARE_SOFT, 3, 160, 0.3f)
    ))

    val all: List<SoundTheme> = listOf(soft, crisp, glass, retro)

    fun byId(id: String): SoundTheme = all.firstOrNull { it.id == id } ?: glass
}
