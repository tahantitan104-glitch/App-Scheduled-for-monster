package com.example.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.example.ResolveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persisted periodic safety-net recovery job for active focus sessions and enabled schedules.
 * Deduplicated via a fixed [RECOVERY_JOB_ID] so repeat boots/updates never enqueue duplicates.
 */
class SessionRecoveryJobService : JobService() {

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = applicationContext as? ResolveApplication ?: return false
        jobScope.launch {
            var needsReschedule = false
            runCatching {
                val engine = app.container.sessionEngine
                engine.initialize()
                engine.onSystemBootOrTimeChange(isBoot = false)
                val session = engine.activeSession.value
                val hasActiveSession = session != null && !session.isTerminal
                val hasEnabledSchedule = app.container.scheduleRepository.getAllSchedules().any { it.isEnabled }
                needsReschedule = hasActiveSession || hasEnabledSchedule
                if (!needsReschedule) {
                    cancel(applicationContext)
                }
            }
            runCatching { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }

    override fun onDestroy() {
        jobScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val RECOVERY_JOB_ID = 4401
        private const val PERIODIC_INTERVAL_MS = 15L * 60_000L

        fun scheduleIfNeeded(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                    ?: return
                val alreadyPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    scheduler.getPendingJob(RECOVERY_JOB_ID) != null
                } else {
                    scheduler.allPendingJobs.any { it.id == RECOVERY_JOB_ID }
                }
                if (alreadyPending) return

                val component = ComponentName(context, SessionRecoveryJobService::class.java)
                val builder = JobInfo.Builder(RECOVERY_JOB_ID, component)
                    .setPersisted(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setPeriodic(PERIODIC_INTERVAL_MS, 5L * 60_000L)
                } else {
                    builder.setPeriodic(PERIODIC_INTERVAL_MS)
                }
                scheduler.schedule(builder.build())
            }
        }

        fun cancel(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                    ?: return
                scheduler.cancel(RECOVERY_JOB_ID)
            }
        }
    }
}
