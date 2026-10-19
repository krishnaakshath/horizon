package dev.tvlauncher.safemode

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
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
            try {
                buildMinimal()
            } catch (e2: Throwable) {
                // Even the minimal screen failed: count it, leave for the TV's Settings app, go.
                try { SafeMode.guard(this).recordSafeModeCrash(e2.javaClass.simpleName.ifEmpty { "Throwable" }) } catch (e3: Throwable) { }
                try { SettingsEscape.open(this) } catch (e3: Throwable) { }
                try { finish() } catch (e3: Throwable) { }
            }
        }
    }

    private fun buildList() {
        val root = column()
        val last = try { SafeMode.guard(this).history.lastException } catch (e: Throwable) { null }
        header(root, SafeModeList.subtitle(last, minimal = false))
        list = ListView(this).apply {
            itemsCanFocus = false
            divider = null
            dividerHeight = 0
            isVerticalScrollBarEnabled = false
            selector = rounded(FOCUS)
            setDrawSelectorOnTop(false)
            setOnItemClickListener { _, _, position, _ -> rows.getOrNull(position)?.let(::open) }
        }
        root.addView(list, LinearLayout.LayoutParams(px(LIST_W), 0, 1f).apply { topMargin = px(24) })
        setContentView(root)
    }

    /** Title and one calm line; framework views only. */
    private fun header(root: LinearLayout, sub: String) {
        root.addView(text(SafeModeList.TITLE, 30f, TEXT))
        root.addView(text(sub, 18f, GREY).apply { setPadding(0, px(8), 0, 0) })
    }

    /** Minimal screen: framework buttons only, no app list, system Settings first. */
    private fun buildMinimal() {
        list = null
        val root = column()
        header(root, SafeModeList.subtitle(null, minimal = true))
        var first: View? = null
        for (row in SafeModeList.minimal(StockLauncher.intent(this) != null, StockLauncher.label(this))) {
            val v: View = if (row is SafeModeList.Row.Notice) {
                text(row.text, 18f, GREY).apply { setPadding(px(16), px(8), px(16), px(8)) }
            } else {
                rowView(row).apply {
                    isFocusable = true
                    isClickable = true
                    background = StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), rounded(FOCUS))
                        addState(intArrayOf(), rounded(Color.TRANSPARENT))
                    }
                    setOnClickListener { open(row) }
                }
            }
            if (first == null && v.isFocusable) first = v
            root.addView(v, LinearLayout.LayoutParams(px(LIST_W), -2).apply { topMargin = px(if (first === v) 24 else 4) })
        }
        setContentView(root)
        first?.requestFocus()
    }

    private fun load() {
        val l = list ?: return
        val stock = StockLauncher.intent(this)
        var failed = false
        val leanback = query(Intent.CATEGORY_LEANBACK_LAUNCHER) { failed = true }
        val launcher = query(Intent.CATEGORY_LAUNCHER) { failed = true }
        rows = SafeModeList.build(leanback, launcher, packageName, stock != null, appsFailed = failed)
        l.adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = rows[position]
                return ((convertView as? TextView) ?: rowView(row)).apply {
                    text = SafeModeList.label(row)
                    setTextColor(colors(row))
                }
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
            SafeModeList.Row.StockLauncher -> StockLauncher.start(this)
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

    /** It stands in for the home screen: Back does nothing. */
    @Deprecated("Safe mode never exits on Back")
    override fun onBackPressed() = Unit

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BASE)
        setPadding(px(48), px(27), px(48), px(27))
    }

    /** One list row: 20 sp, 56 dp tall, dark text on the white focus/selection fill. */
    private fun rowView(row: SafeModeList.Row) = text(SafeModeList.label(row), 20f, TEXT).apply {
        minHeight = px(56)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(16), px(8), px(16), px(8))
        setTextColor(colors(row))
    }

    private fun colors(row: SafeModeList.Row): ColorStateList {
        val rest = when {
            row is SafeModeList.Row.Notice -> GREY
            SafeModeList.isAction(row) -> ACCENT
            else -> TEXT
        }
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(DARK, DARK, rest)
        )
    }

    private fun rounded(fill: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = px(14).toFloat()
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
        // Plain literals on purpose: safe mode never touches the launcher Theme or panel code.
        private const val BASE = 0xFF0B0D12.toInt()
        private const val TEXT = 0xFFF5F7FA.toInt()
        private const val DARK = 0xFF0B0D12.toInt()
        private const val FOCUS = 0xFFF5F7FA.toInt()
        private const val GREY = 0xFFA7B0C0.toInt()
        private const val ACCENT = 0xFF8AB4FF.toInt()
        private const val LIST_W = 640
    }
}
