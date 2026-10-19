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
    /** Asks for READ_EXTERNAL_STORAGE so Pictures/TVLauncher and USB collections can be listed. */
    const val PICTURES = "picturesAccess"
    val STEPPERS = setOf(INTERVAL, DIM)

    fun stepper(value: String) = "‹  $value  ›"

    /**
     * [hasPictures]: the owner has at least one picture collection. [accessNote]: instructions shown
     * after the access row was used (null before). [folderPath]: where collections go.
     * [picturesGranted]: the shared Pictures folder and USB sticks can be read.
     * The slideshow switch works for built-in collections too, so it is always enabled.
     */
    fun entries(
        s: WallpaperSettings,
        accessGranted: Boolean,
        hasPictures: Boolean,
        accessNote: String?,
        folderPath: String,
        picturesGranted: Boolean = true
    ): List<PanelEntry> {
        val rows = AmbientSettingsRows.rows(s, accessGranted).associateBy { it.key }
        val slideshowOn = AmbientSettingsRows.slideInterval(s.source) != null
        val e = ArrayList<PanelEntry>()
        if (!hasPictures) e += PanelEntry.Message("msg:pictures", "No pictures of your own yet. Built-in wallpapers are shown above.")
        e += PanelEntry.Row(SLIDESHOW, R.drawable.ic_set_tiles, "Slideshow", trailing = Trailing.Switch(slideshowOn))
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
        if (!picturesGranted) {
            e += PanelEntry.Row(PICTURES, R.drawable.ic_set_import, "Pictures folder and USB sticks", "Allow access", Trailing.Chevron)
        }
        e += PanelEntry.Message("msg:howto",
            "Add your own: each folder in $folderPath (or TVLauncher on a USB stick) is a collection, named after the folder.",
            "JPG, PNG or WebP up to 8 MB, ${WallpaperPlan.MAX_FILES} per folder.\nFrom a computer: ${UserWallpapers.ADB_PUSH_FOLDER}")
        return e
    }
}
