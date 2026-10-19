package dev.tvlauncher.setup

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import dev.tvlauncher.ambient.NotificationAccess
import dev.tvlauncher.ambient.UserWallpapers
import dev.tvlauncher.ambient.WeatherCompanion
import dev.tvlauncher.ambient.WeatherContract
import dev.tvlauncher.appsettings.AddOns
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomPrefs

/** Reads the real state of this TV for [SetupPlan] and [SetupAuto]. Every probe is guarded. */
object SetupProber {
    private inline fun <T> safe(default: T, f: () -> T): T = try { f() } catch (e: Throwable) { default }

    fun installed(context: Context, pkg: String): Boolean = safe(false) {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    }

    fun resolves(context: Context, intent: Intent): Boolean = safe(false) {
        context.packageManager.resolveActivity(intent, 0) != null
    }

    fun secure(context: Context, key: String): String? = safe(null) { Settings.Secure.getString(context.contentResolver, key) }

    fun granted(context: Context, permission: String): Boolean = safe(false) {
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun canWriteSecure(context: Context): Boolean = granted(context, Manifest.permission.WRITE_SECURE_SETTINGS)

    fun isHome(context: Context): Boolean = safe(false) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return@safe rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
    }

    /** The first home-app chooser this TV has: the role request, then the Home and Default apps screens. */
    fun homeChooser(context: Context): Intent? = safe(null) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                val i = rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
                if (resolves(context, i)) return@safe i
            }
        }
        listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .map { Intent(it) }.firstOrNull { resolves(context, it) }
    }

    fun listenerGranted(context: Context): Boolean = NotificationAccess.isEnabled(context) || safe(false) {
        Build.VERSION.SDK_INT >= 27 && context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(NotificationAccess.component(context)) == true
    }

    fun listenerScreen(context: Context): Boolean =
        resolves(context, NotificationAccess.settingsIntent()) || resolves(context, Intent(NotificationAccess.SHORT_ACTION))

    fun helperEnabled(context: Context): Boolean =
        SecureList.contains(secure(context, SetupAuto.A11Y_SERVICES), SetupAuto.HELPER_COMPONENT) &&
            secure(context, SetupAuto.A11Y_ENABLED)?.trim() == "1"

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Asked before, still denied, and Android no longer shows the dialog. */
    fun blocked(activity: Activity?, store: SetupStore, permission: String): Boolean =
        activity != null && store.asked(permission) && !granted(activity, permission) &&
            !safe(true) { activity.shouldShowRequestPermissionRationale(permission) }

    fun probe(context: Context, store: SetupStore = SetupStore(context)): SetupProbe {
        val activity = context as? Activity
        val missing = AddOns.all.filter { !installed(context, it.packageName) }.map { it.title }
        val room = safe(null) { RoomPrefs(context).load() }
        return SetupProbe(
            isHome = isHome(context),
            homeChooser = homeChooser(context) != null,
            listenerGranted = listenerGranted(context),
            listenerScreen = listenerScreen(context),
            canWriteSecure = canWriteSecure(context),
            helperInstalled = installed(context, HELPER_PACKAGE),
            helperEnabled = helperEnabled(context),
            accessibilityScreen = resolves(context, accessibilityIntent()),
            locationGranted = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
            locationBlocked = blocked(activity, store, Manifest.permission.ACCESS_FINE_LOCATION),
            picturesGranted = UserWallpapers.canReadShared(context),
            picturesBlocked = blocked(activity, store, UserWallpapers.PERMISSION),
            weatherInstalled = installed(context, WeatherContract.PACKAGE),
            weatherScreen = safe(false) { WeatherCompanion.settingsIntent(context) != null },
            getAppsInstalled = installed(context, AddOns.STORE_PACKAGE),
            missingCompanions = missing,
            usageGranted = UsageAccess.hasAccess(context),
            usageScreen = safe(false) { UsageAccess.settingsIntent(context) != null },
            roomAutomatic = room?.choice?.let { it == RoomChoice.AUTO } ?: true,
            lite = safe(false) { DeviceProfileReader.read(context).effectsMode == EffectsMode.LITE }
        )
    }

    fun autoInputs(context: Context, store: SetupStore = SetupStore(context)) = AutoInputs(
        canWriteSecure = canWriteSecure(context),
        listenerGranted = listenerGranted(context),
        listenerFlat = secure(context, SetupAuto.LISTENERS),
        helperInstalled = installed(context, HELPER_PACKAGE),
        a11yFlat = secure(context, SetupAuto.A11Y_SERVICES),
        a11yEnabled = secure(context, SetupAuto.A11Y_ENABLED),
        tvUserSetupComplete = secure(context, SetupAuto.TV_SETUP_COMPLETE),
        nowPlayingAutoDone = store.nowPlayingAutoDone,
        roomConfigured = safe(true) {
            context.getSharedPreferences(RoomPrefs.NAME, Context.MODE_PRIVATE).contains(RoomPrefs.KEY)
        },
        zoneId = safe("") { java.util.TimeZone.getDefault().id }
    )

    const val HELPER_PACKAGE = "dev.tvlauncher.helper"
}
