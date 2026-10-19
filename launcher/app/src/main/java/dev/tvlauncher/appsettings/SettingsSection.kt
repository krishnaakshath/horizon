package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import dev.tvlauncher.device.DeviceProfile

/**
 * One entry of the Settings sidebar. Other modules implement this and call
 * [SettingsRegistry.register]; a section whose [id] matches a built-in [Slot]
 * ("sounds", "insights", "helper", "browser", "devices") fills that slot ([SectionIds]).
 *
 * [group], [availability] and [description] are optional: a section written before groups
 * existed still compiles and is listed under [SettingsGroup.APPS] (or its well-known group).
 */
interface SettingsSection {
    /** Stable id; also the key used for focus restoration and slot replacement. */
    val id: String
    val title: String
    /** A 24dp vector drawable, tinted by the sidebar. */
    val iconRes: Int

    /** Sidebar group. Override to place the section; the default is [SettingsGroup.APPS] for new ids. */
    val group: SettingsGroup get() = SettingsGroup.defaultFor(id)

    /** When the section is listed in the sidebar (e.g. only while its companion app is installed). */
    val availability: SectionAvailability get() = SectionAvailability.Always

    /** One line under the page title (ellipsized); empty for none. */
    val description: String get() = ""

    /** Builds the content-pane page. Called on the main thread; heavy work goes through [SettingsNav.background]. */
    fun buildPage(context: Context, nav: SettingsNav): View
}

/** What a page can ask of the Settings screen hosting it. */
interface SettingsNav {
    /** Current accent and motion, already resolved against the device and system scale. */
    val style: SettingsStyle

    /** This TV's profile, read once by the screen (do not re-read it per page). */
    val profile: DeviceProfile

    /** Opens a sub-page inside the content pane; BACK returns to the row that opened it. */
    fun push(title: String, build: (Context, SettingsNav) -> View)

    /** Goes up one level. False when already at the section's root page. */
    fun pop(): Boolean

    /** Rebuilds the current page, keeping focus on the row with the same tag (or index). */
    fun refresh()

    /** Re-reads appearance settings (accent, animation level), re-tints and rebuilds. */
    fun appearanceChanged()

    /** A transient glass notice at the bottom of the content pane. */
    fun notify(message: String)

    /** Runs [work] off the main thread; [onResult] runs on the main thread only if the page is still shown. */
    fun <T> background(work: () -> T, onResult: (T) -> Unit)

    /** Rebuilds whatever page is shown now (independent of the page that started a task). */
    fun refreshCurrent()

    /**
     * Like [background], but [onResult] is delivered on the main thread even if the user has moved
     * to another page meanwhile (skipped only once the screen is destroyed). For work whose
     * outcome must always be reported, such as backup import/export.
     */
    fun <T> task(work: () -> T, onResult: (T) -> Unit)

    /** Call after a page filled itself asynchronously so pending focus can be restored. */
    fun contentReady()

    /** Whether the "Advanced" block [key] was left open on this screen (closed by default). */
    fun isAdvancedOpen(key: String): Boolean = false

    /** Remembers the "Advanced" block state for rebuilds of the same page while Settings is open. */
    fun setAdvancedOpen(key: String, open: Boolean) {}
}
