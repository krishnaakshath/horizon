package dev.tvlauncher.appsettings

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.settings.Branding
import dev.tvlauncher.settings.Prefs
import dev.tvlauncher.settings.SettingsBackup
import java.io.File

/** Version, this TV's profile, backup & restore, licenses and the non-affiliation notice. */
object AboutSection : SettingsSection {
    const val ID = "about"
    override val id = ID
    override val title = "About & Licenses"
    override val iconRes = R.drawable.ic_set_about

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        page.group(Branding.NAME)
        page.row("about.version", "Version", RowKind.Info(version(context)), iconRes = R.drawable.ic_set_about)
        page.row("about.license", "License", RowKind.Action(Branding.LICENSE) {
            nav.push("License") { c, n -> textPage(c, n, R.raw.license_mit) }
        }, iconRes = R.drawable.ic_set_license)
        page.row("about.notices", "Third-party notices", RowKind.Action {
            nav.push("Third-party notices") { c, n -> textPage(c, n, R.raw.third_party_notices) }
        }, subtitle = "AndroidX RecyclerView and Kotlin, Apache-2.0", iconRes = R.drawable.ic_set_license)

        val profile = DeviceProfileReader.read(context)
        page.group("This TV")
        page.row("about.ram", "Memory", RowKind.Info(AboutText.ram(profile.totalRamKb)), iconRes = R.drawable.ic_sc_device)
        page.row("about.abi", "Processor", RowKind.Info(profile.abi), iconRes = R.drawable.ic_sc_developer)
        page.row("about.effects", "Effects", RowKind.Info(AboutText.effects(profile.effectsMode)),
            subtitle = "Animations now: ${AboutText.motion(nav.style.motion)}", iconRes = R.drawable.ic_set_motion)
        page.row("about.resolution", "Resolution", RowKind.Info("${profile.widthPx} × ${profile.heightPx}"),
            iconRes = R.drawable.ic_sc_display)

        page.group("Backup & restore")
        val app = context.applicationContext
        val prefs = Prefs(app)
        page.row("about.export", "Export settings", RowKind.Action(chevron = false) {
            nav.background({ backupFile(app)?.let { f -> SettingsBackup.export(prefs.load(), f) } }) { ok ->
                nav.notify(
                    when (ok) {
                        null -> "Storage is not available"
                        true -> "Saved ${SettingsBackup.FILE_NAME}"
                        false -> "Could not write the backup"
                    }
                )
            }
        }, subtitle = "Favorites, order and hidden apps to ${SettingsBackup.FILE_NAME} in the launcher's folder",
            iconRes = R.drawable.ic_set_export)
        page.row("about.import", "Import settings", RowKind.Action(chevron = false) {
            nav.background({ backupFile(app)?.let { f -> SettingsBackup.importOrNull(f)?.also { prefs.save(it) } } }) { s ->
                nav.notify(if (s != null) "Settings restored" else "No valid ${SettingsBackup.FILE_NAME} found")
                if (s != null) nav.refresh()
            }
        }, subtitle = "Reads the same file; an invalid file changes nothing", iconRes = R.drawable.ic_set_import)

        page.note(Branding.DISCLAIMER)
        return page
    }

    /** Android/data/<package>/files/launcher-settings.json; null without shared storage. Off main thread. */
    private fun backupFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, SettingsBackup.FILE_NAME) }

    private fun version(context: Context): String = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        "${info.versionName} (${info.versionCode})"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }

    private fun textPage(context: Context, nav: SettingsNav, raw: Int): View {
        val page = TextPage(context, nav.style)
        nav.background({
            try {
                context.resources.openRawResource(raw).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "Could not read this text."
            }
        }) { text -> page.setText(text) }
        return page
    }
}
