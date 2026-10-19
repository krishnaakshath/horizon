package dev.tvlauncher.system

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.settings.Prefs
import dev.tvlauncher.ui.SystemActions
import java.io.File

/** One installed app as the Apps page lists it (plain values; JVM-testable ordering). */
data class AppRow(val pkg: String, val label: String, val system: Boolean, val launchable: Boolean)

object AppsModel {
    /** User apps first, then built-in apps that can be opened, each alphabetical; [hidden] dropped while a PIN is set. */
    fun order(apps: List<AppRow>, hidden: Set<String> = emptySet()): List<AppRow> =
        apps.filter { it.pkg !in hidden && (it.launchable || !it.system) }
            .sortedWith(compareBy<AppRow>({ it.system }, { it.label.lowercase() }, { it.pkg }))

    /** Friendly words for the dangerous permissions an app was granted. */
    fun permissionWords(granted: List<String>): List<String> = granted.mapNotNull {
        when (it.substringAfterLast('.')) {
            "CAMERA" -> "Camera"
            "RECORD_AUDIO" -> "Microphone"
            "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" -> "Location"
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE" -> "Storage"
            "READ_CONTACTS" -> "Contacts"
            "READ_PHONE_STATE" -> "Phone"
            else -> null
        }
    }.distinct()
}

/**
 * Settings > Apps: every app with an app page in the launcher (open, version, size, permissions in
 * use, stop background activity, uninstall user apps through the system's confirm dialog).
 * Force stop and clearing data need system access and are not offered.
 */
object AppsSection : SettingsSection {
    override val id = SystemSpecs.APPS
    override val title = "Apps"
    override val iconRes = R.drawable.ic_sys_apps
    override val group = SettingsGroup.APPS
    override val description = "Open, inspect and uninstall apps."

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val deep = SystemLinks.appOf(SystemLinks.consume(context))
        nav.background({ load(context) }) { apps ->
            if (apps.isEmpty()) page.note("No apps found.")
            var lastSystem: Boolean? = null
            for (a in apps) {
                if (lastSystem != a.system) { page.group(if (a.system) "Built into the TV" else "Installed"); lastSystem = a.system }
                page.body.row("apps.app." + a.pkg, a.label, RowKind.Action { nav.push(a.label) { c, n -> appPage(c, n, a.pkg) } },
                    iconRes = R.drawable.ic_sys_apps)
            }
            nav.contentReady()
        }
        if (deep != null) page.post { nav.push(label(context, deep)) { c, n -> appPage(c, n, deep) } }
        return page
    }

    private fun load(context: Context): List<AppRow> = safe(emptyList()) {
        val pm = context.packageManager
        val hidden = if (PinLock.isSet(context)) Prefs(context.applicationContext).load().hidden else emptySet()
        @Suppress("DEPRECATION")
        val list = pm.getInstalledApplications(0).filter { it.packageName != context.packageName }.map { ai ->
            val launch = pm.getLeanbackLaunchIntentForPackage(ai.packageName) ?: pm.getLaunchIntentForPackage(ai.packageName)
            AppRow(ai.packageName, pm.getApplicationLabel(ai).toString(), isSystem(ai), launch != null)
        }
        AppsModel.order(list, hidden)
    }

    private fun isSystem(ai: ApplicationInfo) =
        ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun label(context: Context, pkg: String) = safe(pkg) {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }

    fun appPage(context: Context, nav: SettingsNav, pkg: String): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.APP_INFO)
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info: PackageInfo? = safe(null) { pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS) }
        val ai = info?.applicationInfo
        if (info == null || ai == null) {
            page.row("app.open", "Not installed", RowKind.Info(), iconRes = R.drawable.ic_sys_apps)
            return page
        }
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (launch != null) page.row("app.open", "Open", RowKind.Action(chevron = false) {
            if (!SystemActions.start(context, launch)) nav.notify("Couldn't open it")
        }, iconRes = R.drawable.ic_set_check)
        else page.row("app.open", "Open", RowKind.Info("No screen of its own"), iconRes = R.drawable.ic_set_check)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        page.row("app.version", "Version", RowKind.Info(info.versionName ?: code.toString()), subtitle = pkg, iconRes = R.drawable.ic_set_about)
        val size = page.row("app.size", "Size", RowKind.Info("…"), iconRes = R.drawable.ic_sc_storage)
        val granted = info.requestedPermissions.orEmpty().filterIndexed { i, _ ->
            (info.requestedPermissionsFlags?.getOrNull(i) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        }
        val words = AppsModel.permissionWords(granted)
        page.row("app.perms", "Allowed", RowKind.Info(if (words.isEmpty()) "Nothing sensitive" else words.joinToString(", ")),
            iconRes = R.drawable.ic_sys_lock)
        page.row("app.stop", "Stop background activity", RowKind.Action(chevron = false) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            nav.notify(if (safe(false) { am?.killBackgroundProcesses(pkg); true }) "Stopped what it was doing in the background"
            else "This TV didn't let the launcher stop it")
        }, subtitle = "Full force stop is kept by the TV", iconRes = R.drawable.ic_net_close)
        if (!isSystem(ai)) page.row("app.uninstall", "Uninstall", RowKind.Action(chevron = false) {
            if (!SystemActions.start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))) nav.notify("Couldn't start uninstalling")
        }, iconRes = R.drawable.ic_set_reset)
        else page.row("app.uninstall", "Uninstall", RowKind.Info("Built into the TV"), iconRes = R.drawable.ic_set_reset)
        nav.background({ sizeOf(context, ai) }) { b -> size.setValue(if (b > 0) SystemText.bytes(b) else "Unknown") }
        return page
    }

    /** Code + data + cache when usage access is granted, otherwise the installed package files. */
    private fun sizeOf(context: Context, ai: ApplicationInfo): Long {
        if (Build.VERSION.SDK_INT >= 26) {
            val s = safe(-1L) {
                val ssm = context.getSystemService(StorageStatsManager::class.java)
                val st = ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, Process.myUserHandle())
                st.appBytes + st.dataBytes + st.cacheBytes
            }
            if (s > 0) return s
        }
        return safe(0L) { (listOf(ai.sourceDir) + ai.splitSourceDirs.orEmpty()).sumOf { File(it).length() } }
    }
}
