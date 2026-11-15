package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.designsystem.ResolveTheme
import com.example.presentation.ResolveViewModel
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.navigation.ResolveAppShell

class MainActivity : ComponentActivity() {

    private val viewModel: ResolveViewModel by viewModels { ResolveViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        viewModel.refreshInstalledApps()
    }
}
