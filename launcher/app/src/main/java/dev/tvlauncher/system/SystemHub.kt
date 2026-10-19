package dev.tvlauncher.system

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.Settings
import android.view.View
import dev.tvlauncher.HomeActivity
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.AboutText
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRow
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.ClockFormat
import dev.tvlauncher.ui.SystemActions
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Settings > TV & system: the launcher's own pages for what the TV's settings app used to do
 * (About this TV, date and time, language, volume, storage and USB, power and sleep timer, live
 * TV), with HDMI-CEC, Reset launcher and "Managed by the TV" under Advanced.
 */
object SystemHubSection : SettingsSection, SpecifiedPage {
    override val id = SystemSpecs.SYSTEM
    override val title = "TV & system"
    override val iconRes = R.drawable.ic_sys_tv
    override val group = SettingsGroup.SYSTEM
    override val description = "About, time, language, volume, storage and power."
    override val spec = SystemSpecs.SYSTEM_HUB

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        page.row("sys.about", "About this TV", RowKind.Action(Build.MODEL) { nav.push("About this TV") { c, n -> about(c, n) } },
            iconRes = R.drawable.ic_sc_device)
        page.row("sys.datetime", "Date & time", RowKind.Action(clock(context)) { nav.push("Date & time") { c, n -> dateTime(c, n) } },
            iconRes = R.drawable.ic_sc_date)
        page.row("sys.language", "Language & keyboard", RowKind.Action(Locale.getDefault().displayLanguage) {
            nav.push("Language & keyboard") { c, n -> language(c, n) }
        }, iconRes = R.drawable.ic_sys_language)
        val vol = page.row("sys.volume", "Volume", RowKind.Action("…") { nav.push("Volume") { c, n -> volume(c, n) } },
            iconRes = R.drawable.ic_sys_volume)
        val st = page.row("sys.storage", "Storage & USB", RowKind.Action("…") { nav.push("Storage & USB") { c, n -> storage(c, n) } },
            iconRes = R.drawable.ic_sc_storage)
        val pw = page.row("sys.power", "Power & sleep", RowKind.Action(sleepValue(context)) { nav.push("Power & sleep") { c, n -> power(c, n) } },
            iconRes = R.drawable.ic_sys_power)
        page.row("sys.tv", "Live TV", RowKind.Action { nav.push("Live TV") { c, n -> liveTv(c, n) } },
            subtitle = "Channels, guide and inputs", iconRes = R.drawable.ic_sys_tv)
        page.advanced(nav, "system.advanced", "HDMI-CEC, reset launcher, managed by the TV") { adv ->
            adv.row("sys.cec", "HDMI-CEC", RowKind.Action { nav.push("HDMI-CEC") { c, n -> cec(c, n) } },
                subtitle = "Control of connected players", iconRes = R.drawable.ic_dev_hdmi)
            adv.row("sys.reset", "Reset launcher", RowKind.Action { nav.push("Reset launcher") { c, n -> reset(c, n) } },
                subtitle = "Back to how the launcher first looked", iconRes = R.drawable.ic_set_reset)
            adv.row("sys.managed", "Managed by the TV", RowKind.Action { nav.push("Managed by the TV") { c, n -> managed(c, n) } },
                subtitle = "Picture, resolution, updates and reset", iconRes = R.drawable.ic_settings)
        }
        nav.background({ Triple(volumeValue(context), storageValue(context), Unit) }) { (v, s, _) ->
            vol.setValue(v); st.setValue(s)
        }
        pw.setValue(sleepValue(context))
        // Deep links from the home screen (Spotlight Sound / Power).
        when (SystemLinks.consume(context)) {
            SystemLinks.VOLUME -> page.post { nav.push("Volume") { c, n -> volume(c, n) } }
            SystemLinks.POWER -> page.post { nav.push("Power & sleep") { c, n -> power(c, n) } }
        }
        return page
    }

    // ---- About this TV

    private fun about(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.ABOUT)
        val model = listOf(Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }, Build.MODEL)
            .distinct().joinToString(" ")
        page.row("about.model", "Model", RowKind.Info(model), iconRes = R.drawable.ic_sys_tv)
        page.row("about.android", "Android", RowKind.Info("Android ${Build.VERSION.RELEASE}"),
            subtitle = "Security update ${Build.VERSION.SECURITY_PATCH}", iconRes = R.drawable.ic_sc_device)
        val storage = page.row("about.storage", "Storage", RowKind.Info("…"), iconRes = R.drawable.ic_sc_storage)
        val ram = page.row("about.ram", "Memory", RowKind.Info(AboutText.ram(nav.profile.totalRamKb)), iconRes = R.drawable.ic_sc_device)
        val hz = safe(0f) { (context as? Activity)?.windowManager?.defaultDisplay?.refreshRate ?: 0f }
        page.row("about.display", "Screen", RowKind.Info("${nav.profile.widthPx} × ${nav.profile.heightPx}" +
            if (hz > 1f) " · ${hz.toInt()} Hz" else ""), iconRes = R.drawable.ic_sc_display)
        page.row("about.launcher", "Launcher", RowKind.Info(safe("") {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }), iconRes = R.drawable.ic_set_about)
        nav.background({
            val s = internal()
            val mi = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
            SystemText.usedOf(s.first - s.second, s.first) to "${SystemText.bytes(mi.availMem)} free"
        }) { (s, m) -> storage.setValue(s); ram.setSubtitle(m) }
        return page
    }

    // ---- Date & time

    private fun clock(context: Context) = safe("") { android.text.format.DateFormat.getTimeFormat(context).format(Date()) }

    private fun dateTime(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.DATETIME)
        val now = Date()
        page.row("dt.now", "Today", RowKind.Info(DateFormat.getDateInstance(DateFormat.FULL).format(now)),
            subtitle = clock(context), iconRes = R.drawable.ic_sc_date)
        val tz = TimeZone.getDefault()
        val offset = tz.getOffset(now.time) / 60_000
        page.row("dt.zone", "Time zone", RowKind.Info(tz.getDisplayName(tz.inDaylightTime(now), TimeZone.LONG)),
            subtitle = "GMT%s%02d:%02d".format(if (offset >= 0) "+" else "-", kotlin.math.abs(offset) / 60, kotlin.math.abs(offset) % 60),
            iconRes = R.drawable.ic_set_clock)
        val auto = safe(1) { Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 1) } == 1
        page.row("dt.auto", "Set automatically", RowKind.Info(if (auto) "On" else "Off"),
            subtitle = "From the network; only the TV can change this", iconRes = R.drawable.ic_set_calendar)
        val prefs = AppearancePrefs(context)
        val formats = listOf(ClockFormat.AUTO, ClockFormat.H24, ClockFormat.H12)
        page.row("dt.format", "Clock format", RowKind.Choice(listOf("Follow TV", "24-hour", "12-hour"),
            formats.indexOf(prefs.load().clockFormat).coerceAtLeast(0)) { i ->
            prefs.update { it.copy(clockFormat = formats[i]) }
            nav.appearanceChanged()
        }, subtitle = "The launcher's clock", iconRes = R.drawable.ic_set_clock)
        return page
    }

    // ---- Language & keyboard

    private fun language(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.LANGUAGE)
        val cur = Locale.getDefault()
        page.row("lang.current", "Language", RowKind.Info(cur.getDisplayName(cur).replaceFirstChar { it.titlecase(cur) }),
            iconRes = R.drawable.ic_sys_language)
        val all = LocaleList.getDefault().let { l -> (0 until l.size()).map { l[it].getDisplayName(cur) } }
        page.row("lang.list", "Preferred languages", RowKind.Info("${all.size}"), subtitle = all.take(3).joinToString(", "),
            iconRes = R.drawable.ic_sys_language)
        page.row("lang.keyboard", "Keyboard", RowKind.Info(keyboard(context)), iconRes = R.drawable.ic_set_tiles)
        page.note("The TV sets its language and keyboard during setup; the launcher follows them.")
        return page
    }

    private fun keyboard(context: Context): String = safe("Default") {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return@safe "Default"
        val pkg = id.substringBefore('/')
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }

    // ---- Volume

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun volumeValue(context: Context): String = safe("") {
        val a = audio(context) ?: return@safe ""
        if (a.isStreamMute(AudioManager.STREAM_MUSIC)) "Muted"
        else SystemText.volume(a.getStreamVolume(AudioManager.STREAM_MUSIC), a.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }

    private fun volume(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.VOLUME)
        val a = audio(context)
        if (a == null) { page.row("vol.level", "Volume", RowKind.Info("Not available"), iconRes = R.drawable.ic_sys_volume); return page }
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val level = a.getStreamVolume(AudioManager.STREAM_MUSIC)
        page.row("vol.level", "Volume", RowKind.Stepper(level, 0, max, { SystemText.volume(it, max) }, bar = true) { v ->
            safe(Unit) { a.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_PLAY_SOUND) }
        }, iconRes = R.drawable.ic_sys_volume)
        val muted = safe(false) { a.isStreamMute(AudioManager.STREAM_MUSIC) }
        page.row("vol.mute", "Mute", RowKind.Toggle(muted) { on ->
            safe(Unit) { a.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0) }
        }, iconRes = R.drawable.ic_sys_volume)
        page.row("vol.output", "Sound comes from", RowKind.Info(output(a)),
            subtitle = "Speaker, ARC and sound modes are set by the TV", iconRes = R.drawable.ic_set_sounds)
        if (a.isVolumeFixed) page.note("This TV keeps app volume fixed; use the remote's volume keys.")
        return page
    }

    private fun output(a: AudioManager): String = safe("TV speakers") {
        val types = a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        when {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types -> "Bluetooth audio"
            AudioDeviceInfo.TYPE_HDMI_ARC in types -> "HDMI ARC"
            AudioDeviceInfo.TYPE_USB_DEVICE in types || AudioDeviceInfo.TYPE_USB_HEADSET in types -> "USB audio"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES in types || AudioDeviceInfo.TYPE_WIRED_HEADSET in types -> "Headphones"
            else -> "TV speakers"
        }
    }

    // ---- Storage & USB

    private fun internal(): Pair<Long, Long> = safe(0L to 0L) {
        val s = StatFs(Environment.getDataDirectory().path)
        s.totalBytes to s.availableBytes
    }

    private fun storageValue(context: Context): String { val (t, f) = internal(); return if (t > 0) "${SystemText.bytes(f)} free" else "" }

    private fun usbVolumes(context: Context): List<StorageVolume> = safe(emptyList()) {
        (context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager)?.storageVolumes.orEmpty().filter { it.isRemovable }
    }

    private fun volumeDir(v: StorageVolume): File? = safe(null) {
        if (Build.VERSION.SDK_INT >= 30) v.directory
        else (v.javaClass.getMethod("getPathFile").invoke(v) as? File)
    }

    private fun storage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.STORAGE)
        val used = page.row("st.internal", "Internal storage", RowKind.Info("…"), iconRes = R.drawable.ic_sc_storage)
        val free = page.row("st.free", "Free", RowKind.Info("…"), iconRes = R.drawable.ic_sc_storage)
        val usb = usbVolumes(context)
        page.row("st.usb", "USB drives", RowKind.Action(if (usb.isEmpty()) "None" else "${usb.size}") {
            nav.push("USB drives") { c, n -> usbPage(c, n) }
        }, iconRes = R.drawable.ic_sys_usb)
        nav.background({ internal() }) { (t, f) ->
            used.setValue(SystemText.usedOf(t - f, t)); free.setValue(SystemText.bytes(f))
            used.setSubtitle("${SystemText.percent(t - f, t)} % used")
        }
        return page
    }

    private fun usbPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val vols = usbVolumes(context)
        if (vols.isEmpty()) {
            page.row("usb.none", "No USB drive", RowKind.Info(), subtitle = "Plug one in; it shows up here", iconRes = R.drawable.ic_sys_usb)
            return page
        }
        for ((i, v) in vols.withIndex()) {
            val dir = volumeDir(v)
            val name = safe("USB drive") { v.getDescription(context) }
            val state = safe("") { v.state }
            val row = page.row("usb.$i", name, RowKind.Action("…") {
                nav.push(name) { c, n -> usbBrowse(c, n, v, name) }
            }, subtitle = if (state == Environment.MEDIA_MOUNTED) "Ready" else "Not ready", iconRes = R.drawable.ic_sys_usb)
            nav.background({ dir?.let { d -> safe(0L to 0L) { StatFs(d.path).let { it.totalBytes to it.availableBytes } } } }) { p ->
                row.setValue(p?.takeIf { it.first > 0 }?.let { "${SystemText.bytes(it.second)} free" } ?: "")
            }
        }
        return page
    }

    private fun usbBrowse(context: Context, nav: SettingsNav, v: StorageVolume, name: String): View {
        val page = SettingsPage(context, nav.style)
        val dir = volumeDir(v)
        page.row("usb.eject", "Safely remove", RowKind.Action(chevron = false) {
            if (!eject(context, v)) nav.notify("Unplug $name when nothing is playing from it.")
        }, iconRes = R.drawable.ic_net_close)
        nav.background({ safe(emptyList<File>()) { dir?.listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() }.orEmpty() } }) { files ->
            if (files.isEmpty()) page.note(if (dir == null) "This drive can't be read here." else "Nothing to show on this drive.")
            for ((i, f) in files.take(MAX_FILES).withIndex()) {
                page.row("usb.f.$i", f.name, RowKind.Info(if (f.isDirectory) "Folder" else SystemText.bytes(f.length())),
                    iconRes = if (f.isDirectory) R.drawable.ic_sc_storage else R.drawable.ic_sc_apps)
            }
            if (files.size > MAX_FILES) page.note("And ${files.size - MAX_FILES} more. Open the drive in a media app to play them.")
            nav.contentReady()
        }
        return page
    }

    /** The TV's small "remove drive" confirmation (a dialog, not the settings app); false when the TV lacks it. */
    private fun eject(context: Context, v: StorageVolume): Boolean {
        val id = safe<String?>(null) { v.javaClass.getMethod("getId").invoke(v) as? String } ?: return false
        val i = Intent("com.android.tv.settings.action.UNMOUNT_STORAGE").setPackage(TvScreens.TV_SETTINGS)
            .putExtra("android.os.storage.extra.VOLUME_ID", id)
        if (context.packageManager.resolveActivity(i, 0) == null) return false
        return SystemActions.start(context, i)
    }

    // ---- Power & sleep

    private fun sleepValue(context: Context): String {
        val left = PowerActions.sleepMinutesLeft(context)
        return if (left > 0) "Sleep in ${SleepTimer.label(left)}" else ""
    }

    private fun power(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.POWER)
        val ready = PowerActions.helperEnabled(context)
        page.row("pw.off", "Turn off the TV", RowKind.Action(if (ready) null else "Remote", chevron = false) {
            if (!PowerActions.powerMenu(context)) nav.notify(
                if (PowerActions.helperInstalled(context)) "Switch the Helper on in Accessibility, or use the remote's power button."
                else "Use the remote's power button. The Helper add-on adds this here."
            )
        }, subtitle = "Opens the TV's power menu", iconRes = R.drawable.ic_sys_power)
        val prefs = SystemPrefs(context)
        val minutes = if (PowerActions.sleepMinutesLeft(context) > 0) prefs.sleepMinutes else 0
        val idx = SleepTimer.CHOICES.indexOf(minutes).coerceAtLeast(0)
        page.row("pw.timer", "Sleep timer", RowKind.Choice(SleepTimer.CHOICES.map { SleepTimer.label(it) }, idx) { i ->
            val m = SleepTimer.CHOICES[i]
            PowerActions.setSleepTimer(context, m)
            nav.notify(if (m == 0) "Sleep timer off" else "The TV's power menu opens in ${SleepTimer.label(m)}")
        }, subtitle = "At the end the power menu opens (or the screen dims)", iconRes = R.drawable.ic_set_clock)
        page.row("pw.screensaver", "Screensaver now", RowKind.Action(chevron = false) {
            if (!SystemActions.ambient(context)) nav.notify("The screensaver could not start")
        }, iconRes = R.drawable.ic_qa_ambient)
        val dream = "${context.packageName}/dev.tvlauncher.ambient.AmbientDream"
        val ours = safe(false) { Settings.Secure.getString(context.contentResolver, "screensaver_components")?.contains(dream) == true }
        if (SystemPermissions.canWriteSecure(context)) {
            page.row("pw.dream", "Launcher as screensaver", RowKind.Toggle(ours) { on ->
                val ok = safe(false) {
                    val cr = context.contentResolver
                    if (on) {
                        Settings.Secure.putString(cr, "screensaver_components", dream)
                        Settings.Secure.putInt(cr, "screensaver_enabled", 1)
                    } else {
                        Settings.Secure.putString(cr, "screensaver_components", "")
                    }
                    true
                }
                if (!ok) nav.notify("The screensaver could not be changed")
            }, subtitle = "When the TV sits idle", iconRes = R.drawable.ic_qa_ambient)
        } else {
            page.row("pw.dream", "Launcher as screensaver", RowKind.Info(if (ours) "On" else "Off"),
                subtitle = "When the TV sits idle", iconRes = R.drawable.ic_qa_ambient)
        }
        return page
    }

    // ---- Live TV

    private class TvTarget(val key: String, val title: String, val action: String?, val icon: Int)

    private val TV_TARGETS = listOf(
        TvTarget("tv.live", "Watch live TV", "tcl.sys.intent.action.TV", R.drawable.ic_sys_tv),
        TvTarget("tv.guide", "TV guide", "com.tcl.tv.action.EPG", R.drawable.ic_set_calendar),
        TvTarget("tv.channels", "Channels", "com.tcl.tv.action.CHANNEL_MANAGER", R.drawable.ic_dev_antenna)
    )

    private fun liveTv(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.TV)
        val pm = context.packageManager
        for (t in TV_TARGETS) {
            val i = t.action?.let { Intent(it) }?.takeIf { safe(false) { pm.resolveActivity(it, 0) != null } }
            if (i == null) page.row(t.key, t.title, RowKind.Info("Not on this TV"), iconRes = t.icon)
            else page.row(t.key, t.title, RowKind.Action { if (!SystemActions.start(context, i)) nav.notify("${t.title} could not be opened") },
                iconRes = t.icon)
        }
        page.row("tv.inputs", "Inputs", RowKind.Action { nav.push("Devices") { c, n -> DevicesSettingsSection.buildPage(c, n) } },
            subtitle = "HDMI and AV ports", iconRes = R.drawable.ic_input)
        return page
    }

    // ---- HDMI-CEC

    private fun cec(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.CEC)
        val on = safe(-1) { Settings.Global.getInt(context.contentResolver, "hdmi_control_enabled", -1) }
        page.row("cec.control", "HDMI-CEC", RowKind.Info(when (on) { 1 -> "On"; 0 -> "Off"; else -> "Unknown" }),
            subtitle = "Lets the remote control players; switched in the TV", iconRes = R.drawable.ic_dev_hdmi)
        val names = InputReader.read(context).filter { it.parentId != null }.map { it.label.trim() }.filter { it.isNotEmpty() }.distinct()
        page.row("cec.devices", "Devices found", RowKind.Info(if (names.isEmpty()) "None" else "${names.size}"),
            subtitle = names.take(3).joinToString(", ").ifEmpty { null }, iconRes = R.drawable.ic_set_devices)
        return page
    }

    // ---- Reset launcher

    private fun reset(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val gate = InsistGate()
        page.note("Clears favourites, order, hidden apps, looks, sounds and room lighting. Apps and the TV's own settings stay as they are.")
        lateinit var row: SettingsRow
        row = page.row("reset.go", "Reset launcher", RowKind.Action(chevron = false) {
            if (!gate.press("reset", SystemClock.elapsedRealtime())) { row.setValue("Press OK again"); return@Action }
            resetLauncher(context)
        }, iconRes = R.drawable.ic_set_reset)
        return page
    }

    /** Clears every launcher preference file except the crash guard, then restarts the home screen. */
    fun resetLauncher(context: Context) {
        val app = context.applicationContext
        val dir = File(app.applicationInfo.dataDir, "shared_prefs")
        safe(Unit) {
            dir.listFiles()?.map { it.name.removeSuffix(".xml") }?.filter { it != "crash_guard" }?.forEach {
                app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
        safe(Unit) {
            app.startActivity(Intent(app, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        (context as? Activity)?.finishAffinity()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 400)
    }

    // ---- Managed by the TV

    private val MANAGED_KEYS = mapOf(
        TvScreens.Screen.PICTURE to "tv.picture", TvScreens.Screen.DISPLAY to "tv.display",
        TvScreens.Screen.ACCESSIBILITY to "tv.access", TvScreens.Screen.UPDATE to "tv.update",
        TvScreens.Screen.RESET to "tv.reset", TvScreens.Screen.ALL to "tv.all"
    )

    private fun managed(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.MANAGED)
        val gate = InsistGate()
        for ((screen, key) in MANAGED_KEYS) {
            lateinit var row: SettingsRow
            row = page.row(key, screen.title, RowKind.Action("Managed by the TV", chevron = false) {
                if (!gate.press(key, SystemClock.elapsedRealtime())) { row.setValue("Press OK again to open"); return@Action }
                row.setValue("Managed by the TV")
                if (!TvScreens.open(context, screen)) nav.notify("This TV has no such screen")
            }, subtitle = screen.why, iconRes = R.drawable.ic_settings)
        }
        return page
    }

    private const val MAX_FILES = 40
}
