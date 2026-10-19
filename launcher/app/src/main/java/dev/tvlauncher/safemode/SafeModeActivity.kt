package dev.tvlauncher.safemode

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
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
 * Deliberately tiny fallback launcher shown after repeated crashes (see [CrashGuard]). Plain
 * framework views only: no animations, sounds, network/Bluetooth listeners, Spotlight or splash.
 * It is not a HOME candidate itself; HomeActivity redirects here while the guard is tripped.
 */
class SafeModeActivity : Activity() {
    private var rows: List<SafeModeList.Row> = emptyList()
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = px(32)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(px(48), px(27), px(48), px(27))
        }
        root.addView(text(SafeModeList.HEADER, 30f, Color.WHITE))
        val last = SafeMode.guard(this).history.lastException
        root.addView(text("Last error: ${last ?: "unknown"}. Pick an app, or try the normal launcher again.", 18f, GREY)
            .apply { setPadding(0, px(8), 0, pad / 2) })
        list = ListView(this).apply {
            itemsCanFocus = false
            isVerticalScrollBarEnabled = true
            divider = null
            setOnItemClickListener { _, _, position, _ -> rows.getOrNull(position)?.let(::open) }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        overridePendingTransition(0, 0)
        load()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        load()
    }

    private fun load() {
        val stock = stockLauncherIntent()
        rows = SafeModeList.build(query(Intent.CATEGORY_LEANBACK_LAUNCHER), query(Intent.CATEGORY_LAUNCHER), packageName, stock != null)
        list.adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                ((convertView as? TextView) ?: text("", 24f, Color.WHITE).apply {
                    setPadding(px(20), px(14), px(20), px(14))
                }).apply {
                    text = SafeModeList.label(rows[position])
                    setTextColor(if (rows[position] is SafeModeList.Row.App) Color.WHITE else ACCENT)
                }
        }
        list.setSelection(0)
        list.requestFocus()
    }

    private fun open(row: SafeModeList.Row) {
        val ok = when (row) {
            SafeModeList.Row.TryNormal -> {
                SafeMode.guard(this).reset()
                start(Intent(this, HomeActivity::class.java)).also { if (it) finish() }
            }
            SafeModeList.Row.SystemSettings -> start(Intent(Settings.ACTION_SETTINGS))
            SafeModeList.Row.StockLauncher -> stockLauncherIntent()?.let(::start) ?: false
            is SafeModeList.Row.App -> start(Intent(Intent.ACTION_MAIN).setClassName(row.app.packageName, row.app.activityClass))
        }
        if (!ok) Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show()
    }

    private fun start(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) {
        false
    }

    private fun query(category: String): List<AppEntry> = try {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            .mapNotNull { it.activityInfo?.let { ai -> AppEntry(ai.packageName, it.loadLabel(packageManager).toString(), ai.name) } }
    } catch (e: RuntimeException) {
        emptyList()
    }

    /** The stock TV launcher's HOME activity, only when it is installed and enabled. */
    private fun stockLauncherIntent(): Intent? = try {
        val pkg = SafeModeList.STOCK_LAUNCHER
        val info = try { packageManager.getApplicationInfo(pkg, 0) } catch (e: PackageManager.NameNotFoundException) { null }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
        val ai = packageManager.resolveActivity(home, 0)?.activityInfo
        if (SafeModeList.stockAvailable(info != null, info?.enabled == true, ai != null)) home.setClassName(pkg, ai!!.name) else null
    } catch (e: RuntimeException) {
        null
    }

    /** It stands in for the home screen: Back does nothing. */
    @Deprecated("Safe mode never exits on Back")
    override fun onBackPressed() = Unit

    private fun text(s: String, sp: Float, color: Int) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
    }

    private fun px(dp: Int) = (dp * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val GREY = 0xFFB0B8C4.toInt()
        const val ACCENT = 0xFF8AB4FF.toInt()
    }
}
