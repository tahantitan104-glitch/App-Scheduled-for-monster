package com.example.presentation.guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ResolveApplication
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.designsystem.ResolveTheme
import com.example.domain.model.AccountabilityPartnerConfig
import com.example.domain.model.AppLockVerificationResult
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.ResolveThemeId
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.components.ResolveIcons
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Full-screen blocker shown when an app hits its configured Daily Usage Quota limit.
 */
class QuotaReachedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as? ResolveApplication
        val container = app?.container
        val targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        val appName = intent?.getStringExtra(EXTRA_APP_NAME)?.takeIf { it.isNotBlank() }
            ?: targetPackage.substringAfterLast('.').ifBlank { "Selected App" }
        val limitMinutes = intent?.getIntExtra(EXTRA_LIMIT_MINUTES, 30) ?: 30
        val usedMinutes = intent?.getIntExtra(EXTRA_USED_MINUTES, limitMinutes) ?: limitMinutes

        setContent {
            val themeId by (container?.preferencesRepository?.appThemeIdFlow)
                ?.collectAsStateWithLifecycle(initialValue = ResolveThemeId.DEEP_FOREST_GREEN)
                ?: remember { mutableStateOf(ResolveThemeId.DEEP_FOREST_GREEN) }
            val partnerConfig by (container?.accountabilityPartnerManager?.configFlow)
                ?.collectAsStateWithLifecycle(initialValue = AccountabilityPartnerConfig())
                ?: remember { mutableStateOf(AccountabilityPartnerConfig()) }

            val scope = rememberCoroutineScope()
            var showOverrideDialog by remember { mutableStateOf(false) }
            var pinError by remember { mutableStateOf<String?>(null) }

            BackHandler { goHomeSafely() }

            ResolveTheme(themeId = themeId) {
                ProvideSafeTextToolbar {
                    QuotaReachedContent(
                        appName = appName,
                        usedMinutes = usedMinutes,
                        limitMinutes = limitMinutes,
                        usesPartnerPin = partnerConfig.enabled &&
                            partnerConfig.hasPartnerPin &&
                            partnerConfig.requireForQuotaOverride,
                        partnerName = partnerConfig.partnerName,
                        onCloseApp = { goHomeSafely() },
                        onRequestOverride = {
                            pinError = null
                            showOverrideDialog = true
                        }
                    )

                    if (showOverrideDialog && container != null) {
                        val usePartner = partnerConfig.enabled &&
                            partnerConfig.hasPartnerPin &&
                            partnerConfig.requireForQuotaOverride
                        QuotaOverrideDialog(
                            usePartnerPin = usePartner,
                            partnerName = partnerConfig.partnerName,
                            errorText = pinError,
                            onDismiss = { showOverrideDialog = false },
                            onVerify = { input ->
                                scope.launch {
                                    val verified = if (usePartner) {
                                        when (val r = container.accountabilityPartnerManager.verifyPartnerPin(input)) {
                                            AppLockVerificationResult.Verified -> true
                                            is AppLockVerificationResult.InvalidCredential -> {
                                                pinError = "Incorrect PIN (${r.remainingAttemptsBeforeCooldown} left)."
                                                false
                                            }
                                            is AppLockVerificationResult.Throttled -> {
                                                pinError = "Wait ${r.retryAfterSeconds}s before retrying."
                                                false
                                            }
                                            AppLockVerificationResult.NotConfigured -> {
                                                pinError = "Partner PIN is not configured."
                                                false
                                            }
                                        }
                                    } else {
                                        when (val r = container.recoveryCodeManager.verifyRecoveryCode(input)) {
                                            RecoveryVerificationResult.Verified -> true
                                            is RecoveryVerificationResult.InvalidCode -> {
                                                pinError = "Invalid code (${r.remainingAttemptsBeforeCooldown} left)."
                                                false
                                            }
                                            is RecoveryVerificationResult.Throttled -> {
                                                pinError = "Wait ${r.retryAfterSeconds}s before retrying."
                                                false
                                            }
                                            RecoveryVerificationResult.NotConfigured -> {
                                                pinError = "Set a Recovery Code in More first."
                                                false
                                            }
                                        }
                                    }

                                    if (verified && targetPackage.isNotBlank()) {
                                        val todayEpoch = Instant.ofEpochMilli(container.clockProvider.wallClockMs())
                                            .atZone(container.clockProvider.zoneId())
                                            .toLocalDate()
                                            .toEpochDay()
                                        container.preferencesRepository.grantQuotaOverrideForDay(targetPackage, todayEpoch)
                                        showOverrideDialog = false
                                        container.installedAppsRepository.launchAllowedApp(targetPackage)
                                        finish()
                                    }
                                }
                            }
                        )
                    }
                }
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
        const val EXTRA_TARGET_PACKAGE = "extra_quota_pkg"
        const val EXTRA_APP_NAME = "extra_quota_app_name"
        const val EXTRA_LIMIT_MINUTES = "extra_quota_limit_min"
        const val EXTRA_USED_MINUTES = "extra_quota_used_min"
    }
}

@Composable
private fun QuotaReachedContent(
    appName: String,
    usedMinutes: Int,
    limitMinutes: Int,
    usesPartnerPin: Boolean,
    partnerName: String,
    onCloseApp: () -> Unit,
    onRequestOverride: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(24.dp)
            .testTag("quota_reached_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surfaceSecondary)
                .border(1.dp, palette.warn.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.warnContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ResolveIcons.HourglassTop,
                    contentDescription = "Daily quota reached",
                    tint = palette.warn,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = "DAILY QUOTA REACHED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = palette.warn
            )

            Text(
                text = "$appName paused for today",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = palette.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "${usedMinutes}m used today · Daily allowance: ${limitMinutes}m. Quota resets automatically at midnight.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onCloseApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryButton,
                    contentColor = palette.onPrimaryButton
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("quota_close_app_btn")
            ) {
                Text("Close App")
            }

            OutlinedButton(
                onClick = onRequestOverride,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.controlBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("quota_override_btn")
            ) {
                Text(
                    text = if (usesPartnerPin) {
                        val label = partnerName.ifBlank { "Partner" }
                        "Unlock Today with $label PIN"
                    } else {
                        "Unlock Today with Recovery Code"
                    },
                    color = palette.textPrimary
                )
            }
        }
    }
}

@Composable
private fun QuotaOverrideDialog(
    usePartnerPin: Boolean,
    partnerName: String,
    errorText: String?,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = {
            Text(
                text = if (usePartnerPin) {
                    "${partnerName.ifBlank { "Partner" }} Approval"
                } else {
                    "Recovery Code Override"
                },
                color = palette.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (usePartnerPin) {
                        "Ask your accountability partner to enter their PIN to lift today's quota."
                    } else {
                        "Enter your Recovery Code to lift this app's quota for today."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { ch -> ch.isDigit() }.take(18) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text(if (usePartnerPin) "Partner PIN" else "Recovery Code") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(input) },
                enabled = input.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryButton,
                    contentColor = palette.onPrimaryButton
                )
            ) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textSecondary)
            }
        }
    )
}
