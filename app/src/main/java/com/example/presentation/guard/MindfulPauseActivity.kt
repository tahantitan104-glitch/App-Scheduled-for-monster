package com.example.presentation.guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ResolveApplication
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.designsystem.ResolveTheme
import com.example.domain.model.ResolveThemeId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Calm Mindful Breathing Delay screen shown before opening selected distracting apps.
 */
class MindfulPauseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as? ResolveApplication
        val container = app?.container
        val targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        val appLabel = intent?.getStringExtra(EXTRA_APP_NAME)?.takeIf { it.isNotBlank() }
            ?: targetPackage.substringAfterLast('.').ifBlank { "Selected App" }
        val delaySeconds = intent?.getIntExtra(EXTRA_DELAY_SECONDS, 10)?.coerceIn(5, 15) ?: 10
        val passMinutes = intent?.getIntExtra(EXTRA_PASS_MINUTES, 5)?.coerceIn(1, 30) ?: 5

        setContent {
            val themeId by (container?.preferencesRepository?.appThemeIdFlow)
                ?.collectAsStateWithLifecycle(initialValue = ResolveThemeId.DEEP_FOREST_GREEN)
                ?: remember { androidx.compose.runtime.mutableStateOf(ResolveThemeId.DEEP_FOREST_GREEN) }

            val scope = rememberCoroutineScope()

            BackHandler {
                goHomeSafely()
            }

            ResolveTheme(themeId = themeId) {
                MindfulPauseContent(
                    appName = appLabel,
                    delaySeconds = delaySeconds,
                    passMinutes = passMinutes,
                    onStayFocused = { goHomeSafely() },
                    onContinueTempPass = {
                        scope.launch {
                            if (container != null && targetPackage.isNotBlank()) {
                                container.preferencesRepository.grantMindfulPass(
                                    packageName = targetPackage,
                                    durationMs = passMinutes * 60_000L,
                                    nowWallMs = container.clockProvider.wallClockMs()
                                )
                                container.installedAppsRepository.launchAllowedApp(targetPackage)
                            }
                            finish()
                        }
                    }
                )
            }
        }
    }

    private fun goHomeSafely() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(homeIntent) }
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_mindful_pkg"
        const val EXTRA_APP_NAME = "extra_mindful_app_name"
        const val EXTRA_DELAY_SECONDS = "extra_mindful_delay_sec"
        const val EXTRA_PASS_MINUTES = "extra_mindful_pass_min"
    }
}

@Composable
private fun MindfulPauseContent(
    appName: String,
    delaySeconds: Int,
    passMinutes: Int,
    onStayFocused: () -> Unit,
    onContinueTempPass: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var remainingSec by remember(delaySeconds) { mutableIntStateOf(delaySeconds) }

    LaunchedEffect(delaySeconds) {
        while (remainingSec > 0) {
            delay(1_000L)
            remainingSec--
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mindful_breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(24.dp)
            .testTag("mindful_pause_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "MINDFUL PAUSE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = palette.accentStrong
            )

            Box(
                modifier = Modifier.size(164.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(152.dp)
                        .scale(breathScale)
                        .clip(CircleShape)
                        .background(palette.accentContainer.copy(alpha = 0.55f))
                        .border(2.dp, palette.accent.copy(alpha = 0.6f), CircleShape)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (remainingSec > 0) "${remainingSec}s" else "Ready",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (remainingSec > 0) "Breathe slowly" else "Choose mindfully",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }

            Text(
                text = "Do you really need to open $appName right now?",
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Take a slow breath and decide whether this serves your focus.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = onStayFocused,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryButton,
                    contentColor = palette.onPrimaryButton
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("mindful_stay_focused_btn")
            ) {
                Text(
                    text = "Stay Focused (Close $appName)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            OutlinedButton(
                onClick = onContinueTempPass,
                enabled = remainingSec <= 0,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.cardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = palette.textSecondary,
                    disabledContentColor = palette.textMuted
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("mindful_continue_pass_btn")
            ) {
                Text(
                    text = if (remainingSec > 0) {
                        "Wait ${remainingSec}s to continue..."
                    } else {
                        "Continue for $passMinutes min"
                    }
                )
            }
        }
    }
}
