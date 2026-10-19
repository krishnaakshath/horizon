package dev.tvlauncher.weather

object Units {
    const val C = "C"
    const val F = "F"

    fun normalize(unit: String?): String = if (unit == F) F else C

    fun format(tempC: Double, unit: String): String {
        if (!tempC.isFinite()) return "--"
        val v = if (unit.equals(F, ignoreCase = true)) tempC * 9.0 / 5.0 + 32.0 else tempC
        val abs = Math.floor(Math.abs(v) + 0.5).toLong()
        val signed = if (v < 0) -abs else abs
        return "$signed°"
    }
}
