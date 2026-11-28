package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ResolveApplication
import com.example.domain.model.ContentBlockerConfig
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Local on-device DNS-only VpnService.
 * Routes ONLY virtual DNS address 10.111.222.2/32 through TUN — normal app traffic never traverses user space.
 */
class ResolveDnsVpnService : VpnService() {

    private val running = AtomicBoolean(false)
    private var tunInterface: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null
    private var forwardExecutor: ExecutorService? = null
    private val writeLock = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpnTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        if (running.compareAndSet(false, true)) {
            startVpnTunnel()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpnTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }

    private fun startVpnTunnel() {
        val tun = runCatching {
            Builder()
                .setSession("Resolve DNS Filter")
                .addAddress(VIRTUAL_TUN_ADDRESS, 32)
                .addRoute(VIRTUAL_DNS_SERVER, 32)
                .addDnsServer(VIRTUAL_DNS_SERVER)
                .setMtu(1500)
                .apply {
                    runCatching { addDisallowedApplication(packageName) }
                }
                .establish()
        }.onFailure {
            Log.w(TAG, "Unable to establish local DNS VPN interface", it)
        }.getOrNull()

        if (tun == null) {
            running.set(false)
            stopSelf()
            return
        }

        tunInterface = tun
        forwardExecutor = Executors.newFixedThreadPool(4)

        readerThread = thread(
            start = true,
            isDaemon = true,
            name = "ResolveDnsVpnReader"
        ) {
            runTunnelLoop(tun)
        }
    }

    private fun runTunnelLoop(tun: ParcelFileDescriptor) {
        val container = (application as? ResolveApplication)?.container
        val input = FileInputStream(tun.fileDescriptor)
        val output = FileOutputStream(tun.fileDescriptor)
        val buffer = ByteArray(4096)

        while (running.get() && !Thread.currentThread().isInterrupted) {
            val readLen = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (readLen <= 0) {
                if (!running.get()) break
                runCatching { Thread.sleep(10L) }
                continue
            }

            val packetCopy = buffer.copyOf(readLen)
            val parsed = DnsPacketCodec.parseIpv4UdpDnsQuery(packetCopy, readLen) ?: continue

            val config = container?.contentBlockerConfigState?.value ?: ContentBlockerConfig()
            val session = container?.sessionEngine?.activeSession?.value
            val isLockActive = session?.isEnforcementPhase == true

            if (DnsBlocklistMatcher.shouldBlockDomain(parsed.queryDomain, config, isLockActive)) {
                val nxDomain = DnsPacketCodec.buildNxDomainResponse(parsed)
                synchronized(writeLock) {
                    runCatching { output.write(nxDomain) }
                }
                container?.recordDnsBlockedAsync()
            } else {
                val exec = forwardExecutor ?: continue
                runCatching {
                    exec.execute {
                        forwardUpstreamDnsQuery(parsed, output)
                    }
                }
            }
        }
    }

    private fun forwardUpstreamDnsQuery(query: ParsedDnsPacket, output: FileOutputStream) {
        val dnsQueryBytes = query.rawPacket.copyOfRange(
            query.dnsPayloadOffset,
            query.dnsPayloadOffset + query.dnsPayloadLength
        )
        val upstreams = listOf("1.1.1.1", "8.8.8.8")
        for (upstreamHost in upstreams) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = 2500
                val upstreamAddr = InetAddress.getByName(upstreamHost)
                val outPacket = DatagramPacket(dnsQueryBytes, dnsQueryBytes.size, upstreamAddr, 53)
                socket.send(outPacket)

                val respBuf = ByteArray(2048)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket.receive(respPacket)
                if (respPacket.length > 0) {
                    val responseIpPacket = DnsPacketCodec.buildForwardedUdpDnsResponse(
                        query = query,
                        upstreamDnsPayload = respBuf,
                        payloadLen = respPacket.length
                    )
                    synchronized(writeLock) {
                        runCatching { output.write(responseIpPacket) }
                    }
                    return
                }
            } catch (_: Throwable) {
                // Try fallback upstream resolver
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    private fun stopVpnTunnel() {
        running.set(false)
        readerThread?.interrupt()
        readerThread = null
        runCatching { forwardExecutor?.shutdownNow() }
        forwardExecutor = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { stopForeground(true) }
        }
    }

    private fun startForegroundCompat() {
        ensureNotificationChannel()
        val notification = buildNotification()
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

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(this, 4401, openAppIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_resolve)
            .setContentTitle("Resolve DNS Filter Active")
            .setContentText("Blocking distracting websites locally on device")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_vpn_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_vpn_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val TAG = "ResolveDnsVpnService"
        private const val VIRTUAL_TUN_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS_SERVER = "10.111.222.2"
        private const val CHANNEL_ID = "resolve_vpn_channel"
        private const val NOTIFICATION_ID = 4402
        const val ACTION_START = "com.example.vpn.ACTION_START"
        const val ACTION_STOP = "com.example.vpn.ACTION_STOP"

        fun prepareVpnIntent(context: Context): Intent? = runCatching {
            prepare(context)
        }.getOrNull()

        fun isVpnConsentGranted(context: Context): Boolean = runCatching {
            prepare(context) == null
        }.getOrDefault(false)

        fun reconcileState(
            context: Context,
            config: ContentBlockerConfig,
            isFocusLockActive: Boolean
        ) {
            val shouldRun = DnsBlocklistMatcher.shouldVpnBeActive(config, isFocusLockActive)
            if (shouldRun && isVpnConsentGranted(context)) {
                start(context)
            } else {
                stop(context)
            }
        }

        fun start(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, ResolveDnsVpnService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, ResolveDnsVpnService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { appCtx.startService(intent) }
        }
    }
}
