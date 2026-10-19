package dev.tvlauncher.weather

import android.app.job.JobParameters
import android.app.job.JobService

class RefreshJobService : JobService() {
    @Volatile private var worker: Thread? = null
    @Volatile private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        val t = Thread {
            val gated = params.jobId == RefreshScheduler.STALE_JOB_ID
            val ok = try {
                // Gated jobs re-check the rules at run time: another refresh may have won meanwhile.
                if (gated && Refresher.decide(WeatherStore(applicationContext), System.currentTimeMillis()) != RefreshPolicy.Decision.REFRESH) true
                else Refresher.refresh(applicationContext)
            } catch (e: Exception) { false }
            // After onStopJob the system already owns rescheduling; calling jobFinished would be wrong.
            // Gated jobs follow RefreshPolicy's own backoff instead of the system's.
            if (!stopped) jobFinished(params, !ok && !gated)
        }
        worker = t
        t.start()
        return true
    }

    /**
     * The system cancelled the job (constraints lost, etc.): interrupt the worker and ask to be
     * rescheduled (return true) so the refresh is retried when the network is back.
     */
    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        worker?.interrupt()
        return true
    }
}
