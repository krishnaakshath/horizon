package dev.tvlauncher.ambient

import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.Trailing

/**
 * Pure rows under the wallpaper picker's thumbnail grid, in the shared panel row style. Values and
 * visibility follow [AmbientSettingsRows] so the picker and Settings > Ambient always agree.
 * Steppers show "‹ value ›": LEFT/RIGHT step, OK steps forward.
 */
object WallpaperPanelModel {
    const val SLIDESHOW = "slideshow"
    const val INTERVAL = "slideshowInterval"
    const val DIM = "dim"
    const val NOTIFICATIONS = "notificationsEnabled"
    const val ACCESS = "notificationAccess"
    const val TEXT = "showNotificationText"
    val STEPPERS = setOf(INTERVAL, DIM)

    fun stepper(value: String) = "‹  $value  ›"

    /**
     * [hasPictures]: the user's folder has at least one picture. [accessNote]: instructions shown
     * after the access row was used (null before). [folderPath]: where pictures go.
     */
    fun entries(
        s: WallpaperSettings,
        accessGranted: Boolean,
        hasPictures: Boolean,
        accessNote: String?,
        folderPath: String
    ): List<PanelEntry> {
        val rows = AmbientSettingsRows.rows(s, accessGranted).associateBy { it.key }
        val slideshowOn = s.source is WallpaperSource.Slideshow
        val e = ArrayList<PanelEntry>()
        if (!hasPictures) e += PanelEntry.Message("msg:pictures", "No pictures of your own yet. Built-in wallpapers are above.")
        e += PanelEntry.Row(SLIDESHOW, R.drawable.ic_set_tiles, "Slideshow of my pictures",
            trailing = Trailing.Switch(slideshowOn), enabled = hasPictures || slideshowOn)
        rows.getValue(INTERVAL).takeIf { it.visible }?.let {
            e += PanelEntry.Row(INTERVAL, R.drawable.ic_set_clock, "Change every", stepper(it.value))
        }
        e += PanelEntry.Row(DIM, R.drawable.ic_room_evening, "Dim", stepper(rows.getValue(DIM).value))
        e += PanelEntry.Row(NOTIFICATIONS, R.drawable.ic_set_status, "Notification chips",
            trailing = Trailing.Switch(s.notificationsEnabled))
        if (rows.getValue(ACCESS).visible) {
            e += PanelEntry.Row(ACCESS, R.drawable.ic_set_helper, "Notification access", "Not allowed", Trailing.Chevron)
            accessNote?.let { e += PanelEntry.Message("msg:access", it) }
        }
        rows.getValue(TEXT).takeIf { it.visible }?.let { r ->
            e += PanelEntry.Row(TEXT, R.drawable.ic_set_eye, "Show notification text", trailing = Trailing.Switch(s.showNotificationText))
            r.hint?.let { e += PanelEntry.Message("msg:text", it) }
        }
        e += PanelEntry.Message("msg:howto", "Add pictures in $folderPath",
            "From a computer: ${UserWallpapers.ADB_PUSH}\nJPG, PNG or WebP, up to 8 MB each, ${WallpaperPlan.MAX_FILES} max. Reopen after copying.")
        return e
    }
}
