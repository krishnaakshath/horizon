package dev.tvlauncher.setup

import android.content.Context
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SectionIds
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.room.RoomPrefs
import dev.tvlauncher.settings.Prefs
import dev.tvlauncher.settings.SettingsBackup
import java.io.File

/**
 * Settings > System > Backup & restore of the whole configuration (fills the built-in "backup"
 * slot). Saves to a USB stick when one is plugged in, so a second TV can restore it,
 * also from the first-run setup.
 */
object ConfigBackupSection : SettingsSection, SpecifiedPage {
    override val id = SectionIds.BACKUP
    override val title = "Backup & restore"
    override val iconRes = R.drawable.ic_set_backup
    override val group = SettingsGroup.SYSTEM
    override val description = "Your whole setup in one file, on a USB stick or this TV."
    override val spec = PageSpec(id, listOf("backup.export", "backup.import"))

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val app = context.applicationContext
        page.row("backup.export", "Save my setup", RowKind.Action(chevron = false) {
            nav.task({ ConfigBackupIo.export(app) }) { f ->
                nav.notify(if (f != null) "Saved ${ConfigBackup.FILE_NAME}" else "Could not save the backup")
            }
        }, subtitle = "Apps, look, lighting, wallpaper and sounds. Goes to a USB stick when one is plugged in",
            iconRes = R.drawable.ic_set_export)
        page.row("backup.import", "Restore from USB", RowKind.Action(chevron = false) {
            nav.task({ restoreAny(app) }) { ok ->
                nav.notify(if (ok) "Setup restored" else "No valid backup found")
                if (ok) nav.refreshCurrent()
            }
        }, subtitle = "Looks on USB sticks and on this TV; an invalid file changes nothing", iconRes = R.drawable.ic_set_import)
        return page
    }

    /** The newest full backup, else the older launcher-only file. Off the main thread. */
    fun restoreAny(context: Context): Boolean {
        ConfigBackupIo.find(context)?.let { if (ConfigBackupIo.restore(context, it)) return true }
        val legacy = context.getExternalFilesDir(null)?.let { File(it, SettingsBackup.FILE_NAME) } ?: return false
        val r = SettingsBackup.restoreOrNull(legacy) ?: return false
        Prefs(context).save(r.settings)
        r.room?.let { RoomPrefs(context).save(it) }
        r.layout?.let { dev.tvlauncher.homelayout.HomeLayoutPrefs(context).save(it) }
        r.motion?.let { dev.tvlauncher.splash.MotionStylePrefs(context).save(it) }
        return true
    }
}
