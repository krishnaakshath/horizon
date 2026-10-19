package dev.tvlauncher.store

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.store.core.AbiPicker
import dev.tvlauncher.store.core.ApkFetch
import dev.tvlauncher.store.core.ComponentRisk
import dev.tvlauncher.store.core.DisplayText
import dev.tvlauncher.store.core.HashExpectation
import dev.tvlauncher.store.core.InstallFallbackPolicy
import dev.tvlauncher.store.core.InstallOutcomeResolver
import dev.tvlauncher.store.core.ReviewModel
import dev.tvlauncher.store.core.InstalledState
import dev.tvlauncher.store.core.ReceiverTokenPolicy
import dev.tvlauncher.store.core.FdroidApp
import dev.tvlauncher.store.core.FdroidCatalogCodec
import dev.tvlauncher.store.core.Format
import dev.tvlauncher.store.core.GithubRelease
import dev.tvlauncher.store.core.GithubRepoRef
import dev.tvlauncher.store.core.InstallReview
import dev.tvlauncher.store.core.InstallStatusMapper
import dev.tvlauncher.store.core.InstalledRecord
import dev.tvlauncher.store.core.InstalledRegistry
import dev.tvlauncher.store.core.LinkHistory
import dev.tvlauncher.store.core.PermissionRisk
import dev.tvlauncher.store.core.RepoList
import dev.tvlauncher.store.core.Review
import dev.tvlauncher.store.core.Sha256Stream
import dev.tvlauncher.store.core.SignerCompare
import dev.tvlauncher.store.core.UrlPolicy
import dev.tvlauncher.store.data.FdroidRepository
import dev.tvlauncher.store.data.GithubRepository
import dev.tvlauncher.store.data.StoreFiles
import dev.tvlauncher.store.install.ApkInspector
import dev.tvlauncher.store.install.Inspected
import dev.tvlauncher.store.install.Installer
import dev.tvlauncher.store.net.ApkDownloader
import dev.tvlauncher.store.net.SecureHttp
import dev.tvlauncher.store.ui.ButtonKind
import dev.tvlauncher.store.ui.Kit
import dev.tvlauncher.store.ui.ProgressBarView
import dev.tvlauncher.store.ui.button
import dev.tvlauncher.store.ui.callout
import dev.tvlauncher.store.ui.chip
import dev.tvlauncher.store.ui.dp
import dev.tvlauncher.store.ui.glass
import dev.tvlauncher.store.ui.horizontal
import dev.tvlauncher.store.ui.iconTile
import dev.tvlauncher.store.ui.input
import dev.tvlauncher.store.ui.lp
import dev.tvlauncher.store.ui.rounded
import dev.tvlauncher.store.ui.text
import dev.tvlauncher.store.ui.vertical
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Get Apps: install apps from an https link, the F-Droid catalog or a GitHub project's latest release.
 * Every install goes: download (https, capped, hashed, verified) → inspect → explicit user confirmation →
 * system PackageInstaller session (Android shows its own prompt too). Nothing is ever installed silently.
 */
class StoreActivity : Activity(), Installer.Listener {
    /** Game controllers: A = OK, B = BACK, sticks move focus, Y / Start open the launcher's Spotlight. */
    private val pad = dev.tvlauncher.input.GamepadInput(this)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = pad.dispatch(event) { super.dispatchKeyEvent(it) }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = pad.motion(ev) || super.dispatchGenericMotionEvent(ev)

    private sealed class Screen {
        object Home : Screen()
        object Link : Screen()
        object Fdroid : Screen()
        class FdroidDetail(val app: FdroidApp, val installedNote: String? = null) : Screen()
        object Github : Screen()
        class Release(val ref: GithubRepoRef, val release: GithubRelease, val expectedPackage: String?, val note: String?) : Screen()
        object Installed : Screen()
        class Permission(val job: DownloadJob) : Screen()
    }

    private lateinit var root: FrameLayout
    private val stack = ArrayList<Screen>()
    /** A transient full-screen state (progress, confirm, result) shown above the stack. */
    private var modal: View? = null
    private var onModalBack: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var cancelFlag = AtomicBoolean(false)

    private lateinit var files: StoreFiles
    private lateinit var http: SecureHttp
    private lateinit var downloader: ApkDownloader
    private lateinit var inspector: ApkInspector
    private lateinit var fdroid: FdroidRepository
    private lateinit var github: GithubRepository

    private var catalog: FdroidCatalogCodec.Catalog? = null
    private var fdroidQuery = ""
    private var autoRefreshTried = false

    /** The APK currently waiting for confirmation or being installed (deleted after the attempt). */
    private var pendingFile: File? = null
    private var pendingJob: DownloadJob? = null
    private var pendingInspected: Inspected? = null
    /** The install handed to Android, kept apart from [pendingFile] so a late result is attributed correctly. */
    private class Installing(val file: File, val inspected: Inspected, val job: DownloadJob) {
        var committedAt: Long = System.currentTimeMillis()
    }
    private var installing: Installing? = null
    private var fallbackStartedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        files = StoreFiles(this)
        http = SecureHttp("GetApps/${BuildConfigLite.VERSION} (Android ${Build.VERSION.RELEASE}; TV)")
        downloader = ApkDownloader(this, http)
        inspector = ApkInspector(this)
        fdroid = FdroidRepository(files, http)
        github = GithubRepository(http)
        if (Installer.currentToken == null) {
            // Nothing in flight: abandon our leftover sessions (e.g. after process death) and stale downloads.
            Installer.abandonStaleSessions(this)
            ApkDownloader.cleanStale(this)
        }
        root = FrameLayout(this)
        root.setBackgroundColor(Kit.BASE)
        setContentView(root)
        stack += Screen.Home
        Installer.listener = this
        render()
    }

    override fun onResume() {
        super.onResume()
        val top = stack.lastOrNull()
        if (modal == null && top is Screen.Permission && Installer.canRequestInstalls(this)) {
            stack.removeAt(stack.size - 1)
            startDownload(top.job)
        } else if (modal == null && (top is Screen.Installed || top is Screen.Home)) {
            render()
        }
        if (fallbackStartedAt > 0) checkFallbackResult()
    }

    override fun onDestroy() {
        if (Installer.listener === this) Installer.listener = null
        cancelFlag.set(true)
        generation++
        io.shutdownNow()
        discardPending()
        if (installing != null && fallbackStartedAt == 0L) {
            // Nobody is left to show the result: abandon the pending session and clean up.
            installing = null
            Installer.abandonCurrent(this)
            ApkDownloader.cleanStale(this)
        }
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- navigation

    private fun push(s: Screen) { stack += s; modal = null; render() }

    private fun popTo(predicate: (Screen) -> Boolean) {
        while (stack.size > 1 && !predicate(stack.last())) stack.removeAt(stack.size - 1)
        modal = null
        render()
    }

    @Deprecated("Activity back handling for targetSdk 30")
    override fun onBackPressed() {
        val m = onModalBack
        if (modal != null && m != null) { m(); return }
        if (modal != null) { modal = null; render(); return }
        if (stack.size > 1) { stack.removeAt(stack.size - 1); render(); return }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun render() {
        root.removeAllViews()
        val m = modal
        if (m != null) {
            root.addView(m)
            focusFirst(m)
            return
        }
        onModalBack = null
        val view = when (val s = stack.last()) {
            Screen.Home -> homeScreen()
            Screen.Link -> linkScreen()
            Screen.Fdroid -> fdroidScreen()
            is Screen.FdroidDetail -> fdroidDetailScreen(s)
            Screen.Github -> githubScreen()
            is Screen.Release -> releaseScreen(s)
            Screen.Installed -> installedScreen()
            is Screen.Permission -> permissionScreen(s.job)
        }
        root.addView(view)
        focusFirst(view)
    }

    private fun showModal(view: View, onBack: (() -> Unit)?) {
        modal = view
        onModalBack = onBack
        root.removeAllViews()
        root.addView(view)
        focusFirst(view)
    }

    private fun focusFirst(v: View) {
        v.post {
            val target = v.findViewWithTag<View>(FOCUS_TAG) ?: v.focusSearch(View.FOCUS_DOWN)
            target?.requestFocus()
        }
    }

    private fun View.primaryFocus(): View { tag = FOCUS_TAG; return this }

    // ---------------------------------------------------------------- scaffold

    /** Overscan-safe page: header (logomark + wordmark + title), scrollable body, trust footer. */
    private fun page(title: String?, body: View, scroll: Boolean = true): View {
        val col = vertical(this)
        col.setPadding(dp(Kit.MARGIN_H), dp(Kit.MARGIN_V), dp(Kit.MARGIN_H), dp(Kit.MARGIN_V))

        val header = horizontal(this, 14)
        val mark = ImageView(this).apply { setImageResource(R.drawable.ic_launcher_foreground) }
        header.addView(FrameLayout(this).apply {
            background = rounded(Kit.SURFACE, 12, Kit.STROKE, 1)
            addView(mark, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(ImageView(this).apply { setImageResource(R.drawable.wordmark); adjustViewBounds = true },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)))
        if (title != null) {
            header.addView(text("·", 20f, Kit.TEXT_3))
            header.addView(text(title, 20f, Kit.TEXT_2, Kit.medium))
        }
        col.addView(header, lp())

        val bodyHolder: View = if (scroll) ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(4), dp(8), dp(4), dp(8))
            addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else body
        col.addView(bodyHolder, lp(h = 0, weight = 1f).apply { topMargin = dp(20); bottomMargin = dp(14) })

        col.addView(text(Kit.FOOTER, 13f, Kit.TEXT_3), lp())
        return col
    }

    private fun sectionTitle(s: String) = text(s, 15f, Kit.TEXT_2, Kit.medium).apply { setPadding(0, dp(6), 0, dp(2)) }

    private fun statusLine(s: String, color: Int = Kit.TEXT_2) = text(s, 15f, color)

    // ---------------------------------------------------------------- home

    private fun homeScreen(): View {
        val body = vertical(this, 22)
        body.addView(text("Get apps for this TV", 34f, Kit.TEXT, Kit.light))
        body.addView(text("Choose where the app comes from. You'll see what it is and what it can access before anything is installed.",
            16f, Kit.TEXT_2))

        val cards = horizontal(this, 20)
        cards.addView(sourceCard(R.drawable.ic_link, "Install from link", "Paste a direct https link to an .apk file") {
            push(Screen.Link)
        }.primaryFocus(), lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        cards.addView(sourceCard(R.drawable.ic_catalog, "F-Droid", "Free & open-source apps, checksum-verified") {
            push(Screen.Fdroid)
        }, lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        cards.addView(sourceCard(R.drawable.ic_release, "GitHub", "Latest release of a project you choose") {
            push(Screen.Github)
        }, lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        body.addView(cards, lp(h = dp(200)))

        val row = horizontal(this, 16)
        val count = files.installed.size
        row.addView(button("Installed by H02 Store" + if (count > 0) " ($count)" else "") { push(Screen.Installed) })
        if (!Installer.canRequestInstalls(this)) {
            row.addView(chip("One-time permission needed before the first install", Kit.WARN, Kit.WARN_BG))
        }
        body.addView(row, lp())
        return page(null, body)
    }

    private fun sourceCard(icon: Int, title: String, subtitle: String, onClick: () -> Unit): View {
        val col = vertical(this, 10)
        col.addView(iconTile(getDrawable(icon), 56))
        col.addView(View(this), lp(h = 0, weight = 1f))
        col.addView(text(title, 22f, Kit.TEXT, Kit.medium))
        col.addView(text(subtitle, 14f, Kit.TEXT_2))
        return glass(col, 22, 22, onClick)
    }

    // ---------------------------------------------------------------- link

    private fun linkScreen(): View {
        val body = vertical(this, 16)
        body.addView(text("Install from a link", 28f, Kit.TEXT, Kit.light))
        body.addView(text("Enter a direct https:// link to an .apk file. Links to this device or your local network, " +
            "plain http:// and unusual addresses are refused.", 15f, Kit.TEXT_2))
        val error = statusLine("", Kit.DANGER).apply { visibility = View.GONE }
        lateinit var field: EditText
        val go = {
            val url = field.text.toString().trim()
            when (val v = UrlPolicy.check(url)) {
                is UrlPolicy.Verdict.Rejected -> { error.text = v.reason; error.visibility = View.VISIBLE }
                is UrlPolicy.Verdict.Allowed -> {
                    error.visibility = View.GONE
                    files.linkHistory = LinkHistory.add(files.linkHistory, url)
                    startDownload(DownloadJob.fromLink(url))
                }
            }
        }
        field = input("https://example.org/app.apk", "https://", uri = true) { go() }
        val row = horizontal(this, 14)
        row.addView(field.primaryFocus(), lp(0, weight = 1f))
        row.addView(button("Download", ButtonKind.PRIMARY) { go() })
        body.addView(row, lp())
        body.addView(error)
        val history = files.linkHistory
        if (history.isNotEmpty()) {
            body.addView(sectionTitle("Recent links"))
            for (url in history) {
                body.addView(glass(text(DisplayText.sanitize(url, DisplayText.URL), 15f, Kit.TEXT), 18, 12) {
                    field.setText(url)
                    field.setSelection(url.length)
                    go()
                }, lp())
            }
        }
        return page("Link", body)
    }

    // ---------------------------------------------------------------- F-Droid

    private fun fdroidScreen(): View {
        if (catalog == null) catalog = fdroid.cached()
        val c = catalog
        if (!autoRefreshTried && (c == null || FdroidCatalogCodec.isStale(c.savedAtMillis, System.currentTimeMillis()))) {
            // Refresh at most once per 24 h, only when the user opens F-Droid (and once per session on failure).
            autoRefreshTried = true
            main.post { refreshCatalog(auto = true) }
        }
        val body = vertical(this, 14)
        val status = horizontal(this, 14)
        val info = if (c == null) "Catalog not downloaded yet." else
            "Catalog updated ${ago(c.savedAtMillis)} · ${c.apps.size} apps · ${c.apps.count { it.tvReady || it.curated }} TV picks"
        status.addView(statusLine(info), lp(0, weight = 1f))
        status.addView(button("Refresh") { refreshCatalog(auto = false) })
        body.addView(status, lp())

        val results = vertical(this, 10)
        val search = input("Search by name or package id (e.g. vlc, org.videolan.vlc)", fdroidQuery) {}
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun afterTextChanged(s: Editable?) {
                fdroidQuery = s?.toString() ?: ""
                fillFdroidResults(results)
            }
        })
        body.addView(search, lp())
        body.addView(results, lp())
        if (!fillFdroidResults(results)) search.primaryFocus()
        return page("F-Droid", body)
    }

    /** Fills the result rows; true when at least one (focusable) row was added and tagged for first focus. */
    private fun fillFdroidResults(results: LinearLayout): Boolean {
        results.removeAllViews()
        val apps = catalog?.apps ?: return false
        val q = fdroidQuery
        results.addView(sectionTitle(if (q.isBlank()) "TV picks" else "Results for “${q.trim()}”"))
        val hits = FdroidCatalogCodec.search(apps, q)
        if (hits.isEmpty()) {
            results.addView(statusLine("No compatible F-Droid app matches. Try another word or a package id."))
            return false
        }
        hits.forEachIndexed { i, app ->
            val row = appRow(app) { push(Screen.FdroidDetail(app)) }
            if (i == 0) row.primaryFocus()
            results.addView(row, lp())
        }
        return true
    }

    private fun appRow(app: FdroidApp, onClick: () -> Unit): View {
        val row = horizontal(this, 16)
        row.addView(iconTile(null, 48, app.name))
        val mid = vertical(this, 4)
        val titleRow = horizontal(this, 8)
        titleRow.addView(text(app.name, 18f, Kit.TEXT, Kit.medium))
        if (app.tvReady) titleRow.addView(chip("TV", Kit.ACCENT, Kit.OK_BG))
        if (app.antiFeatures.isNotEmpty()) titleRow.addView(chip("Note", Kit.WARN, Kit.WARN_BG))
        mid.addView(titleRow)
        if (app.summary.isNotEmpty()) mid.addView(text(app.summary, 14f, Kit.TEXT_2).apply { maxLines = 1 })
        row.addView(mid, lp(0, weight = 1f))
        row.addView(text("${app.versionName}\n${Format.size(app.size)}", 14f, Kit.TEXT_2).apply { gravity = Gravity.END })
        return glass(row, 18, 12, onClick)
    }

    private fun fdroidDetailScreen(s: Screen.FdroidDetail): View {
        val app = s.app
        val body = vertical(this, 16)
        val head = horizontal(this, 18)
        head.addView(iconTile(null, 72, app.name))
        val t = vertical(this, 6)
        t.addView(text(app.name, 28f, Kit.TEXT, Kit.medium))
        t.addView(text(app.packageName, 14f, Kit.TEXT_3, Kit.mono))
        head.addView(t, lp(0, weight = 1f))
        body.addView(head, lp())
        if (app.summary.isNotEmpty()) body.addView(text(app.summary, 17f, Kit.TEXT_2))
        s.installedNote?.let { body.addView(callout(it, Kit.ACCENT, Kit.OK_BG)) }
        body.addView(facts(listOf(
            "Version" to "${app.versionName} (${app.versionCode})",
            "Download size" to Format.size(app.size),
            "SHA-256 (from F-Droid)" to Sha256Stream.short(app.sha256),
            "Signer (from F-Droid)" to (app.signerSha256?.let { SignerCompare.shortFingerprint(it) } ?: "not published"),
            "Categories" to DisplayText.sanitize(app.categories.joinToString(", "), 120).ifEmpty { "—" },
        )), lp())
        if (!app.tvReady) body.addView(callout("F-Droid doesn't list this app as made for TV. It may need a mouse or touch screen.", Kit.WARN, Kit.WARN_BG))
        if (app.antiFeatures.isNotEmpty()) {
            body.addView(callout("F-Droid notes: " + app.antiFeatures.joinToString(", ") { antiFeature(it) }, Kit.WARN, Kit.WARN_BG))
        }
        val row = horizontal(this, 14)
        row.addView(button("Download & review", ButtonKind.PRIMARY) { startDownload(DownloadJob.fromFdroid(app)) }.primaryFocus())
        row.addView(button("Back") { onBackPressedCompat() })
        body.addView(row)
        return page("F-Droid", body)
    }

    private fun antiFeature(k: String) = when (k) {
        "NonFreeNet" -> "uses a non-free network service"
        "Ads" -> "contains ads"
        "Tracking" -> "tracks you"
        "NonFreeDep" -> "depends on non-free software"
        "NonFreeAdd" -> "promotes non-free add-ons"
        "UpstreamNonFree" -> "upstream source is not fully free"
        "NSFW" -> "may show adult content"
        "KnownVuln" -> "has a known security issue"
        "DisabledAlgorithm" -> "signed with a weak algorithm"
        "NoSourceSince" -> "source code no longer available"
        else -> k
    }

    private fun refreshCatalog(auto: Boolean) {
        val gen = ++generation
        val cancel = AtomicBoolean(false).also { cancelFlag = it }
        val hadCatalog = catalog != null
        val abort = {
            cancel.set(true)
            generation++
            modal = null
            if (!hadCatalog && stack.lastOrNull() == Screen.Fdroid) stack.removeAt(stack.size - 1)
            render()
        }
        val (view, bar, label) = progressView(
            "Updating the F-Droid catalog",
            "Downloading F-Droid's index (about 20 MB) and keeping a small catalog of apps that work on this TV. " +
                "This happens at most once a day.",
        ) { abort() }
        showModal(view) { abort() }
        var lastUi = 0L
        io.execute {
            try {
                val c = fdroid.refresh({ cancel.get() }) { done, total ->
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUi > 200) {
                        lastUi = now
                        main.post { if (gen == generation) updateProgress(bar, label, done, total) }
                    }
                }
                main.post {
                    if (gen != generation) return@post
                    catalog = c
                    modal = null
                    render()
                }
            } catch (e: Throwable) {
                main.post {
                    if (gen != generation) return@post
                    if (cancel.get()) {
                        modal = null
                        if (!hadCatalog && stack.lastOrNull() == Screen.Fdroid) stack.removeAt(stack.size - 1)
                        render()
                    } else if (auto && hadCatalog) {
                        modal = null
                        render() // keep using the cached catalog quietly
                    } else {
                        showError("Couldn't update the F-Droid catalog", describeError(e)) {
                            modal = null
                            if (!hadCatalog && stack.lastOrNull() == Screen.Fdroid) stack.removeAt(stack.size - 1)
                            render()
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- GitHub

    private fun githubScreen(): View {
        val body = vertical(this, 16)
        body.addView(text("Install from GitHub", 28f, Kit.TEXT, Kit.light))
        body.addView(text("Enter a project as owner/repo (for example jellyfin/jellyfin-androidtv). H02 Store reads its latest " +
            "release and offers the .apk built for this TV.", 15f, Kit.TEXT_2))
        val error = statusLine("", Kit.DANGER).apply { visibility = View.GONE }
        lateinit var field: EditText
        val go = {
            val ref = GithubRepoRef.parse(field.text.toString())
            if (ref == null) {
                error.text = "That isn't a valid owner/repo name."
                error.visibility = View.VISIBLE
            } else {
                files.repos = RepoList.add(files.repos, ref.slug)
                lookupRelease(ref, expectedPackage = null, installedTag = null)
            }
        }
        field = input("owner/repo", uri = true) { go() }
        val row = horizontal(this, 14)
        row.addView(field.primaryFocus(), lp(0, weight = 1f))
        row.addView(button("Look up", ButtonKind.PRIMARY) { go() })
        body.addView(row, lp())
        body.addView(error)
        val repos = files.repos
        if (repos.isNotEmpty()) {
            body.addView(sectionTitle("Your projects"))
            for (slug in repos) {
                val r = horizontal(this, 12)
                r.addView(glass(text(slug, 17f, Kit.TEXT, Kit.medium), 18, 12) {
                    GithubRepoRef.parse(slug)?.let { lookupRelease(it, null, null) }
                }, lp(0, weight = 1f))
                r.addView(button("Remove") {
                    files.repos = files.repos.filter { it != slug }
                    render()
                })
                body.addView(r, lp())
            }
        }
        return page("GitHub", body)
    }

    private fun lookupRelease(ref: GithubRepoRef, expectedPackage: String?, installedTag: String?) {
        if (!online()) { showOffline(); return }
        val gen = ++generation
        val (view, _, _) = progressView("Looking up ${ref.slug}", "Asking GitHub for the latest release…") { generation++; modal = null; render() }
        showModal(view) { generation++; modal = null; render() }
        io.execute {
            try {
                val release = github.latest(ref)
                main.post {
                    if (gen != generation) return@post
                    if (installedTag != null && Format.sameRelease(installedTag, release.tag)) {
                        showInfo("You're up to date", "${ref.slug} ${release.displayTag} is the latest release and it's what you installed.")
                        return@post
                    }
                    val note = installedTag?.let { "New release ${release.displayTag} (you installed ${DisplayText.sanitize(it, DisplayText.TAG)})." }
                    modal = null
                    push(Screen.Release(ref, release, expectedPackage, note))
                }
            } catch (e: Throwable) {
                main.post {
                    if (gen != generation) return@post
                    showError("Couldn't get the release", describeError(e)) { modal = null; render() }
                }
            }
        }
    }

    private fun releaseScreen(s: Screen.Release): View {
        val body = vertical(this, 14)
        body.addView(text(s.ref.slug, 28f, Kit.TEXT, Kit.medium))
        body.addView(text("Latest release: ${s.release.displayTag}" + if (s.release.name != s.release.displayTag) " — ${s.release.name}" else "",
            16f, Kit.TEXT_2))
        s.note?.let { body.addView(callout(it, Kit.ACCENT, Kit.OK_BG)) }
        val ranked = AbiPicker.rank(s.release.apkAssets, Build.SUPPORTED_ABIS.toList()) { it.name }
        val hidden = s.release.apkAssets.size - ranked.size
        if (ranked.isEmpty()) {
            body.addView(callout(
                if (s.release.apkAssets.isEmpty()) "This release has no .apk file to install."
                else "None of this release's .apk files are built for this TV's processor (${Build.SUPPORTED_ABIS.firstOrNull()}).",
                Kit.WARN, Kit.WARN_BG,
            ))
        }
        ranked.forEachIndexed { i, asset ->
            val row = horizontal(this, 14)
            row.addView(iconTile(getDrawable(R.drawable.ic_release), 44))
            val mid = vertical(this, 4)
            val tr = horizontal(this, 8)
            tr.addView(text(DisplayText.sanitize(asset.name, DisplayText.NAME), 17f, Kit.TEXT, Kit.medium).apply { maxLines = 1 })
            if (i == 0) tr.addView(chip("Best for this TV", Kit.ON_ACCENT, Kit.ACCENT))
            mid.addView(tr)
            mid.addView(text(
                Format.size(asset.size) + " · " + if (asset.sha256 != null) "checksum published (will be verified)" else "no checksum published",
                14f, Kit.TEXT_2,
            ))
            row.addView(mid, lp(0, weight = 1f))
            val v = glass(row, 18, 12) {
                startDownload(DownloadJob.fromGithub(s.ref, s.release, asset).copy(expectedPackage = s.expectedPackage))
            }
            if (i == 0) v.primaryFocus()
            body.addView(v, lp())
        }
        if (hidden > 0) body.addView(statusLine("$hidden other build(s) for different devices are hidden."))
        return page("GitHub", body)
    }

    // ---------------------------------------------------------------- installed list

    private fun installedScreen(): View {
        val body = vertical(this, 14)
        val title = horizontal(this, 14)
        title.addView(iconTile(getDrawable(R.drawable.ic_installed), 44))
        title.addView(text("Installed by H02 Store", 28f, Kit.TEXT, Kit.light))
        body.addView(title)
        val records = files.installed
        if (records.isEmpty()) {
            body.addView(statusLine("Apps you install with H02 Store will appear here."))
            body.addView(button("Back") { onBackPressedCompat() }.primaryFocus())
            return page("Installed", body)
        }
        body.addView(statusLine("Update checks never install anything by themselves: you'll review and confirm every update."))
        records.forEachIndexed { i, r ->
            val info = inspector.installedInfo(r.packageName)
            val row = horizontal(this, 14)
            val icon = try { if (info != null) packageManager.getApplicationIcon(r.packageName) else null } catch (_: Exception) { null }
            row.addView(iconTile(icon, 48, r.label))
            val mid = vertical(this, 4)
            mid.addView(text(r.label, 18f, Kit.TEXT, Kit.medium))
            val src = when (r.source) {
                InstalledRecord.Source.FDROID -> "F-Droid"
                InstalledRecord.Source.GITHUB -> "GitHub · ${r.ref}"
                InstalledRecord.Source.LINK -> "Link"
            }
            val ver = info?.let { DisplayText.sanitize(it.versionName, ReviewModel.VERSION_MAX).ifEmpty { "?" } } ?: "not installed"
            mid.addView(text("$src · $ver", 14f, if (info == null) Kit.WARN else Kit.TEXT_2))
            row.addView(mid, lp(0, weight = 1f))
            if (info != null) {
                val open = button("Open") { openApp(r.packageName) }
                if (i == 0) open.primaryFocus()
                row.addView(open)
                row.addView(button("Check update") { checkUpdate(r) })
                row.addView(button("Uninstall", ButtonKind.DANGER) { uninstall(r.packageName) })
            } else {
                val rm = button("Remove from list") {
                    files.installed = InstalledRegistry.remove(files.installed, r.packageName)
                    render()
                }
                if (i == 0) rm.primaryFocus()
                row.addView(rm)
            }
            body.addView(glass(row, 18, 12), lp())
        }
        return page("Installed", body)
    }

    private fun openApp(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            showInfo("Can't open this app", "This app has no screen to open from a TV remote.")
            return
        }
        try { startActivity(intent) } catch (_: Exception) {
            showInfo("Can't open this app", "Android couldn't start it.")
        }
    }

    private fun uninstall(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        } catch (_: Exception) {
            showInfo("Can't uninstall from here", "Open Settings › Apps on the TV to remove this app.")
        }
    }

    private fun checkUpdate(r: InstalledRecord) {
        val info = inspector.installedInfo(r.packageName)
        val installedCode = info?.let { ApkInspector.versionCodeOf(it) } ?: -1
        when (r.source) {
            InstalledRecord.Source.FDROID -> {
                val c = catalog ?: fdroid.cached()
                if (c == null || FdroidCatalogCodec.isStale(c.savedAtMillis, System.currentTimeMillis())) {
                    catalog = c
                    showInfo("Catalog needs a refresh", "Open F-Droid once to refresh the catalog (at most daily), then check again.",
                        actionLabel = "Open F-Droid") { modal = null; push(Screen.Fdroid) }
                    return
                }
                catalog = c
                val app = c.apps.firstOrNull { it.packageName == r.packageName }
                when {
                    app == null -> showInfo("Not in the catalog", "F-Droid doesn't currently offer a version of this app for this TV.")
                    app.versionCode > installedCode -> {
                        modal = null
                        push(Screen.FdroidDetail(app, "Update available: ${DisplayText.sanitize(info?.versionName, ReviewModel.VERSION_MAX).ifEmpty { "?" }} → ${app.versionName}"))
                    }
                    else -> showInfo("You're up to date", "${r.label} ${DisplayText.sanitize(info?.versionName, ReviewModel.VERSION_MAX)} is the newest version on F-Droid for this TV.")
                }
            }
            InstalledRecord.Source.GITHUB -> {
                val ref = GithubRepoRef.parse(r.ref)
                if (ref == null) showInfo("Can't check", "The saved project name is invalid.")
                else lookupRelease(ref, expectedPackage = r.packageName, installedTag = r.version)
            }
            InstalledRecord.Source.LINK -> showInfo(
                "Links can't be checked automatically",
                "A plain link doesn't say whether there is a newer version. You can download it again and review it; " +
                    "the review screen shows whether it is newer than what's installed.",
                actionLabel = "Download again",
            ) {
                if (UrlPolicy.isAllowedApkUrl(r.ref)) startDownload(DownloadJob.fromLink(r.ref).copy(expectedPackage = r.packageName))
            }
        }
    }

    // ---------------------------------------------------------------- permission

    private fun permissionScreen(job: DownloadJob): View {
        val body = vertical(this, 16)
        body.addView(text("One-time permission needed", 28f, Kit.TEXT, Kit.light))
        body.addView(text(
            "Android needs your OK before H02 Store can hand apps to the system installer. This doesn't let H02 Store install " +
                "anything silently: you will still review every app here, and Android will ask you to confirm each install.",
            16f, Kit.TEXT_2,
        ))
        body.addView(text("1. Choose “Open settings”.\n2. Turn on “Allow from this source” (or “Install unknown apps”) for H02 Store.\n3. Press BACK to return here.",
            16f, Kit.TEXT))
        val note = statusLine("", Kit.WARN).apply { visibility = View.GONE }
        val row = horizontal(this, 14)
        row.addView(button("Open settings", ButtonKind.PRIMARY) {
            if (!Installer.openUnknownSourcesSettings(this)) {
                note.text = "This TV doesn't offer that settings screen. Use the computer command below instead."
                note.visibility = View.VISIBLE
            }
        }.primaryFocus())
        row.addView(button("I've allowed it — continue") {
            if (Installer.canRequestInstalls(this)) {
                stack.removeAt(stack.size - 1)
                startDownload(job)
            } else {
                note.text = "Still not allowed. Turn on the setting for H02 Store, then try again."
                note.visibility = View.VISIBLE
            }
        })
        body.addView(row)
        body.addView(note)
        return page("Permission", body)
    }

    // ---------------------------------------------------------------- download → review → install

    private fun startDownload(job: DownloadJob) {
        if (!online()) { showOffline(); return }
        if (!Installer.canRequestInstalls(this)) { push(Screen.Permission(job)); return }
        discardPending()
        val gen = ++generation
        val cancel = AtomicBoolean(false).also { cancelFlag = it }
        val check = if (job.published is HashExpectation.Sha256) "will be verified against the published checksum"
        else "hashed while downloading (no checksum published)"
        val (view, bar, label) = progressView("Downloading ${job.title}",
            hostOf(job.url)?.let { "From $it · secure https · $check" } ?: "") { abortDownload(cancel) }
        showModal(view) { abortDownload(cancel) }
        var lastUi = 0L
        io.execute {
            var downloaded: ApkFetch.Result? = null
            try {
                val d = downloader.download(job.url, { u, hop -> job.hopAllowed(u, hop) }, job.published, job.declaredSize, { cancel.get() }) { done, total ->
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUi > 150 || done == total) {
                        lastUi = now
                        main.post { if (gen == generation) updateProgress(bar, label, done, total) }
                    }
                }
                downloaded = d
                val inspected = inspector.inspect(d.file, d.sha256, d.hashVerified, job.expectedPackage, job.expectedSigner)
                main.post {
                    if (gen != generation || isDestroyed) { d.file.delete(); return@post }
                    pendingFile = d.file
                    pendingJob = job
                    pendingInspected = inspected
                    showConfirm(job, inspected)
                }
            } catch (e: Throwable) {
                downloaded?.file?.delete()
                main.post {
                    if (gen != generation) return@post
                    if (cancel.get() || e is dev.tvlauncher.store.core.DownloadLimiter.Cancelled) {
                        modal = null
                        render()
                    } else {
                        showError("Download failed", describeError(e)) { modal = null; render() }
                    }
                }
            }
        }
    }

    private fun abortDownload(cancel: AtomicBoolean) {
        cancel.set(true) // the worker notices within one read, deletes the partial file and stops
        generation++
        modal = null
        render()
    }

    /**
     * The review screen. Left: identity, blockers/warnings, facts and the actions (focus starts on Cancel, so
     * installing takes a deliberate move + OK). Right: every requested permission, riskiest first, highlighted.
     */
    private fun showConfirm(job: DownloadJob, ins: Inspected) {
        val f = ins.facts
        val review: Review = InstallReview.evaluate(f)
        val left = vertical(this, 14)

        val head = horizontal(this, 18)
        head.addView(iconTile(ins.icon, 72, ins.label))
        val t = vertical(this, 6)
        t.addView(text(ins.label, 28f, Kit.TEXT, Kit.medium).apply { maxLines = 2 })
        t.addView(text(f.packageName ?: "unknown package", 14f, Kit.TEXT_3, Kit.mono))
        val chips = horizontal(this, 8)
        chips.addView(when (review.installedState) {
            InstalledState.INSTALLED -> chip("Update", Kit.ACCENT_2, 0x265B8CFF)
            InstalledState.NOT_INSTALLED -> chip("New install", Kit.TEXT_2, Kit.SURFACE_2)
            InstalledState.UNKNOWN -> chip("Install status unknown", Kit.WARN, Kit.WARN_BG)
        })
        when (f.tvReady) {
            true -> chips.addView(chip("TV-ready", Kit.ACCENT, Kit.OK_BG))
            false -> chips.addView(chip("Not designed for TV", Kit.WARN, Kit.WARN_BG))
            null -> chips.addView(chip("TV support unknown", Kit.TEXT_2, Kit.SURFACE_2))
        }
        if (ins.hashVerified) chips.addView(chip("Checksum verified", Kit.ACCENT, Kit.OK_BG))
        t.addView(chips)
        head.addView(t, lp(0, weight = 1f))
        left.addView(head, lp())

        for (b in review.blockers) {
            val big = b == InstallReview.SIGNATURE_DIFFERS
            left.addView(callout(b, Kit.TEXT, if (big) Kit.DANGER else Kit.DANGER_BG, if (big) 20f else 16f, bold = true))
        }
        for (w in review.warnings) left.addView(callout(w, Kit.WARN, Kit.WARN_BG, 14f))

        val model = ReviewModel.build(f)
        val installedVer = if (review.isUpdate) inspector.installedInfo(f.packageName ?: "")?.versionName
            ?.let { DisplayText.sanitize(it, ReviewModel.VERSION_MAX) } else null
        left.addView(facts(listOfNotNull(
            "Version" to model.versionText,
            installedVer?.let { "Installed now" to it },
            "Size" to Format.size(ins.fileSize),
            "SHA-256" to Sha256Stream.short(ins.sha256) + if (ins.hashVerified) "  ✓ matches source" else "",
            "Signer (current)" to when (f.signers.size) {
                0 -> "none"
                1 -> SignerCompare.shortFingerprint(f.signers.first())
                else -> "${f.signers.size} signers: " + f.signers.joinToString(", ") { SignerCompare.shortFingerprint(it) }
            },
            "From" to (hostOf(job.url) ?: job.url),
        )), lp())

        // Sensitive capabilities stay visible right next to the action (up to 5, then "and N more").
        if (model.leftCapabilities.isNotEmpty()) {
            val capList = vertical(this, 4)
            for (c in model.leftCapabilities) {
                val color = if (c.severity == ComponentRisk.Severity.HIGH) Kit.DANGER else Kit.WARN
                capList.addView(text("▲  " + c.text, 15f, color, Kit.medium))
            }
            if (model.leftMore > 0) capList.addView(text("and ${model.leftMore} more (see the list on the right)", 13f, Kit.TEXT_2))
            capList.setPadding(dp(16), dp(10), dp(16), dp(10))
            capList.background = rounded(Kit.DANGER_BG, Kit.RADIUS_SMALL)
            left.addView(capList, lp())
        }

        val actions = horizontal(this, 14)
        val install = button(if (review.isUpdate) "Install update" else "Install", ButtonKind.PRIMARY) { confirmInstall() }
        install.isEnabled = review.canInstall
        install.isFocusable = review.canInstall
        val cancel = button("Cancel") { cancelConfirm() }
        actions.addView(install)
        actions.addView(cancel)
        actions.addView(text(if (review.canInstall) "Android will also ask you to confirm." else "This app can't be installed.",
            14f, Kit.TEXT_3))
        left.addView(actions)
        cancel.primaryFocus()

        val right = vertical(this, 8)
        // Our own summary first, then capabilities (they never appear as permissions), then permissions.
        right.addView(text(
            if (review.sensitiveCount > 0) InstallReview.summary(review.sensitiveCount) else "No sensitive capabilities or permissions",
            15f, if (review.sensitiveCount > 0) Kit.WARN else Kit.TEXT_2, Kit.medium,
        ))
        for (c in model.sensitiveCapabilities) {
            val color = if (c.severity == ComponentRisk.Severity.HIGH) Kit.DANGER else Kit.WARN
            val r = vertical(this, 2)
            r.addView(text("▲  " + c.text, 15f, color, Kit.medium))
            r.addView(text("Declares a component protected by ${c.key}", 11f, Kit.TEXT_3, Kit.mono).apply { maxLines = 1; setPadding(dp(20), 0, 0, 0) })
            right.addView(r, lp())
        }
        if (model.infoCapabilities.isNotEmpty()) {
            right.addView(text("Also declares: " + model.infoCapabilities.joinToString("; ") { it.text.lowercase() }, 13f, Kit.TEXT_3))
        }
        right.addView(text(
            if (model.totalPermissions == 0) "Requests no permissions" else "Permissions requested by the app (${model.totalPermissions})",
            17f, Kit.TEXT, Kit.medium,
        ).apply { setPadding(0, dp(6), 0, 0) })
        // APK-supplied rows: fixed neutral bullet, sanitised, recognised names only — they can't mimic our lines.
        for (p in model.permissions) {
            val color = when (p.level) {
                PermissionRisk.Level.SPECIAL -> Kit.DANGER
                PermissionRisk.Level.DANGEROUS -> Kit.WARN
                PermissionRisk.Level.NORMAL -> Kit.TEXT_2
            }
            val r = vertical(this, 2)
            r.addView(text(APK_BULLET + p.label, 14f, color,
                if (p.level == PermissionRisk.Level.NORMAL) Kit.regular else Kit.medium))
            r.addView(text(p.name, 11f, Kit.TEXT_3, Kit.mono).apply { maxLines = 1; setPadding(dp(20), 0, 0, 0) })
            right.addView(r, lp())
        }
        if (model.permissionsMore > 0) right.addView(text("and ${model.permissionsMore} more permissions", 13f, Kit.TEXT_2))
        if (model.unrecognizedPermissions > 0) {
            right.addView(text("Unrecognized permission name" + (if (model.unrecognizedPermissions > 1) "s" else "") +
                " (${model.unrecognizedPermissions}) not shown", 13f, Kit.WARN))
        }
        // Focusable so the remote can move into the list and scroll it with up/down.
        val permScroll = ScrollView(this).apply {
            isFocusable = true
            isVerticalScrollBarEnabled = true
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(Kit.SURFACE_FOCUS, Kit.RADIUS, Kit.ACCENT, 2))
                addState(intArrayOf(), rounded(Kit.SURFACE, Kit.RADIUS, Kit.STROKE, 1))
            }
            addView(right)
        }

        val leftScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(left)
        }
        val columns = horizontal(this, 24)
        columns.gravity = Gravity.TOP
        columns.addView(leftScroll, lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.35f))
        columns.addView(permScroll, lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        showModal(page("Review", columns, scroll = false)) { cancelConfirm() }
    }

    private fun cancelConfirm() {
        discardPending()
        modal = null
        render()
    }

    private fun confirmInstall() {
        val file = pendingFile ?: return
        val ins = pendingInspected ?: return
        val job = pendingJob ?: return
        val pkg = ins.facts.packageName ?: return
        if (!InstallReview.evaluate(ins.facts).canInstall) return
        if (!Installer.canRequestInstalls(this)) { discardPending(); push(Screen.Permission(job)); return }
        pendingFile = null
        pendingInspected = null
        pendingJob = null
        val inst = Installing(file, ins, job)
        installing = inst
        // Hiding this screen doesn't cancel anything: a late result from Android is still shown and recorded.
        val (view, bar, _) = progressView("Installing ${ins.label}", "Handing the app to Android's installer. Confirm on the next screen.") {
            modal = null; render()
        }
        bar.progress = -1f
        showModal(view) { modal = null; render() }
        val token = UUID.randomUUID().toString()
        io.execute {
            try {
                Installer.commitSession(this, file, pkg, token)
            } catch (e: Throwable) {
                main.post {
                    if (installing !== inst) { file.delete(); return@post }
                    val cause = (e as? Installer.StageFailure)?.cause ?: e
                    val fallbackOk = e is Installer.StageFailure && InstallFallbackPolicy.allowed(e.stage, cause)
                    val result = if (fallbackOk) Installer.systemInstallerFallback(this, file) else null
                    if (result == Installer.FallbackResult.STARTED) {
                        fallbackStartedAt = SystemClock.uptimeMillis()
                    } else {
                        installing = null
                        file.delete()
                        if (result == Installer.FallbackResult.NO_SYSTEM_INSTALLER) {
                            showError("No system installer found", "This TV has no system app installer H02 Store can hand the app to, so nothing was installed.") { modal = null; render() }
                        } else {
                            showError("Couldn't start the installer", describeError(cause)) { modal = null; render() }
                        }
                    }
                }
                return@execute
            }
            file.delete() // the bytes now live in the system's session
            main.post { scheduleInstallTimeout(inst) }
        }
    }

    /**
     * If Android's confirmation never resolves, give up after 12 minutes (the status token stays valid for 14):
     * abandon the session and clean up — but first check whether the app did get installed.
     */
    private fun scheduleInstallTimeout(inst: Installing) {
        inst.committedAt = System.currentTimeMillis()
        main.postDelayed({
            if (installing === inst) {
                installing = null
                Installer.abandonCurrent(this)
                ApkDownloader.cleanStale(this)
                if (!isDestroyed) {
                    if (resolveOutcome(inst, InstallOutcomeResolver.Reported.TIMED_OUT) == InstallOutcomeResolver.Reported.SUCCESS) {
                        reportSuccess(inst, null)
                    } else {
                        showInfo("Install didn't finish", "Android's confirmation wasn't answered, so the install was cancelled. You can try again any time.")
                    }
                }
            }
        }, ReceiverTokenPolicy.SESSION_TIMEOUT_MILLIS)
    }

    private fun resolveOutcome(inst: Installing, reported: InstallOutcomeResolver.Reported): InstallOutcomeResolver.Reported {
        val pkg = inst.inspected.facts.packageName ?: return reported
        val now = inspector.installedInfo(pkg)
        return InstallOutcomeResolver.resolve(
            reported = reported,
            expectedVersionCode = inst.inspected.facts.versionCode,
            previousVersionCode = inst.inspected.facts.installedVersionCode,
            nowVersionCode = now?.let { ApkInspector.versionCodeOf(it) },
            lastUpdateTimeMillis = now?.lastUpdateTime,
            committedAtMillis = inst.committedAt,
        )
    }

    private fun reportSuccess(inst: Installing?, packageName: String?) {
        val ins = inst?.inspected
        val pkg = packageName ?: ins?.facts?.packageName
        if (pkg != null && inst != null) recordInstall(pkg, ins?.label ?: pkg, inst.job, ins?.facts?.versionCode ?: 0)
        showSuccess(ins?.label ?: pkg ?: "App", pkg)
    }

    /** Status from the system installer (main thread). */
    override fun onInstallStatus(status: Int, message: String?, packageName: String?, confirm: Intent?): Boolean {
        if (status == InstallStatusMapper.STATUS_PENDING_USER_ACTION) {
            if (confirm == null) return false
            return try { startActivity(confirm); true } catch (_: Exception) { false }
        }
        val inst = installing
        installing = null
        inst?.file?.delete()
        val outcome = InstallStatusMapper.map(status, message)
        when (outcome.kind) {
            InstallStatusMapper.Kind.SUCCESS -> reportSuccess(inst, packageName)
            InstallStatusMapper.Kind.CANCELLED -> {
                // Never claim "cancelled" for an app that is in fact installed.
                if (inst != null && resolveOutcome(inst, InstallOutcomeResolver.Reported.CANCELLED) == InstallOutcomeResolver.Reported.SUCCESS) {
                    reportSuccess(inst, packageName)
                } else { modal = null; render() }
            }
            else -> showError(outcome.title, outcome.message, DisplayText.sanitize(outcome.detail, 200)) { modal = null; render() }
        }
        return true
    }

    private fun recordInstall(pkg: String, label: String, job: DownloadJob, versionCode: Long) {
        if (!InstalledRegistry.isValidPackageName(pkg)) return
        val rec = InstalledRecord(pkg, label, job.source, job.ref, job.releaseTag ?: versionCode.toString(), System.currentTimeMillis())
        files.installed = InstalledRegistry.upsert(files.installed, rec)
    }

    private fun checkFallbackResult() {
        // Called when we come back from the system installer UI (legacy ACTION_INSTALL_PACKAGE fallback).
        if (SystemClock.uptimeMillis() - fallbackStartedAt < 500) return
        fallbackStartedAt = 0
        val inst = installing ?: return
        installing = null
        inst.file.delete()
        val ins = inst.inspected
        val pkg = ins.facts.packageName ?: return
        val now = inspector.installedInfo(pkg)
        if (now != null && ApkInspector.versionCodeOf(now) == ins.facts.versionCode) {
            recordInstall(pkg, ins.label, inst.job, ins.facts.versionCode)
            showSuccess(ins.label, pkg)
        } else {
            showInfo("Not installed", "The installer closed without installing the app. You can try again any time.")
        }
    }

    private fun discardPending() {
        pendingFile?.delete()
        pendingFile = null
        pendingInspected = null
        pendingJob = null
    }

    // ---------------------------------------------------------------- shared views

    private fun facts(rows: List<Pair<String, String>>): View {
        val col = vertical(this, 8)
        for ((k, v) in rows) {
            val r = horizontal(this, 16)
            r.addView(text(k, 14f, Kit.TEXT_3), LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT))
            r.addView(text(v, 15f, Kit.TEXT, if (k.startsWith("SHA") || k.startsWith("Signer")) Kit.mono else Kit.regular),
                lp(0, weight = 1f))
            col.addView(r, lp())
        }
        return glass(col, 20, 16)
    }

    private data class Progress(val view: View, val bar: ProgressBarView, val label: TextView)

    private fun progressView(title: String, subtitle: String, onCancel: () -> Unit): Progress {
        val body = vertical(this, 18)
        body.gravity = Gravity.CENTER_VERTICAL
        body.addView(text(title, 28f, Kit.TEXT, Kit.light))
        if (subtitle.isNotEmpty()) body.addView(text(subtitle, 15f, Kit.TEXT_2))
        val bar = ProgressBarView(this)
        body.addView(bar, lp())
        val label = text("Connecting…", 15f, Kit.TEXT_2)
        body.addView(label)
        body.addView(button("Cancel") { onCancel() }.primaryFocus())
        val page = page(null, body, scroll = false)
        return Progress(page, bar, label)
    }

    private fun updateProgress(bar: ProgressBarView, label: TextView, done: Long, total: Long) {
        val pct = Format.percent(done, total)
        bar.progress = if (pct < 0) -1f else done.toFloat() / total
        label.text = if (pct < 0) Format.size(done) else "${Format.size(done)} of ${Format.size(total)} · $pct%"
    }

    private fun showSuccess(label: String, pkg: String?) {
        val body = vertical(this, 18)
        body.gravity = Gravity.CENTER_VERTICAL
        body.addView(text("✓", 54f, Kit.ACCENT, Kit.light))
        body.addView(text("$label is installed", 30f, Kit.TEXT, Kit.light))
        body.addView(text("You'll find it on your home screen. Manage it later under “Installed by H02 Store”.", 16f, Kit.TEXT_2))
        val row = horizontal(this, 14)
        if (pkg != null) row.addView(button("Open app", ButtonKind.PRIMARY) { openApp(pkg) }.primaryFocus())
        row.addView(button("Done") { modal = null; popTo { it == Screen.Home } })
        body.addView(row)
        showModal(page(null, body, scroll = false)) { modal = null; render() }
    }

    private fun showError(title: String, message: String, detail: String? = null, onDone: () -> Unit) {
        val body = vertical(this, 16)
        body.gravity = Gravity.CENTER_VERTICAL
        body.addView(text(title, 30f, Kit.TEXT, Kit.light))
        body.addView(text(message, 17f, Kit.TEXT_2))
        if (!detail.isNullOrBlank()) body.addView(text("Details: $detail", 13f, Kit.TEXT_3, Kit.mono))
        body.addView(button("Back", ButtonKind.PRIMARY) { onDone() }.primaryFocus())
        showModal(page(null, body, scroll = false)) { onDone() }
    }

    private fun showInfo(title: String, message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        val body = vertical(this, 16)
        body.gravity = Gravity.CENTER_VERTICAL
        body.addView(text(title, 30f, Kit.TEXT, Kit.light))
        body.addView(text(message, 17f, Kit.TEXT_2))
        val row = horizontal(this, 14)
        if (actionLabel != null && action != null) row.addView(button(actionLabel, ButtonKind.PRIMARY) { action() }.primaryFocus())
        row.addView(button("Back") { modal = null; render() }.apply { if (actionLabel == null) primaryFocus() })
        body.addView(row)
        showModal(page(null, body, scroll = false)) { modal = null; render() }
    }

    private fun showOffline() = showInfo("You're offline", "Connect the TV to the internet (Settings › Network) and try again.")

    private fun onBackPressedCompat() {
        @Suppress("DEPRECATION") onBackPressed()
    }

    // ---------------------------------------------------------------- helpers

    private fun online(): Boolean {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return true
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true // if we can't tell, let the request try and fail with a clear message
        }
    }

    private fun hostOf(url: String): String? = (UrlPolicy.check(url, 8192) as? UrlPolicy.Verdict.Allowed)?.host

    private fun ago(t: Long): String {
        val mins = (System.currentTimeMillis() - t) / 60000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 48 * 60 -> "${mins / 60} h ago"
            else -> "${mins / (24 * 60)} days ago"
        }
    }

    private fun describeError(e: Throwable): String = when (e) {
        is GithubRepository.FriendlyError -> e.message ?: "GitHub error."
        else -> ApkDownloader.describe(e)
    }

    companion object {
        private const val FOCUS_TAG = "primary-focus"
        /** Fixed, dimmed prefix for every APK-supplied row. */
        private const val APK_BULLET = "·  "
    }
}

/** Version string without enabling BuildConfig generation. */
internal object BuildConfigLite {
    const val VERSION = "0.1.0"
}
