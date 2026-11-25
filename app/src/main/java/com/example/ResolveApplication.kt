package com.example

import android.app.Application
import android.os.Build
import android.util.Log
import com.example.di.AppContainer
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ResolveApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w("ResolveApp", "Recovered from background initialization error", throwable)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + appExceptionHandler)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { FirebaseApp.initializeApp(this) }
        }
        container = AppContainer(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            appScope.launch(Dispatchers.IO) {
                runCatching { ProviderInstaller.installIfNeeded(this@ResolveApplication) }
            }
        }
        runCatching { container.multiDeviceSyncCoordinator.start() }
        appScope.launch {
            runCatching { container.sessionEngine.initialize() }
        }
    }
}
