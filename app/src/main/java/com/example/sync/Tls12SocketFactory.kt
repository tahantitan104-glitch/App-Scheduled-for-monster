package com.example.sync

import android.content.Context
import android.os.Build
import com.google.android.gms.security.ProviderInstaller
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Ensures TLS 1.2 is enabled on Android 5.1.1 (API 22) and pre-Oreo devices
 * when communicating with Google Identity Toolkit & Cloud Firestore REST APIs.
 */
object Tls12SocketFactory {

    private val providerChecked = AtomicBoolean(false)

    fun ensureProviderInstalled(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        if (providerChecked.compareAndSet(false, true)) {
            val ok = runCatching {
                ProviderInstaller.installIfNeeded(context.applicationContext)
                true
            }.getOrDefault(false)
            if (!ok) {
                providerChecked.set(false)
            }
        }
    }

    fun applyIfNeeded(connection: HttpsURLConnection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        createSocketFactoryOrNull()?.let { factory ->
            connection.sslSocketFactory = factory
        }
    }

    fun createSocketFactoryOrNull(): SSLSocketFactory? {
        return runCatching {
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, null, null)
            Wrapper(sslContext.socketFactory)
        }.getOrNull()
    }

    private class Wrapper(
        private val delegate: SSLSocketFactory
    ) : SSLSocketFactory() {

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        @Throws(IOException::class)
        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
            return patch(delegate.createSocket(s, host, port, autoClose))
        }

        @Throws(IOException::class)
        override fun createSocket(host: String?, port: Int): Socket {
            return patch(delegate.createSocket(host, port))
        }

        @Throws(IOException::class)
        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int
        ): Socket {
            return patch(delegate.createSocket(host, port, localHost, localPort))
        }

        @Throws(IOException::class)
        override fun createSocket(host: InetAddress?, port: Int): Socket {
            return patch(delegate.createSocket(host, port))
        }

        @Throws(IOException::class)
        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket {
            return patch(delegate.createSocket(address, port, localAddress, localPort))
        }

        private fun patch(socket: Socket): Socket {
            if (socket is SSLSocket) {
                val supported = socket.supportedProtocols.orEmpty().toSet()
                val desired = listOf("TLSv1.3", "TLSv1.2").filter { it in supported }
                if (desired.isNotEmpty()) {
                    socket.enabledProtocols = desired.toTypedArray()
                }
            }
            return socket
        }
    }
}
