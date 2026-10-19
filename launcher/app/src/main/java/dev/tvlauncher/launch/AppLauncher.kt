package dev.tvlauncher.launch

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.View
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.component
import dev.tvlauncher.device.DeviceProfile

/**
 * Starts an app. [source] is an opaque anchor (a View at runtime) so the logic stays JVM-testable.
 * Each strategy returns false or throws when it cannot start the app.
 */
interface LaunchBackend {
    fun startWithClipReveal(app: AppEntry, source: Any, durationMs: Long): Boolean
    fun startWithScaleUp(app: AppEntry, source: Any, durationMs: Long): Boolean
    fun startWithFade(app: AppEntry, durationMs: Long): Boolean
}

class AppLauncher(private val profile: DeviceProfile, private val backend: LaunchBackend) {
    /** Never throws; returns false if every strategy failed (e.g. the activity is gone). */
    fun launch(app: AppEntry, sourceView: Any?): Boolean {
        val ms = profile.appOpenMs
        val attempts = ArrayList<() -> Boolean>(3)
        if (sourceView != null) {
            attempts += { backend.startWithClipReveal(app, sourceView, ms) }
            attempts += { backend.startWithScaleUp(app, sourceView, ms) }
        }
        attempts += { backend.startWithFade(app, ms) }
        for (attempt in attempts) {
            try {
                if (attempt()) return true
            } catch (e: RuntimeException) {
                // ActivityNotFoundException, SecurityException, ... -> next strategy
            }
        }
        return false
    }
}

/**
 * Real backend. ActivityOptions has no public duration parameter for clip-reveal, scale-up or
 * the stock custom animation (the system window-animation scale governs them), so [durationMs]
 * is carried through the call for the contract but cannot be enforced here.
 */
class AndroidLaunchBackend(private val context: Context) : LaunchBackend {
    private fun intent(app: AppEntry) =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun startWithClipReveal(app: AppEntry, source: Any, durationMs: Long): Boolean {
        val v = source as View
        val opts = ActivityOptions.makeClipRevealAnimation(v, 0, 0, v.width, v.height)
        context.startActivity(intent(app), opts.toBundle())
        return true
    }

    override fun startWithScaleUp(app: AppEntry, source: Any, durationMs: Long): Boolean {
        val v = source as View
        val opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
        context.startActivity(intent(app), opts.toBundle())
        return true
    }

    override fun startWithFade(app: AppEntry, durationMs: Long): Boolean {
        val opts = ActivityOptions.makeCustomAnimation(
            context, android.R.anim.fade_in, android.R.anim.fade_out
        )
        context.startActivity(intent(app), opts.toBundle())
        return true
    }
}
