package dev.tvlauncher.safemode

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import dev.tvlauncher.HomeActivity
import dev.tvlauncher.apps.AppEntry

/**
 * Deliberately tiny fallback launcher shown after repeated home-screen crashes (see [CrashGuard]).
 * Framework widgets and an explicit framework theme only (no launcher Theme, fonts, appearance or
 * sound code); no animations, listeners, Spotlight or splash. It runs in its own task and is not a
 * HOME candidate; HomeActivity redirects here while the guard is tripped.
 *
 * It must never become a trap: with [EXTRA_MINIMAL] (safe mode crashed before, see [EscapePlan]),
 * or when building the list fails, it shows the minimal screen (system Settings first, the stock
 * launcher or how to re-enable it, and "Try normal launcher again"). If it crashes anyway, the
 * next HOME press goes straight to the TV's Settings app.
 */
class SafeModeActivity : Activity() {
    private var rows: List<SafeModeList.Row> = emptyList()
    private var list: ListView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        show(intent)
        try { overridePendingTransition(0, 0) } catch (e: Throwable) { }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(i: Intent?) {
        val minimal = try { i?.getBooleanExtra(EXTRA_MINIMAL, false) == true } catch (e: Throwable) { true }
        try {
            if (minimal) buildMinimal() else { buildList(); load() }
        } catch (e: Throwable) {
            try { buildMinimal() } catch (e2: Throwable) { }
        }
    }

    private fun buildList() {
        val root = column()
        root.addView(text(SafeModeList.HEADER, 30f, Color.WHITE))
        val last = try { SafeMode.guard(this).history.lastException } catch (e: Throwable) { null }
        root.addView(text("Last error: ${last ?: "unknown"}. Pick an app, or try the normal launcher again.", 18f, GREY)
            .apply { setPadding(0, px(8), 0, px(16)) })
        list = ListView(this).apply {
            itemsCanFocus = false
            divider = null
            setOnItemClickListener { _, _, position, _ -> rows.getOrNull(position)?.let(::open) }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    /** Minimal screen: framework buttons only, no app list, system Settings first. */
    private fun buildMinimal() {
        list = null
        val root = column()
        root.addView(text(SafeModeList.HEADER, 30f, Color.WHITE))
        var first: View? = null
        for (row in SafeModeList.minimal(stockLauncherIntent() != null)) {
            val v: View = if (row is SafeModeList.Row.Notice) {
                text(row.text, 18f, GREY)
            } else {
                Button(this).apply {
                    text = SafeModeList.label(row)
                    setOnClickListener { open(row) }
                }
            }
            if (first == null && v is Button) first = v
            root.addView(v, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(16) })
        }
        setContentView(root)
        first?.requestFocus()
    }

    private fun load() {
        val l = list ?: return
        val stock = stockLauncherIntent()
        var failed = false
        val leanback = query(Intent.CATEGORY_LEANBACK_LAUNCHER) { failed = true }
        val launcher = query(Intent.CATEGORY_LAUNCHER) { failed = true }
        rows = SafeModeList.build(leanback, launcher, packageName, stock != null, appsFailed = failed)
        l.adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                ((convertView as? TextView) ?: text("", 24f, Color.WHITE).apply {
                    setPadding(px(20), px(14), px(20), px(14))
                }).apply {
                    val row = rows[position]
                    text = SafeModeList.label(row)
                    setTextColor(when (row) {
                        is SafeModeList.Row.App -> Color.WHITE
                        is SafeModeList.Row.Notice -> GREY
                        else -> ACCENT
                    })
                }
        }
        l.setSelection(0)
        l.requestFocus()
    }

    private fun open(row: SafeModeList.Row) {
        val ok = when (row) {
            SafeModeList.Row.TryNormal -> {
                try { SafeMode.guard(this).reset() } catch (e: Throwable) { }
                start(Intent(this, HomeActivity::class.java)).also { if (it) finish() }
            }
            SafeModeList.Row.SystemSettings -> SettingsEscape.open(this)
            SafeModeList.Row.StockLauncher -> stockLauncherIntent()?.let(::start) ?: false
            is SafeModeList.Row.App -> start(Intent(Intent.ACTION_MAIN).setClassName(row.app.packageName, row.app.activityClass))
            is SafeModeList.Row.Notice -> true
        }
        if (!ok) try { Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show() } catch (e: Throwable) { }
    }

    private fun start(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Throwable) {
        false
    }

    private fun query(category: String, onError: () -> Unit): List<AppEntry> = try {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            .mapNotNull { it.activityInfo?.let { ai -> AppEntry(ai.packageName, it.loadLabel(packageManager).toString(), ai.name) } }
    } catch (e: Throwable) {
        onError()
        emptyList()
    }

    /** The stock TV launcher's HOME activity, only when it is installed and enabled. */
    private fun stockLauncherIntent(): Intent? = try {
        val pkg = SafeModeList.STOCK_LAUNCHER
        val info = try { packageManager.getApplicationInfo(pkg, 0) } catch (e: PackageManager.NameNotFoundException) { null }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
        val ai = packageManager.resolveActivity(home, 0)?.activityInfo
        if (SafeModeList.stockAvailable(info != null, info?.enabled == true, ai != null)) home.setClassName(pkg, ai!!.name) else null
    } catch (e: Throwable) {
        null
    }

    /** It stands in for the home screen: Back does nothing. */
    @Deprecated("Safe mode never exits on Back")
    override fun onBackPressed() = Unit

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.BLACK)
        setPadding(px(48), px(27), px(48), px(27))
    }

    private fun text(s: String, sp: Float, color: Int) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
    }

    private fun px(dp: Int) = (dp * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        /** Show the minimal screen (no app list). */
        const val EXTRA_MINIMAL = "dev.tvlauncher.safemode.MINIMAL"
        private const val GREY = 0xFFB0B8C4.toInt()
        private const val ACCENT = 0xFF8AB4FF.toInt()
    }
}
