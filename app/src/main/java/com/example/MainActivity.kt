package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.designsystem.ResolveTheme
import com.example.domain.startup.StartupStage
import com.example.presentation.ResolveViewModel
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.navigation.ResolveAppShell

class MainActivity : ComponentActivity() {

    private val viewModel: ResolveViewModel by viewModels { ResolveViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            viewModel.startupStage.value == StartupStage.RESOLVING
        }

        enableEdgeToEdge()
        setContent {
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
        if (viewModel.startupStage.value != StartupStage.RESTORE_LOCK) {
            viewModel.refreshInstalledApps()
        }
    }
}
