package dev.tvlauncher.appsettings

import dev.tvlauncher.ambient.WeatherContract
import dev.tvlauncher.ui.QuickActions

/** An optional companion app, listed on the Add-ons page. [sectionId] is its own Settings page, if any. */
data class AddOn(val id: String, val title: String, val adds: String, val packageName: String, val sectionId: String?)

object AddOns {
    const val STORE_PACKAGE = "dev.tvlauncher.store"

    val all = listOf(
        AddOn("browser", "Browser", "A simple web browser built for the remote", QuickActions.BROWSER_PACKAGE, ModulePlaceholderSection.BROWSER),
        AddOn("getapps", "Get Apps", "Install apps from F-Droid, GitHub or a link", STORE_PACKAGE, GetAppsSection.ID),
        AddOn("weather", "Weather", "Local weather for the home screen and ambient", WeatherContract.PACKAGE, null),
        AddOn("helper", "Helper", "Remote shortcuts such as double-Back to Home", ModuleSections.HELPER_PACKAGE, ModulePlaceholderSection.HELPER)
    )

    fun byId(id: String): AddOn? = all.firstOrNull { it.id == id }

    fun chip(installed: Boolean) = if (installed) "Installed ✓" else "Not installed"

    fun primaryAction(installed: Boolean) = if (installed) "Open" else "How to install"

    fun howToInstall(a: AddOn): String =
        "${a.title} is a separate app (${a.packageName}).\n\n" +
            "1. Copy its APK to a USB drive, or keep it on a computer on the same network.\n" +
            "2. On the TV, open the APK with a file manager and allow the install when asked,\n" +
            "   or from the computer run:  adb install <file>.apk\n" +
            "3. Come back here: the ${a.title} page appears under Apps & tools."
}
