package dev.tvlauncher.setup

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.ambient.NotificationAccess
import dev.tvlauncher.ambient.UserWallpapers
import dev.tvlauncher.ambient.WallpaperCatalog
import dev.tvlauncher.ambient.WallpaperSource
import dev.tvlauncher.ambient.WallpaperStore
import dev.tvlauncher.ambient.WeatherCompanion
import dev.tvlauncher.appsettings.AddOns
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.input.GamepadInput
import dev.tvlauncher.input.PadCommand
import dev.tvlauncher.discover.PopularApps
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomPrefs
import dev.tvlauncher.safemode.SafeMode
import dev.tvlauncher.settings.AccentMode
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.settings.ClockFormat
import dev.tvlauncher.settings.Prefs
import dev.tvlauncher.settings.TileSize
import dev.tvlauncher.sound.SoundPrefs
import dev.tvlauncher.ui.Glass
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * The launcher's own out-of-box experience, 100% remote: one page at a time ([OobePlan]) with
 * the page's content on a glass panel at the right and ONE primary action, Skip and Back at the
 * left. Live: the TV's state is re-read whenever the screen returns to the front. Resumable
 * (the page is remembered) and never blocking: BACK on the first page leaves for Home.
 */
class SetupActivity : Activity() {
    private lateinit var store: SetupStore
    private lateinit var list: LinearLayout
    private lateinit var primary: TextView
    private lateinit var skip: TextView
    private lateinit var back: TextView
    private lateinit var position: TextView
    private lateinit var title: TextView
    private lateinit var lead: TextView
    private lateinit var hint: TextView
    private var full = true
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var gen = 0

    private var pages: List<OobePage> = listOf(OobePage.WELCOME, OobePage.DONE)
    private var page = OobePage.WELCOME
    private var memory = OobeMemory()
    private var steps: List<SetupStep> = emptyList()
    private var snap = Snapshot()

    /** Everything a page shows, read off the main thread. */
    private data class Snapshot(
        val backup: File? = null,
        val network: String? = null,
        val accountState: AccountState = AccountState.UNREADABLE,
        val accountCount: Int = 0,
        val accountScreen: Intent? = null,
        val popularInstalled: List<Pair<String, String>> = emptyList()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Safe Mode rules: while the crash guard is tripped nothing optional runs.
        if (try { SafeMode.guard(this).isTripped } catch (e: Throwable) { false }) { finish(); return }
        store = SetupStore(this)
        if (intent?.getBooleanExtra(EXTRA_RESTART, false) == true) store.saveOobe(OobePlan.restart())
        memory = store.oobe()
        full = try { DeviceProfileReader.read(this).effectsMode == EffectsMode.FULL } catch (e: Throwable) { false }
        setContentView(build())
        primary.requestFocus()
    }

    // ------------------------------------------------------------------ layout

    private fun build(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.BASE) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        root.addView(row, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(dp(Theme.MARGIN_H + 24), dp(Theme.MARGIN_V + 12), dp(Theme.MARGIN_H + 24), dp(Theme.MARGIN_V + 12))
        })
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        position = TextView(this).apply { style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2) }
        left.addView(position)
        title = TextView(this).apply { style(34f, Theme.semibold) }
        left.addView(title, lp(top = Theme.S2))
        lead = TextView(this).apply { style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2); setLineSpacing(0f, 1.2f) }
        left.addView(lead, lp(top = Theme.S3))
        hint = TextView(this).apply { style(Theme.SP_SUB, Theme.regular, Theme.TEXT); setLineSpacing(0f, 1.2f) }
        left.addView(hint, lp(top = Theme.S4))
        primary = pill(primary = true) { onPrimary() }
        left.addView(primary, wrap(top = Theme.S5))
        val minor = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        back = pill(primary = false) { goBack() }.apply { text = OobeCopy.BACK }
        skip = pill(primary = false) { onSkip() }.apply { text = SetupCopy.SKIP }
        minor.addView(back, LinearLayout.LayoutParams(-2, -2))
        minor.addView(skip, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(Theme.S3) })
        left.addView(minor, wrap(top = Theme.S3))
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(Theme.S6) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(Theme.S3), dp(Theme.S3), dp(Theme.S3), dp(Theme.S3)) }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            background = Glass.clear(this@SetupActivity, full, 24)
            addView(list, FrameLayout.LayoutParams(-1, -2))
        }
        row.addView(scroll, LinearLayout.LayoutParams(0, -2, 1.15f))
        return root
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh() // back from a permission dialog or a system screen
    }

    // ------------------------------------------------------------------ state

    /** Applies the automatic part, re-probes off the main thread, then re-renders the page. */
    private fun refresh(moveTo: OobePage? = null) {
        val g = ++gen
        val app = applicationContext
        try {
            worker.execute {
                SetupStatus.autoConfigure(app)
                val probe = try { SetupProber.probe(this, store) } catch (e: Throwable) { SetupProbe() }
                val s = SetupPlan.steps(probe, store.memory())
                val snapshot = readSnapshot()
                val favoritesEmpty = try { Prefs(app).load().favorites.isEmpty() } catch (e: Throwable) { true }
                val oobe = OobeProbe(
                    backupFound = snapshot.backup != null,
                    online = try { OobeHooks.network.isOnline(app) } catch (e: Throwable) { false },
                    account = snapshot.accountState,
                    accountScreen = snapshot.accountScreen != null,
                    permissionsPending = s.any { it.id in PERMISSION_STEPS && it.state == StepState.ACTION },
                    isHome = probe.isHome,
                    favoritesEmpty = favoritesEmpty
                )
                val p = OobePlan.pages(oobe)
                runOnUiThread {
                    if (g != gen || isDestroyed) return@runOnUiThread
                    steps = s
                    snap = snapshot
                    // The visible page stays in the flow while the user is on it, even once satisfied.
                    if (moveTo == null && !started) {
                        started = true
                        page = OobePlan.resume(p, memory)
                    } else if (moveTo != null) page = moveTo
                    pages = OobePage.values().filter { it in p || it == page }
                    render()
                }
            }
        } catch (e: Throwable) {
        }
    }

    private var started = false

    private fun readSnapshot(): Snapshot {
        val (state, count) = try {
            val n = AccountManager.get(this).getAccountsByType("com.google").size
            (if (n > 0) AccountState.SIGNED_IN else AccountState.NONE_VISIBLE) to n
        } catch (e: Throwable) {
            AccountState.UNREADABLE to 0
        }
        val accountScreen = listOf(Settings.ACTION_SYNC_SETTINGS, Settings.ACTION_ADD_ACCOUNT)
            .map { Intent(it) }.firstOrNull { SetupProber.resolves(this, it) }
        val popular = try {
            PopularApps.all.sortedByDescending { it.popularity }
                .filter { SetupProber.installed(this, it.packageName) }
                .take(MAX_APPS).map { it.packageName to it.label }
        } catch (e: Throwable) { emptyList() }
        return Snapshot(
            backup = ConfigBackupIo.find(this),
            network = try { OobeHooks.network.status(this) } catch (e: Throwable) { null },
            accountState = state, accountCount = count, accountScreen = accountScreen,
            popularInstalled = popular
        )
    }

    private fun move(to: OobePage) {
        page = to
        memory = memory.copy(current = to)
        store.saveOobe(memory)
        if (to == OobePage.DONE) store.markCompleted()
        refresh(to)
    }

    private fun onPrimary() {
        when (page) {
            OobePage.DONE -> return leave()
            OobePage.RESTORE -> snap.backup?.let { f ->
                worker.execute {
                    val ok = ConfigBackupIo.restore(applicationContext, f)
                    runOnUiThread { if (ok) finishPage() else hint.text = "That backup could not be read. Nothing was changed." }
                }
                return
            }
            OobePage.PERMISSIONS, OobePage.HOME -> {
                val next = SetupPlan.next(pageSteps())
                if (next != null) return run(next)
            }
            OobePage.NETWORK -> if (snap.network?.startsWith("Connected") != true) {
                if (!OobeHooks.network.open(this)) hint.text = "Wi-Fi settings didn't open. Try again."
                return
            }
            else -> Unit
        }
        finishPage()
    }

    private fun finishPage() {
        memory = OobePlan.finish(memory, page)
        move(OobePlan.next(pages, page, memory))
    }

    private fun onSkip() {
        if (page == OobePage.DONE) return leave()
        memory = OobePlan.skip(memory, page)
        move(OobePlan.next(pages, page, memory))
    }

    private fun goBack() {
        val prev = OobePlan.previous(pages, page)
        if (prev == null) leave() else move(prev)
    }

    /** Leaves for Home; the flow resumes here from Settings > System > Setup. */
    private fun leave() {
        store.markCompleted()
        finish()
    }

    @Deprecated("BACK goes one page back; on the first page it leaves")
    override fun onBackPressed() = goBack()

    // ------------------------------------------------------------------ pages

    private class Row(val key: String, val title: String, val value: String, val done: Boolean? = null, val action: (() -> Unit)? = null)

    private fun pageSteps(): List<SetupStep> = when (page) {
        OobePage.PERMISSIONS -> steps.filter { it.id in PERMISSION_STEPS }
        OobePage.HOME -> steps.filter { it.id == StepId.HOME }
        else -> emptyList()
    }

    private fun render() {
        val focusedKey = currentFocus?.tag as? String
        position.text = OobePlan.position(pages, page)
        title.text = OobeCopy.title(page)
        lead.text = OobeCopy.lead(page)
        val rows = rows()
        val stepNext = SetupPlan.next(pageSteps())
        hint.text = when (page) {
            OobePage.PERMISSIONS, OobePage.HOME -> stepNext?.detail ?: SetupCopy.ALL_SET
            OobePage.DONE -> SetupPlan.progress(steps)
            else -> ""
        }
        hint.visibility = if (hint.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        primary.text = when (page) {
            OobePage.DONE -> SetupCopy.FINISH
            OobePage.RESTORE -> "Restore from USB"
            OobePage.WELCOME -> "Get started"
            OobePage.NETWORK -> if (snap.network?.startsWith("Connected") == true) OobeCopy.CONTINUE else "Choose a network"
            OobePage.PERMISSIONS, OobePage.HOME -> stepNext?.actionLabel ?: OobeCopy.CONTINUE
            else -> OobeCopy.CONTINUE
        }
        skip.visibility = if (page == OobePage.DONE) View.GONE else View.VISIBLE
        list.removeAllViews()
        for (r in rows) list.addView(rowView(r), lp(top = 0).apply { bottomMargin = dp(Theme.S1) })
        val target = (0 until list.childCount).map { list.getChildAt(it) }.firstOrNull { focusedKey != null && it.tag == focusedKey }
        if (target != null) target.requestFocus() else if (!back.hasFocus() && !skip.hasFocus()) primary.requestFocus()
    }

    private fun stepRows(s: List<SetupStep>) = s.map { st ->
        Row("step:${st.id}", st.title, st.detail, done = st.done, action = { run(st) })
    }

    private fun rows(): List<Row> = when (page) {
        OobePage.WELCOME -> listOf(
            Row("lang", "Language", Locale.getDefault().displayName, action = openIf(Settings.ACTION_LOCALE_SETTINGS)),
            Row("keyboard", "Keyboard", keyboardLabel() + " · type with the remote, or a phone keyboard if the TV offers one",
                action = openIf(Settings.ACTION_INPUT_METHOD_SETTINGS))
        )
        OobePage.RESTORE -> listOf(Row("restore", "Saved setup", snap.backup?.let { "${it.name} · ${it.parentFile?.name}" } ?: "None found"))
        OobePage.NETWORK -> listOf(
            Row("net", "Network", snap.network ?: "Not connected", done = snap.network?.startsWith("Connected") == true,
                action = { if (!OobeHooks.network.open(this)) hint.text = "Wi-Fi settings didn't open. Try again." })
        )
        OobePage.TIME -> timeRows()
        OobePage.ACCOUNT -> listOf(
            Row("account", "Google account", OobeCopy.account(snap.accountState, snap.accountCount),
                done = snap.accountState == AccountState.SIGNED_IN,
                action = snap.accountScreen?.let { i -> { SystemActions.start(this, Intent(i)); Unit } })
        )
        OobePage.PERMISSIONS, OobePage.HOME -> stepRows(pageSteps())
        OobePage.APPS -> appRows()
        OobePage.LOOK -> lookRows()
        OobePage.DONE -> stepRows(steps)
    }

    private fun openIf(action: String): (() -> Unit)? {
        val i = Intent(action)
        return if (SetupProber.resolves(this, i)) ({ SystemActions.start(this, Intent(action)); Unit }) else null
    }

    private fun keyboardLabel(): String = try {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        val pkg = id.substringBefore('/')
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Throwable) {
        "On-screen keyboard"
    }

    private fun timeRows(): List<Row> {
        val a = AppearancePrefs(this)
        val clock = a.load().clockFormat
        val room = RoomPrefs(this).load()
        val tz = TimeZone.getDefault()
        val weather = WeatherCompanion.settingsIntent(this)
        return listOf(
            Row("tz", "Time zone", tz.getDisplayName(false, TimeZone.LONG) + " (from the TV)", action = openIf(Settings.ACTION_DATE_SETTINGS)),
            Row("clock", "Clock", when (clock) { ClockFormat.AUTO -> "Automatic"; ClockFormat.H24 -> "24-hour"; ClockFormat.H12 -> "12-hour" }) {
                a.update { it.copy(clockFormat = ClockFormat.values()[(clock.ordinal + 1) % ClockFormat.values().size]) }
                render()
            },
            if (weather != null) Row("city", "Your city", "Choose it in the Weather app") {
                if (SystemActions.start(this, weather)) store.markVisited(StepId.WEATHER)
            } else Row("city", "Your city", if (room.latitudeDeg != null) "Estimated from the time zone" else "Time of day only")
        )
    }

    private fun appRows(): List<Row> {
        val prefs = Prefs(this)
        val favs = try { prefs.load().favorites } catch (e: Throwable) { emptyList() }
        val rows = snap.popularInstalled.map { (pkg, label) ->
            Row("app:$pkg", label, if (pkg in favs) "Favorite" else "Select to add", done = pkg in favs) {
                val s = prefs.load()
                prefs.save(s.copy(favorites = if (pkg in s.favorites) s.favorites - pkg else s.favorites + pkg))
                render()
            }
        }
        val more = Row("more", "Find more apps", "Popular apps for this TV") {
            if (!SystemActions.launchPackage(this, AddOns.STORE_PACKAGE) &&
                !SystemActions.start(this, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=tv&c=apps")))) {
                hint.text = "Open Discover on the home screen to find more apps."
            }
        }
        return rows + more + stepRows(steps.filter { it.id == StepId.APPS })
    }

    private fun lookRows(): List<Row> {
        val ap = AppearancePrefs(this)
        val a = ap.load()
        val accentIndex = if (a.accentMode == AccentMode.AUTO_BRAND) -1 else AppearanceSettings.PRESETS.indexOf(a.fixedAccent)
        val roomPrefs = RoomPrefs(this)
        val room = roomPrefs.load()
        val wallStore = WallpaperStore(this)
        val wall = wallStore.load()
        val saver = (wall.source as? WallpaperSource.BuiltInSlideshow)?.collection
        val sounds = SoundPrefs(this)
        val snd = sounds.load()
        return listOf(
            Row("accent", "Accent color", if (accentIndex < 0) "From the focused app" else ACCENT_NAMES.getOrElse(accentIndex) { "Custom" }) {
                val n = accentIndex + 1
                ap.save(if (n >= AppearanceSettings.PRESETS.size) a.copy(accentMode = AccentMode.AUTO_BRAND)
                    else a.copy(accentMode = AccentMode.FIXED, fixedAccent = AppearanceSettings.PRESETS[n]))
                render()
            },
            Row("tiles", "Home layout", when (a.rowTileSize) { TileSize.SMALL -> "Compact row"; TileSize.MEDIUM -> "Standard row"; TileSize.LARGE -> "Large row" }) {
                ap.save(a.copy(rowTileSize = TileSize.values()[(a.rowTileSize.ordinal + 1) % TileSize.values().size]))
                render()
            },
            Row("room", "Room lighting", if (room.choice == RoomChoice.AUTO) "Automatic" else "Fixed", done = room.choice == RoomChoice.AUTO) {
                roomPrefs.save(room.copy(choice = if (room.choice == RoomChoice.AUTO) RoomChoice.STANDARD else RoomChoice.AUTO))
                render()
            },
            Row("saver", "Screensaver", saver?.let { "$it slideshow" } ?: "Still wallpaper") {
                val cols = WallpaperCatalog.COLLECTIONS
                val i = cols.indexOf(saver)
                wallStore.save(wall.copy(source = if (i + 1 >= cols.size) WallpaperSource.DEFAULT
                    else WallpaperSource.BuiltInSlideshow(cols[i + 1], SLIDESHOW_SEC)))
                render()
            },
            Row("sounds", "Sounds", if (snd.enabled) "On" else "Off", done = snd.enabled) {
                sounds.save(snd.copy(enabled = !snd.enabled))
                render()
            }
        )
    }

    // ------------------------------------------------------------------ views

    private fun rowView(r: Row): View = LinearLayout(this).apply {
        tag = r.key
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(dp(Theme.S4), dp(Theme.S3), dp(Theme.S4), dp(Theme.S3))
        if (r.done != null) {
            val mark = TextView(context).apply {
                gravity = Gravity.CENTER
                style(Theme.SP_LABEL + 1f, Theme.semibold, if (r.done) Theme.TEXT_DARK else Theme.TEXT_2)
                text = if (r.done) "✓" else ""
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (r.done) setColor(Theme.TEXT) else setStroke(dp(2), 0x59FFFFFF)
                }
            }
            addView(mark, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(Theme.S4) })
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply { style(Theme.SP_BUTTON + 1f, Theme.medium); text = r.title })
        texts.addView(TextView(context).apply { style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2); text = r.value; maxLines = 2 }, lp(top = 2))
        addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        fun paint(f: Boolean) {
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(if (f) Theme.GLASS_ROW_FOCUS else 0) }
        }
        paint(false)
        onFocusChangeListener = View.OnFocusChangeListener { _, f -> paint(f) }
        setOnClickListener { r.action?.invoke() }
    }

    private fun pill(primary: Boolean, onClick: () -> Unit) = object : TextView(this) {
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            paintPill(this, primary, gainFocus)
        }
    }.apply {
        style(Theme.SP_BUTTON + 1f, Theme.medium)
        isFocusable = true
        isClickable = true
        gravity = Gravity.CENTER
        setPadding(dp(if (primary) 28 else 20), dp(12), dp(if (primary) 28 else 20), dp(12))
        paintPill(this, primary, false)
        setOnClickListener { onClick() }
    }

    private fun paintPill(v: TextView, primary: Boolean, focused: Boolean) {
        v.background = GradientDrawable().apply {
            cornerRadius = dp(Theme.RADIUS_PILL).toFloat()
            setColor(when { focused -> Theme.TEXT; primary -> 0x33FFFFFF; else -> 0x14FFFFFF })
        }
        v.setTextColor(if (focused) Theme.TEXT_DARK else Theme.TEXT)
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun wrap(top: Int) = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top) }

    /**
     * Game controllers work through setup like the remote (A = OK, B = BACK, sticks move focus).
     * Spotlight and Settings buttons do nothing here, so a stray press cannot leave the checklist.
     */
    private val pad = GamepadInput(this, object : GamepadInput.Host {
        override fun onPadCommand(command: PadCommand, repeat: Int): Boolean =
            command == PadCommand.SEARCH || command == PadCommand.START || command == PadCommand.SELECT
    })

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = pad.dispatch(event, ::handleKey)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = pad.motion(ev) || super.dispatchGenericMotionEvent(ev)

    private fun handleKey(event: KeyEvent): Boolean {
        // MENU skips the current page from anywhere (a remote shortcut; Skip is also on screen).
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) { onSkip(); return true }
        return super.dispatchKeyEvent(event)
    }

    // ------------------------------------------------------------------ step actions

    /** One press on a checklist step: its action. A step that cannot be done here shows the calm line. */
    private fun run(step: SetupStep) {
        store.unskip(step.id)
        val ok = when (step.action) {
            StepAction.NONE -> true
            StepAction.CHOOSE_HOME -> SetupProber.homeChooser(this)?.let { startForResult(it) } ?: false
            StepAction.AUTO_ENABLE -> { worker.execute { SetupStatus.autoConfigure(applicationContext); store.markAutoTried(step.id) }; true }
            StepAction.OPEN_LISTENER_SCREEN -> NotificationAccess.openSettings(this)
            StepAction.OPEN_ACCESSIBILITY -> SystemActions.start(this, SetupProber.accessibilityIntent())
            StepAction.REQUEST_LOCATION -> SetupStatus.requestPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            StepAction.REQUEST_PICTURES -> SetupStatus.requestPermission(this, UserWallpapers.PERMISSION)
            StepAction.OPEN_WEATHER -> WeatherCompanion.settingsIntent(this)?.let { SystemActions.start(this, it) }
                ?.also { if (it) store.markVisited(StepId.WEATHER) } ?: false
            StepAction.OPEN_GET_APPS -> SystemActions.launchPackage(this, AddOns.STORE_PACKAGE)
            StepAction.OPEN_USAGE_SCREEN -> UsageAccess.openSettings(this)
        }
        if (!ok) hint.text = SetupCopy.HELPER_LINE
        refresh()
    }

    private fun startForResult(i: Intent): Boolean = try { startActivityForResult(i, REQ_HOME); true } catch (e: Throwable) { false }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    @Deprecated("Activity result of the home chooser")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refresh()
    }

    override fun onDestroy() {
        gen++
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        /** Start from the first page (Settings > System > Setup when the flow was finished). */
        const val EXTRA_RESTART = "restart"
        private const val REQ_HOME = 41
        private const val MAX_APPS = 8
        private const val SLIDESHOW_SEC = 60
        private val PERMISSION_STEPS = setOf(StepId.NOW_PLAYING, StepId.SHORTCUTS, StepId.NEARBY, StepId.PHOTOS, StepId.INSIGHTS)
        private val ACCENT_NAMES = listOf("Blue", "Violet", "Rose", "Ember", "Amber", "Mint", "Teal", "Silver")
    }
}
