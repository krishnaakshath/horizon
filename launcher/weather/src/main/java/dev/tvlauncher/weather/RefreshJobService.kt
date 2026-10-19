package dev.tvlauncher.weather

import android.app.job.JobParameters
import android.app.job.JobService

class RefreshJobService : JobService() {
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val t = Thread {
            val ok = try { Refresher.refresh(applicationContext) } catch (e: Exception) { false }
            jobFinished(params, !ok) // reschedule with backoff on failure
        }
        worker = t
        t.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
