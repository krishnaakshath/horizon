package dev.tvlauncher.weather

import android.app.job.JobParameters
import android.app.job.JobService

class RefreshJobService : JobService() {
    @Volatile private var worker: Thread? = null
    @Volatile private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        val t = Thread {
            val ok = try { Refresher.refresh(applicationContext) } catch (e: Exception) { false }
            // After onStopJob the system already owns rescheduling; calling jobFinished would be wrong.
            if (!stopped) jobFinished(params, !ok) // reschedule with backoff on failure
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
