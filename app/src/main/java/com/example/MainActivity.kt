package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.designsystem.ResolveTheme
import com.example.domain.startup.StartupStage
import com.example.platform.ForceDarkCapabilityHelper
import com.example.presentation.ResolveViewModel
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.navigation.ResolveAppShell

class MainActivity : ComponentActivity() {

    private val viewModel: ResolveViewModel by viewModels { ResolveViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        ForceDarkCapabilityHelper.ensureResolveNightMode(this)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            viewModel.startupStage.value == StartupStage.RESOLVING
        }

        enableEdgeToEdge()
        setContent {
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = false
                    controller.isAppearanceLightNavigationBars = false
                }
            }

            ResolveTheme {
                ProvideSafeTextToolbar {
                    ResolveAppShell(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCapabilities()
        viewModel.refreshSyncNow()
        if (viewModel.startupStage.value != StartupStage.RESTORE_LOCK) {
            viewModel.refreshInstalledApps()
        }
    }
}
