package dev.tvlauncher.weather

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/** JobScheduler-based scheduling (no libraries). */
object RefreshScheduler {
    private const val PERIODIC_JOB_ID = 1001
    private const val ONESHOT_JOB_ID = 1002
    private const val PERIOD_MS = 30 * 60 * 1000L
    private const val FLEX_MS = 10 * 60 * 1000L

    private fun scheduler(c: Context) = c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    private fun component(c: Context) = ComponentName(c, RefreshJobService::class.java)

    /** Idempotent: keeps an existing pending periodic job unless [force]. */
    fun schedulePeriodic(context: Context, force: Boolean = false) {
        try {
            val js = scheduler(context)
            if (!force && js.getPendingJob(PERIODIC_JOB_ID) != null) return
            val job = JobInfo.Builder(PERIODIC_JOB_ID, component(context))
                .setPeriodic(PERIOD_MS, FLEX_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            js.schedule(job)
        } catch (e: RuntimeException) {
            // Scheduling is best effort; the settings screen and broadcast still refresh.
        }
    }

    /** One-off job as soon as a network is available; the system retries failures with backoff. */
    fun refreshSoon(context: Context) {
        try {
            val job = JobInfo.Builder(ONESHOT_JOB_ID, component(context))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(60_000L)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            scheduler(context).schedule(job)
        } catch (e: RuntimeException) {
        }
    }
}
