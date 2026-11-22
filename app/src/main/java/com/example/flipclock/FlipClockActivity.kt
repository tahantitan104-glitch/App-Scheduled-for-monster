package com.example.flipclock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ResolveApplication
import com.example.designsystem.ResolveTheme
import com.example.domain.model.FlipClockPage
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.SessionState
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.session.LockExitSignal
import kotlinx.coroutines.launch

class FlipClockActivity : ComponentActivity() {

    private var initialPageState by mutableStateOf(FlipClockPage.CLOCK)
    private var restrictedLockState by mutableStateOf(false)
    private var initialSystemBrightnessInt: Int? = null
    private var didModifySystemBrightness = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureFullscreenBars()
        initialSystemBrightnessInt = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()

        val app = application as? ResolveApplication ?: run {
            finish()
            return
        }
        val container = app.container
        val manager = container.flipClockManager
        val sessionEngine = container.sessionEngine

        parseIntent(intent)

        lifecycleScope.launch {
            manager.initialize()
            sessionEngine.initialize()
        }

        // If this Flip Clock instance is Lock-Restricted, exit cleanly when the active Focus Lock ends
        lifecycleScope.launch {
            sessionEngine.lockExitSignals.collect { signal ->
                if (restrictedLockState && signal == LockExitSignal.ExitLockTaskAndFinish && !isFinishing) {
                    finishAndRemoveTask()
                }
            }
        }

        setContent {
            ResolveTheme {
                val scope = rememberCoroutineScope()
                val settings by manager.settings.collectAsStateWithLifecycle()
                val timerState by manager.timerState.collectAsStateWithLifecycle()
                val activeSession by sessionEngine.activeSession.collectAsStateWithLifecycle()

                var controlsVisible by remember { mutableStateOf(false) }

                // Exit restricted Flip Clock automatically if session finishes/recovers externally
                LaunchedEffect(restrictedLockState, activeSession?.state) {
                    if (restrictedLockState) {
                        val st = activeSession?.state
                        if (st == SessionState.COMPLETED ||
                            st == SessionState.RECOVERED ||
                            st == SessionState.ENFORCEMENT_FAILED ||
                            st == SessionState.ENFORCEMENT_UNAVAILABLE
                        ) {
                            if (!isFinishing) {
                                finishAndRemoveTask()
                            }
                        }
                    }
                }

                // First-entry brightness initialization (clamped 15%..85%) or restore last selected brightness
                val startingBrightness = remember(settings.lastBrightnessPercent) {
                    settings.lastBrightnessPercent ?: readClampedSystemBrightnessPercent()
                }
                var appliedBrightness by remember(startingBrightness) {
                    mutableFloatStateOf(startingBrightness)
                }

                LaunchedEffect(Unit) {
                    applyWindowBrightness(startingBrightness)
                    if (settings.lastBrightnessPercent == null) {
                        manager.saveLastBrightnessPercent(startingBrightness)
                    }
                }

                // Keep Screen Awake toggle via Window FLAG_KEEP_SCREEN_ON
                LaunchedEffect(settings.keepScreenAwake) {
                    if (settings.keepScreenAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                DisposableEffect(Unit) {
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Request POST_NOTIFICATIONS once on first Timer start (Android 13+)
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    scope.launch {
                        manager.markNotificationPermissionHandled(denied = !granted)
                        manager.startOrResumeTimer()
                    }
                }

                // Part 14: First Back hides visible overlay controls; second Back exits cleanly
                BackHandler {
                    if (controlsVisible) {
                        controlsVisible = false
                    } else {
                        finish()
                    }
                }

                ProvideSafeTextToolbar {
                    FlipClockScreen(
                        manager = manager,
                        initialPage = initialPageState,
                        isRestrictedLock = restrictedLockState,
                        initialBrightnessPercent = appliedBrightness,
                        controlsVisible = controlsVisible,
                        onControlsVisibleChange = { controlsVisible = it },
                        onBrightnessChanged = { pct ->
                            appliedBrightness = pct
                            applyWindowBrightness(pct)
                        },
                        onRequestTimerStartWithNotificationCheck = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !container.flipTimerNotificationHelper.hasNotificationPermission() &&
                                !timerState.notificationPermissionRequestedOnce
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                scope.launch { manager.startOrResumeTimer() }
                            }
                        },
                        onOpenAppNotificationSettings = {
                            val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { startActivity(appSettingsIntent) }
                        },
                        onSleepTimerExpired = {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            applyWindowBrightness(FlipClockSettings.MIN_BRIGHTNESS_PERCENT)
                            if (!isFinishing) {
                                finish()
                            }
                        },
                        onExit = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?) {
        val rawPage = intent?.getStringExtra(EXTRA_INITIAL_PAGE)
        initialPageState = FlipClockPage.entries.firstOrNull { it.name == rawPage } ?: FlipClockPage.CLOCK
        restrictedLockState = intent?.getBooleanExtra(EXTRA_RESTRICTED_LOCK, false) ?: false
    }

    private fun configureFullscreenBars() {
        runCatching {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun readClampedSystemBrightnessPercent(): Float {
        val sysInt = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        }.getOrDefault(128)
        val rawPercent = (sysInt.coerceIn(0, 255) / 255f) * 100f
        return rawPercent.coerceIn(
            FlipClockSettings.MIN_BRIGHTNESS_PERCENT,
            FlipClockSettings.MAX_BRIGHTNESS_PERCENT
        )
    }

    private fun applyWindowBrightness(percent: Float) {
        val clamped = percent.coerceIn(
            FlipClockSettings.MIN_BRIGHTNESS_PERCENT,
            FlipClockSettings.MAX_BRIGHTNESS_PERCENT
        )
        runCatching {
            val lp = window.attributes
            lp.screenBrightness = clamped / 100f
            window.attributes = lp
            // Keep selected brightness on device Settings if WRITE_SETTINGS was granted by user
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this)) {
                val sysVal = ((clamped / 100f) * 255f).toInt().coerceIn(1, 255)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, sysVal)
                didModifySystemBrightness = true
            }
        }
    }

    override fun onDestroy() {
        restoreInitialBrightness()
        super.onDestroy()
    }

    private fun restoreInitialBrightness() {
        runCatching {
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
            val savedSys = initialSystemBrightnessInt
            if (didModifySystemBrightness && savedSys != null &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this))
            ) {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    savedSys.coerceIn(1, 255)
                )
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_PAGE = "extra_flip_initial_page"
        const val EXTRA_RESTRICTED_LOCK = "extra_flip_restricted_lock"
        const val EXTRA_FROM_NOTIFICATION = "extra_flip_from_notification"

        fun createIntent(
            context: Context,
            restrictedLock: Boolean = false,
            initialPage: FlipClockPage = FlipClockPage.CLOCK,
            fromNotification: Boolean = false
        ): Intent = Intent(context, FlipClockActivity::class.java).apply {
            putExtra(EXTRA_RESTRICTED_LOCK, restrictedLock)
            putExtra(EXTRA_INITIAL_PAGE, initialPage.name)
            putExtra(EXTRA_FROM_NOTIFICATION, fromNotification)
        }
    }
}
