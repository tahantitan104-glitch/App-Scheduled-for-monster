package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ResolveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight background listener/keep-alive service that preserves multi-device
 * lock command responsiveness on Android 5.1.1 (API 22) through modern Android
 * when Google Account Lock Sync is enabled.
 */
class ResolveSyncService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateObserverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        registerWakeSignals()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? ResolveApplication ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val coordinator = app.container.multiDeviceSyncCoordinator
        val initialSyncState = coordinator.uiState.value
        val notification = buildNotification(initialSyncState.activeSyncedDeviceCount.coerceAtLeast(1))

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        if (stateObserverJob?.isActive != true) {
            stateObserverJob = serviceScope.launch {
                coordinator.uiState.collect { state ->
                    if (!state.isSignedIn || !state.syncLockEnabled) {
                        SyncWakeAlarmReceiver.cancel(this@ResolveSyncService)
                        stopForegroundCompat()
                        stopSelf()
                    } else {
                        SyncWakeAlarmReceiver.scheduleNextWake(this@ResolveSyncService)
                        runCatching {
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.notify(
                                NOTIFICATION_ID,
                                buildNotification(state.activeSyncedDeviceCount.coerceAtLeast(1))
                            )
                        }
                    }
                }
            }
        }

        if (heartbeatJob?.isActive != true) {
            heartbeatJob = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    runCatching { coordinator.performWakefulSyncCheck() }
                    delay(8_000L)
                }
            }
        }

        SyncWakeAlarmReceiver.scheduleNextWake(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterWakeSignals()
        SyncWakeAlarmReceiver.cancel(this)
        heartbeatJob?.cancel()
        stateObserverJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerWakeSignals() {
        val app = application as? ResolveApplication ?: return
        if (screenReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    app.container.multiDeviceSyncCoordinator.refreshSyncNow()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_POWER_CONNECTED)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    @Suppress("DEPRECATION")
                    addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                }
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }
                screenReceiver = receiver
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback == null) {
            runCatching {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        app.container.multiDeviceSyncCoordinator.refreshSyncNow()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                            app.container.multiDeviceSyncCoordinator.refreshSyncNow()
                        }
                    }
                }
                cm?.registerDefaultNetworkCallback(callback)
                networkCallback = callback
            }
        }
    }

    private fun unregisterWakeSignals() {
        screenReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        screenReceiver = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback?.let { callback ->
                runCatching {
                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    cm?.unregisterNetworkCallback(callback)
                }
            }
        }
        networkCallback = null
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_sync_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_sync_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(deviceCount: Int): Notification {
        val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_resolve)
            .setColor(0xFF176B5B.toInt())
            .setContentTitle(getString(R.string.notification_sync_active_title))
            .setContentText("Ready for synced locks across $deviceCount device(s)")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "resolve_sync_channel"
        private const val NOTIFICATION_ID = 7003

        fun start(context: Context): Boolean {
            val intent = Intent(context, ResolveSyncService::class.java)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (_: Throwable) {
                false
            }
        }

        fun stop(context: Context) {
            SyncWakeAlarmReceiver.cancel(context)
            val intent = Intent(context, ResolveSyncService::class.java)
            runCatching { context.stopService(intent) }
        }
    }
}
