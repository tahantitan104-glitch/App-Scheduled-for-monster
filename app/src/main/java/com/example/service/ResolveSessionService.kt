package com.example.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ResolveApplication
import com.example.domain.model.ActiveSession
import com.example.domain.model.EnforcementMode
import com.example.domain.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ResolveSessionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var fallbackGuardJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? ResolveApplication ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val container = app.container
        val engine = container.sessionEngine
        val initialSession = engine.activeSession.value
        val notification = buildNotification(initialSession, engine.remainingMillis.value)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        if (observerJob?.isActive != true) {
            observerJob = serviceScope.launch {
                runCatching { engine.initialize() }
                combine(engine.activeSession, engine.remainingMillis) { session, remaining ->
                    session to remaining
                }.collect { (session, remaining) ->
                    if (session == null || session.isTerminal) {
                        container.foregroundGuard.clearBarrier()
                        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                        stopSelf()
                    } else {
                        runCatching {
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.notify(NOTIFICATION_ID, buildNotification(session, remaining))
                        }
                    }
                }
            }
        }

        if (fallbackGuardJob?.isActive != true) {
            fallbackGuardJob = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    delay(850L)
                    runCatching {
                        val session = engine.activeSession.value ?: return@runCatching
                        if (session.state != SessionState.LOCK_ENFORCEMENT_ACTIVE) return@runCatching
                        if (session.enforcementMode != EnforcementMode.ACCESSIBILITY_GUARD) return@runCatching

                        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                        if (pm?.isInteractive == false) return@runCatching
                        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (km?.isKeyguardLocked == true) return@runCatching

                        val fgPkg = container.foregroundGuard.queryLatestForegroundPackage() ?: return@runCatching
                        if (fgPkg == packageName || fgPkg == "android" || fgPkg == "com.android.systemui") {
                            return@runCatching
                        }
                        if (session.allowedPackages.contains(fgPkg)) return@runCatching
                        if (container.phoneAccessManager.resolveEmergencyPackages().contains(fgPkg)) {
                            return@runCatching
                        }
                        if (session.phoneAccessEnabled &&
                            container.phoneAccessManager.resolveEssentialDialerPackages().contains(fgPkg)
                        ) {
                            return@runCatching
                        }

                        container.foregroundGuard.showBriefBarrierAndLaunchLockScreen()
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fallbackGuardJob?.cancel()
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_session_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_session_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(session: ActiveSession?, remainingMs: Long): Notification {
        val targetActivity = if (session?.state == SessionState.LOCK_ENFORCEMENT_ACTIVE ||
            session?.state == SessionState.LOCK_TRANSITION_PENDING
        ) {
            com.example.presentation.lockscreen.LockScreenActivity::class.java
        } else {
            MainActivity::class.java
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, targetActivity),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formatted = formatHms(remainingMs)
        val (title, text) = when (session?.state) {
            SessionState.DELAYED_FREE_USE_ACTIVE ->
                "Delayed Lock — Free Use Active" to "Locks automatically in $formatted"
            SessionState.LOCK_TRANSITION_PENDING ->
                "Resolve Lock Transition" to "Activating device restriction…"
            SessionState.LOCK_ENFORCEMENT_ACTIVE,
            SessionState.PHONE_CALL_ACTIVE ->
                "Resolve Lock Active" to "Remaining time: $formatted"
            else ->
                "Resolve" to "Monitoring focus session"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun formatHms(millis: Long): String {
        val totalSec = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return "%02d:%02d:%02d".format(h, m, s)
    }

    companion object {
        private const val CHANNEL_ID = "resolve_session_channel"
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context) {
            val intent = Intent(context, ResolveSessionService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ResolveSessionService::class.java)
            runCatching { context.stopService(intent) }
        }
    }
}
