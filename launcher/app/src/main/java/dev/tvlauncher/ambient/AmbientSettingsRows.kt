package dev.tvlauncher.ambient

/**
 * Declarative list of the ambient/wallpaper rows the launcher's Settings page must host. The
 * controller renders each [Row] by [Kind] and calls [toggle]/[step] (then WallpaperStore.save);
 * ACTION "wallpaper" opens [WallpaperPickerPanel], CARD "notificationAccess" hosts
 * [NotificationAccessCard]. Pure, so visibility rules are unit-tested.
 */
object AmbientSettingsRows {
    enum class Kind { ACTION, TOGGLE, STEPPER, CHOICE, CARD }

    data class Row(
        val key: String,
        val title: String,
        val kind: Kind,
        val value: String = "",
        val visible: Boolean = true,
        /** One line shown under the row, or null. */
        val hint: String? = null
    )

    private val INTERVALS = intArrayOf(30, 60, 120, 300, 600)
    private val IDLE = intArrayOf(1, 2, 5, 10, 15, 30, 60, 120)
    private fun onOff(b: Boolean) = if (b) "On" else "Off"
    private fun interval(sec: Int) = if (sec < 60) "$sec s" else "${sec / 60} min"

    fun rows(s: WallpaperSettings, accessGranted: Boolean): List<Row> {
        val slideSec = slideInterval(s.source)
        val wallpaper = when (val src = s.source) {
            is WallpaperSource.BuiltIn -> WallpaperCatalog.meta(src.id).title
            is WallpaperSource.UserImage -> src.file.nameWithoutExtension
            is WallpaperSource.Slideshow ->
                if (src.folder.name == "wallpapers") "Slideshow of my pictures" else "${CollectionScanner.displayName(src.folder.name)} slideshow"
            is WallpaperSource.BuiltInSlideshow -> "${src.collection} slideshow"
        }
        return listOf(
            Row("wallpaper", "Wallpaper", Kind.ACTION, wallpaper),
            Row("slideshowInterval", "Change picture every", Kind.STEPPER,
                interval(slideSec ?: WallpaperPlan.DEFAULT_INTERVAL_SEC), visible = slideSec != null),
            Row("dim", "Dim wallpaper", Kind.STEPPER, "${s.dimPercent} %"),
            Row("notificationsEnabled", "Notification chips on ambient", Kind.TOGGLE, onOff(s.notificationsEnabled),
                hint = if (s.notificationsEnabled) null else "Turn on notification chips first"),
            Row("notificationAccess", "Notification access", Kind.CARD,
                visible = s.notificationsEnabled && !accessGranted),
            Row("showNotificationText", "Show notification text", Kind.TOGGLE, onOff(s.showNotificationText),
                visible = s.notificationsEnabled,
                hint = "Only for notifications the app marks public; private ones always show the app name only."),
            Row("nightDim", "Dim more at night (23:00–06:00)", Kind.TOGGLE, onOff(s.nightDim)),
            Row("clock24h", "24-hour clock", Kind.TOGGLE, onOff(s.clock24h)),
            Row("clockPosition", "Clock position", Kind.CHOICE, when (s.clockPosition) {
                ClockPosition.BOTTOM_LEFT -> "Bottom left"
                ClockPosition.CENTER -> "Center"
                ClockPosition.TOP_LEFT -> "Top left"
            }),
            Row("idleMinutes", "Start ambient after", Kind.STEPPER, "${s.idleMinutes} min"),
            Row("pixelShift", "Burn-in protection (move content every minute)", Kind.TOGGLE, onOff(s.pixelShift))
        )
    }

    fun toggle(s: WallpaperSettings, key: String): WallpaperSettings = when (key) {
        "notificationsEnabled" -> s.copy(notificationsEnabled = !s.notificationsEnabled)
        "showNotificationText" -> s.copy(showNotificationText = !s.showNotificationText)
        "nightDim" -> s.copy(nightDim = !s.nightDim)
        "clock24h" -> s.copy(clock24h = !s.clock24h)
        "pixelShift" -> s.copy(pixelShift = !s.pixelShift)
        else -> s
    }

    fun step(s: WallpaperSettings, key: String, delta: Int): WallpaperSettings = when (key) {
        "dim" -> s.copy(dimPercent = (s.dimPercent + delta * 10).coerceIn(0, WallpaperSettings.MAX_DIM))
        "idleMinutes" -> s.copy(idleMinutes = stepIn(IDLE, s.idleMinutes, delta))
        "clockPosition" -> {
            val all = ClockPosition.values()
            s.copy(clockPosition = all[Math.floorMod(s.clockPosition.ordinal + delta, all.size)])
        }
        "slideshowInterval" -> when (val src = s.source) {
            is WallpaperSource.Slideshow -> s.copy(source = src.copy(intervalSec = stepIn(INTERVALS, src.intervalSec, delta)))
            is WallpaperSource.BuiltInSlideshow -> s.copy(source = src.copy(intervalSec = stepIn(INTERVALS, src.intervalSec, delta)))
            else -> s
        }
        else -> s
    }

    /** Interval of a slideshow source (pictures or built-ins), else null. */
    fun slideInterval(src: WallpaperSource): Int? = when (src) {
        is WallpaperSource.Slideshow -> src.intervalSec
        is WallpaperSource.BuiltInSlideshow -> src.intervalSec
        else -> null
    }

    private fun stepIn(steps: IntArray, current: Int, delta: Int): Int {
        val i = steps.indexOfFirst { it >= current }.let { if (it < 0) steps.size - 1 else it }
        return steps[(i + delta).coerceIn(0, steps.size - 1)]
    }
}
