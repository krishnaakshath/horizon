package dev.tvlauncher.weather

object TimeAgo {
    fun format(nowMs: Long, thenMs: Long): String {
        val mins = (nowMs - thenMs) / 60_000L
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 60 * 24 -> "${mins / 60} h ago"
            else -> "${mins / (60 * 24)} d ago"
        }
    }
}
