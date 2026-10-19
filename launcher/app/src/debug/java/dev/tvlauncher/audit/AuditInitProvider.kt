package dev.tvlauncher.audit

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Debug-only, non-exported provider whose only job is to run early in process start and register
 * [AuditTracker] (the system creates providers before Application.onCreate). It serves no data.
 */
class AuditInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { AuditTracker.install(it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/** Remembers the resumed (else the last started) activity. Weak references only. */
object AuditTracker : Application.ActivityLifecycleCallbacks {
    @Volatile private var installed = false
    @Volatile private var resumed: WeakReference<Activity>? = null
    @Volatile private var started: WeakReference<Activity>? = null

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(this)
    }

    fun current(): Activity? = (resumed?.get() ?: started?.get())?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) { resumed = WeakReference(activity) }
    override fun onActivityPaused(activity: Activity) { if (resumed?.get() === activity) resumed = null }
    override fun onActivityStarted(activity: Activity) { started = WeakReference(activity) }
    override fun onActivityStopped(activity: Activity) { if (started?.get() === activity) started = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
