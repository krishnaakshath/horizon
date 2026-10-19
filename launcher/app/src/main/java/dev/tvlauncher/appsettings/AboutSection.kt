package dev.tvlauncher.appsettings

import dev.tvlauncher.homelayout.HomeLayoutPrefs

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.brand.Brand
import dev.tvlauncher.settings.Branding
import dev.tvlauncher.settings.Prefs
import dev.tvlauncher.room.RoomPrefs
import dev.tvlauncher.settings.SettingsBackup
import java.io.File

/** Version, licenses, this TV's profile and the non-affiliation notice. */
object AboutSection : SettingsSection, SpecifiedPage {
    const val ID = "about"
    override val id = ID
    override val title = "About & licenses"
    override val iconRes = R.drawable.ic_set_about
    override val group = SettingsGroup.SYSTEM
    override val description = Branding.NAME
    override val spec = PageSpec(ID, listOf("about.version", "about.license", "about.notices", "about.tv"))

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        page.row("about.version", Brand.NAME, RowKind.Info(version(context)),
            subtitle = Brand.TAGLINE, iconRes = R.drawable.ic_set_about)
        page.row("about.license", "License", RowKind.Action(Branding.LICENSE) {
            nav.push("License") { c, n -> textPage(c, n, R.raw.license_mit) }
        }, iconRes = R.drawable.ic_set_license)
        page.row("about.notices", "Third-party notices", RowKind.Action {
            nav.push("Third-party notices") { c, n -> textPage(c, n, R.raw.third_party_notices) }
        }, subtitle = "AndroidX and Kotlin (Apache-2.0), Inter and Unbounded fonts (OFL)", iconRes = R.drawable.ic_set_license)
        page.row("about.tv", "This TV", RowKind.Action(AboutText.ram(nav.profile.totalRamKb)) {
            nav.push("This TV") { c, n -> thisTv(c, n) }
        }, subtitle = "Memory, processor, effects and resolution", iconRes = R.drawable.ic_sc_device)
        page.note("© ${Branding.COPYRIGHT_YEAR} ${Branding.COPYRIGHT_HOLDER}")
        page.note(Branding.DISCLAIMER)
        return page
    }

    private fun thisTv(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val profile = nav.profile
        page.row("about.ram", "Memory", RowKind.Info(AboutText.ram(profile.totalRamKb)), iconRes = R.drawable.ic_sc_device)
        page.row("about.abi", "Processor", RowKind.Info(profile.abi), iconRes = R.drawable.ic_sc_developer)
        page.row("about.effects", "Effects", RowKind.Info(AboutText.effects(profile.effectsMode)),
            subtitle = "Animations now: ${AboutText.motion(nav.style.motion)}", iconRes = R.drawable.ic_set_motion)
        page.row("about.resolution", "Resolution", RowKind.Info("${profile.widthPx} × ${profile.heightPx}"),
            iconRes = R.drawable.ic_sc_display)
        return page
    }

    internal fun textPage(context: Context, nav: SettingsNav, raw: Int): View {
        val page = TextPage(context, nav.style)
        nav.background({
            try {
                context.resources.openRawResource(raw).bufferedReader().use { it.readText() }
                    .replace(Branding.HOLDER_TOKEN, Branding.COPYRIGHT_HOLDER)
            } catch (e: Exception) {
                "Could not read this text."
            }
        }) { text -> page.setText(text) }
        return page
    }

    private fun version(context: Context): String = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        "${info.versionName} (${info.versionCode})"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }
}

/** Export and import of the home layout to a JSON file in the launcher's folder. */
object BackupSection : SettingsSection, SpecifiedPage {
    const val ID = "backup"
    override val id = ID
    override val title = "Backup & restore"
    override val iconRes = R.drawable.ic_set_backup
    override val group = SettingsGroup.SYSTEM
    override val description = "Favorites, order, hidden apps, home layout and room lighting, saved on this TV."
    override val spec = PageSpec(ID, listOf("backup.export", "backup.import"))

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val app = context.applicationContext
        val prefs = Prefs(app)
        page.row("backup.export", "Export settings", RowKind.Action(chevron = false) {
            nav.task({ backupFile(app)?.let { f -> SettingsBackup.export(prefs.load(), f, RoomPrefs(app).load(), HomeLayoutPrefs(app).load()) } }) { ok ->
                nav.notify(
                    when (ok) {
                        null -> "Storage is not available"
                        true -> "Saved ${SettingsBackup.FILE_NAME}"
                        false -> "Could not write the backup"
                    }
                )
            }
        }, subtitle = "Writes ${SettingsBackup.FILE_NAME} to the launcher's folder", iconRes = R.drawable.ic_set_export)
        page.row("backup.import", "Import settings", RowKind.Action(chevron = false) {
            // importOrNull only returns a fully validated file; nothing is saved otherwise.
            nav.task({
                backupFile(app)?.let { f ->
                    SettingsBackup.restoreOrNull(f)?.also { r ->
                        prefs.save(r.settings)
                        r.room?.let { RoomPrefs(app).save(it) }
                        r.layout?.let { HomeLayoutPrefs(app).save(it) }
                    }
                }
            }) { s ->
                nav.notify(if (s != null) "Settings restored" else "No valid ${SettingsBackup.FILE_NAME} found")
                if (s != null) nav.refreshCurrent()
            }
        }, subtitle = "Reads the same file; an invalid file changes nothing", iconRes = R.drawable.ic_set_import)
        return page
    }

    /** Android/data/<package>/files/launcher-settings.json; null without shared storage. Off main thread. */
    private fun backupFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, SettingsBackup.FILE_NAME) }
}
