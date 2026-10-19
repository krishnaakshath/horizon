package dev.tvlauncher.profiles

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.tvlauncher.system.PinRules
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The one place that edits profiles on disk and switches between them (rules in [ProfileModel],
 * file names in [ProfileStores]). Every call is guarded; a storage failure leaves the current
 * profile in use.
 *
 * Switching is a single path: [switchTo] saves the new active id, points [ProfileStores.route]
 * at the new files, runs [reloadProcessState] for the process-wide caches, bumps [generation] and
 * tells the [Listener]s. The home screen listens (and also compares [generation] in onStart) and
 * rebuilds itself, so HomeLook, Prefs, HomeRoom, HomeSounds, the layout and the shelf are all
 * re-created from the new files.
 */
object ProfileManager {
    private const val KEY = "json"
    private val main = Handler(Looper.getMainLooper())
    private val gen = AtomicInteger()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private var askedThisProcess = false

    // PIN lockout across tries (same rule as the parental PIN).
    private var misses = 0
    private var lockedUntil = 0L

    fun interface Listener {
        /** Main thread, after the active profile changed. */
        fun onProfileSwitched(profile: Profile)
    }

    /** Bumped on every switch; a screen built at an older value must rebuild. */
    val generation: Int get() = gen.get()

    fun addListener(l: Listener) { listeners.addIfAbsent(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    /** Process start (LauncherApp, first thing): route to the saved profile. */
    fun install(context: Context) {
        ProfileStores.activeId = try { load(context).activeId } catch (e: Throwable) { ProfileModel.HOME_ID }
    }

    fun load(context: Context): ProfileState = try {
        ProfileModel.fromJson(ProfileStores.raw(context, ProfileStores.REGISTRY).getString(KEY, null))
    } catch (e: Exception) {
        ProfileModel.initial()
    }

    private fun save(context: Context, state: ProfileState): Boolean = try {
        ProfileStores.raw(context, ProfileStores.REGISTRY).edit().putString(KEY, ProfileModel.toJson(state)).commit()
    } catch (e: Exception) {
        false
    }

    fun active(context: Context): Profile = load(context).active

    /**
     * Adds a profile named [name] whose settings start as a copy of the profile in use (favorites,
     * look, wallpaper, layout, lighting): a new person begins from something familiar.
     */
    fun create(context: Context, name: String): ProfileModel.Result {
        val state = load(context)
        val id = ProfileModel.newId(state) { (Math.random() * Int.MAX_VALUE).toInt() }
        val r = ProfileModel.create(state, name, id)
        if (r !is ProfileModel.Result.Ok) return r
        for (store in ProfileStores.SCOPED) {
            ProfileStores.copy(
                ProfileStores.raw(context, ProfileStores.physical(store, state.activeId)),
                ProfileStores.raw(context, ProfileStores.physical(store, id))
            )
        }
        return if (save(context, r.state)) r else ProfileModel.Result.Error("Could not add the profile.")
    }

    fun rename(context: Context, id: String, name: String) = edit(context) { ProfileModel.rename(it, id, name) }
    fun recolor(context: Context, id: String, color: Int) = edit(context) { ProfileModel.recolor(it, id, color) }
    fun setPin(context: Context, id: String, pin: String) =
        edit(context) { ProfileModel.setPin(it, id, pin, java.util.UUID.randomUUID().toString()) }
    fun clearPin(context: Context, id: String) = edit(context) { ProfileModel.clearPin(it, id) }

    fun setAskOnStart(context: Context, on: Boolean) =
        edit(context) { ProfileModel.Result.Ok(it.copy(askOnStart = on)) }

    /** Removes [id] and its files; when it was in use, the first remaining profile takes over. */
    fun delete(context: Context, id: String): ProfileModel.Result {
        val before = load(context)
        val r = ProfileModel.delete(before, id)
        if (r !is ProfileModel.Result.Ok) return r
        if (!save(context, r.state)) return ProfileModel.Result.Error("Could not delete the profile.")
        if (before.activeId != r.state.activeId) applyActive(context, r.state.active)
        for (name in ProfileStores.filesOf(id)) {
            try {
                ProfileStores.raw(context, name).edit().clear().commit()
                // Home's files keep their (now empty) original names; a copy's file is removed.
                if (id != ProfileModel.HOME_ID) context.applicationContext.deleteSharedPreferences(name)
            } catch (e: Exception) {
            }
        }
        return r
    }

    /** Makes [id] the active profile (the PIN, if any, was checked by the caller). */
    fun switchTo(context: Context, id: String): ProfileModel.Result {
        val r = ProfileModel.switchTo(load(context), id)
        if (r !is ProfileModel.Result.Ok) return r
        if (r.state.activeId == ProfileStores.activeId) return r
        if (!save(context, r.state)) return ProfileModel.Result.Error("Could not switch profiles.")
        applyActive(context, r.state.active)
        return r
    }

    /** Null when [pin] opens [p]; otherwise one line for the PIN sheet (locks out after five misses). */
    fun checkPin(p: Profile, pin: String): String? {
        val now = SystemClock.elapsedRealtime()
        if (now < lockedUntil) return "Too many tries. Wait a moment."
        if (ProfileModel.pinOk(p, pin)) { misses = 0; return null }
        misses++
        val wait = PinRules.lockoutSeconds(misses)
        if (wait > 0) { lockedUntil = now + wait * 1000L; misses = 0 }
        return "Wrong PIN"
    }

    /** True once per process start when "Ask on start" is on and there are two or more profiles. */
    fun consumeAskOnStart(context: Context): Boolean {
        if (askedThisProcess) return false
        askedThisProcess = true
        return ProfileModel.shouldAsk(load(context))
    }

    /** After a restore or a reset rewrote the profile list: route to whatever it now says. */
    fun reloadFromDisk(context: Context) {
        val s = load(context)
        if (s.activeId != ProfileStores.activeId) applyActive(context, s.active)
    }

    private fun applyActive(context: Context, p: Profile) {
        ProfileStores.activeId = p.id
        reloadProcessState(context)
        gen.incrementAndGet()
        val notify = Runnable { for (l in listeners) try { l.onProfileSwitched(p) } catch (e: RuntimeException) { } }
        if (Looper.myLooper() == Looper.getMainLooper()) notify.run() else main.post(notify)
    }

    /**
     * The process-wide caches that outlive a screen and hold values from a profile's files:
     * re-read here. (Per-screen holders such as HomeLook, Prefs, HomeRoom and HomeSounds are
     * rebuilt with the home screen.)
     */
    fun reloadProcessState(context: Context) {
        try {
            val w = dev.tvlauncher.ambient.WallpaperStore(context).load()
            dev.tvlauncher.ambient.NotificationFeed.shared.setEnabled(w.notificationsEnabled)
        } catch (e: Throwable) {
        }
    }

    private inline fun edit(context: Context, change: (ProfileState) -> ProfileModel.Result): ProfileModel.Result {
        val r = change(load(context))
        if (r is ProfileModel.Result.Ok && !save(context, r.state)) return ProfileModel.Result.Error("Could not save the change.")
        return r
    }
}
