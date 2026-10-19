package dev.tvlauncher.safemode

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** The stock TV launcher, framework only. Every call is guarded and never throws. */
object StockLauncher {
    /** Its HOME activity, only when it is installed, enabled and still resolves HOME; else null. */
    fun intent(context: Context): Intent? = try {
        val pm = context.packageManager
        val pkg = SafeModeList.STOCK_LAUNCHER
        val info = try { pm.getApplicationInfo(pkg, 0) } catch (e: PackageManager.NameNotFoundException) { null }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
        val ai = pm.resolveActivity(home, 0)?.activityInfo
        if (SafeModeList.stockAvailable(info != null, info?.enabled == true, ai != null)) home.setClassName(pkg, ai!!.name) else null
    } catch (e: Throwable) {
        null
    }

    fun start(context: Context): Boolean = try {
        val i = intent(context)
        if (i == null) false else { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
    } catch (e: Throwable) {
        false
    }

    /** Its label as the TV shows it (works while disabled), else [SafeModeList.STOCK_LABEL_FALLBACK]. */
    fun label(context: Context): String = try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getApplicationInfo(SafeModeList.STOCK_LAUNCHER, PackageManager.GET_DISABLED_COMPONENTS)
        pm.getApplicationLabel(info).toString().ifBlank { SafeModeList.STOCK_LABEL_FALLBACK }
    } catch (e: Throwable) {
        SafeModeList.STOCK_LABEL_FALLBACK
    }
}
