package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.designsystem.ResolveTheme
import com.example.designsystem.paletteForTheme
import com.example.domain.model.FlipClockPage
import com.example.domain.model.ResolveThemeId
import com.example.flipclock.FlipClockActivity
import com.example.presentation.ResolveViewModel
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.navigation.ResolveAppShell

class MainActivity : ComponentActivity() {
    private val viewModel: ResolveViewModel by viewModels { ResolveViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { actionBar?.hide() }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleWidgetShortcutIntent(intent)
        setContent {
            val activeTheme by viewModel.appThemeId.collectAsStateWithLifecycle()
            val showSetupTips by viewModel.showSetupTips.collectAsStateWithLifecycle()
            val palette = paletteForTheme(activeTheme)

            LaunchedEffect(palette.isDark) {
                val barStyle = if (palette.isDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            ResolveTheme(
                themeId = activeTheme,
                showSetupTips = showSetupTips
            ) {
                ProvideSafeTextToolbar {
                    ResolveAppShell(
                        viewModel = viewModel,
                        onOpenFlipClock = {
                            startActivity(FlipClockActivity.createIntent(this@MainActivity))
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetShortcutIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCapabilities()
        viewModel.refreshDailyAppUsage()
    }

    private fun handleWidgetShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_QUICK_LOCK_15M -> viewModel.startQuickFocusLock(15L)
            ACTION_QUICK_LOCK_30M -> viewModel.startQuickFocusLock(30L)
            ACTION_OPEN_FLIP_CLOCK -> {
                startActivity(
                    FlipClockActivity.createIntent(
                        context = this,
                        initialPage = FlipClockPage.CLOCK
                    )
                )
            }
        }
    }

    companion object {
        const val ACTION_QUICK_LOCK_15M = "com.example.action.QUICK_LOCK_15M"
        const val ACTION_QUICK_LOCK_30M = "com.example.action.QUICK_LOCK_30M"
        const val ACTION_OPEN_FLIP_CLOCK = "com.example.action.OPEN_FLIP_CLOCK"
    }
}
