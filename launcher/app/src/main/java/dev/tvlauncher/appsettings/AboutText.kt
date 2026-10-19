package dev.tvlauncher.appsettings

import dev.tvlauncher.device.EffectsMode
import java.util.Locale

/** Pure strings for the About page. */
object AboutText {
    fun ram(totalKb: Long): String {
        if (totalKb <= 0) return "Unknown"
        val mb = totalKb / 1024
        return if (mb < 1024) "$mb MB" else String.format(Locale.US, "%.1f GB", totalKb / 1024.0 / 1024.0)
    }

    fun effects(device: EffectsMode): String = when (device) {
        EffectsMode.LITE -> "Lite (low-RAM TV)"
        EffectsMode.FULL -> "Full"
    }

    fun motion(m: EffectsMode?): String = when (m) {
        null -> "Off"
        EffectsMode.LITE -> "Lite"
        EffectsMode.FULL -> "Full"
    }
}

/** Whether an optional module's class is compiled into this build (no instantiation). */
object ModuleProbe {
    fun isPresent(className: String): Boolean = try {
        Class.forName(className, false, ModuleProbe::class.java.classLoader)
        true
    } catch (e: ClassNotFoundException) {
        false
    } catch (e: LinkageError) {
        false
    }

    /** True when an app with package name [pkg] is installed (declared in the manifest's queries). */
    fun isAppInstalled(context: android.content.Context, pkg: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }
}
