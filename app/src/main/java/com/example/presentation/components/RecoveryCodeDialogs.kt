package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.RecoveryVerificationResult
import com.example.security.RecoveryCodeManager

@Composable
fun RecoveryCodeSetupDialog(
    generateRandomCode: (Int) -> String,
    onDismiss: () -> Unit,
    onSaveCode: (String) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var selectedLength by remember { mutableIntStateOf(RecoveryCodeManager.DEFAULT_LENGTH) }
    var codeInput by remember { mutableStateOf(generateRandomCode(RecoveryCodeManager.DEFAULT_LENGTH)) }
    var confirmInput by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = {
            Text(
                text = "Configure Recovery Code",
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary
            )
        },
        text = {
            ProvideSafeTextToolbar {
                CleanupTextInteractionsOnDispose()
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Only a salted cryptographic verifier is stored. Write this code down before confirming—it cannot be viewed again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            RecoveryCodeManager.DEFAULT_LENGTH to "16-digit",
                            RecoveryCodeManager.EXTENDED_LENGTH to "32-digit (optional)"
                        ).forEach { (len, title) ->
                            val chipSelected = selectedLength == len
                            FilterChip(
                                selected = chipSelected,
                                onClick = {
                                    selectedLength = len
                                    codeInput = generateRandomCode(len)
                                    confirmInput = ""
                                    acknowledged = false
                                    error = null
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.surfaceElevated,
                                    selectedLabelColor = palette.accentStrong,
                                    containerColor = palette.cardBottom,
                                    labelColor = palette.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = chipSelected,
                                    borderColor = palette.cardBorder,
                                    selectedBorderColor = palette.accentSecondary
                                ),
                                label = { Text(title) }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.background)
                            .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = RecoveryCodeManager.formatGrouped(codeInput.ifBlank { "0".repeat(selectedLength) }),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = palette.accentStrong
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            codeInput = generateRandomCode(selectedLength)
                            confirmInput = ""
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(ResolveIcons.Casino, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate New $selectedLength-Digit Code")
                    }

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { input ->
                            if (input.length <= selectedLength && input.all { it.isDigit() }) {
                                codeInput = input
                                error = null
                            }
                        },
                        label = { Text("Custom or generated $selectedLength digits") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_setup_code_input")
                    )

                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = { input ->
                            if (input.length <= selectedLength && input.all { it.isDigit() }) {
                                confirmInput = input
                                error = null
                            }
                        },
                        label = { Text("Confirm $selectedLength-digit code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_setup_confirm_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { acknowledged = !acknowledged },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acknowledged,
                            onCheckedChange = { acknowledged = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.accent,
                                checkmarkColor = Color.White,
                                uncheckedColor = palette.textSecondary
                            ),
                            modifier = Modifier.testTag("recovery_setup_ack_checkbox")
                        )
                        Text(
                            text = "I have recorded this recovery code safely.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textPrimary
                        )
                    }

                    if (error != null) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val textToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            Button(
                onClick = {
                    when {
                        codeInput.length != selectedLength || !codeInput.all { it.isDigit() } ->
                            error = "Code must be exactly $selectedLength numeric digits."
                        codeInput != confirmInput ->
                            error = "Confirmation code does not match."
                        !acknowledged ->
                            error = "Confirm that you have recorded the code."
                        else -> {
                            dismissTextInteractions(focusManager, textToolbar, keyboardController)
                            onSaveCode(codeInput)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryButton,
                    contentColor = palette.onPrimaryButton
                ),
                modifier = Modifier.testTag("recovery_setup_save_button")
            ) {
                Text("Save Code")
            }
        },
        dismissButton = {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val textToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            TextButton(
                onClick = {
                    dismissTextInteractions(focusManager, textToolbar, keyboardController)
                    onDismiss()
                }
            ) {
                Text("Cancel", color = palette.textSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryCodeVerifySheet(
    expectedLength: Int,
    verificationFeedback: RecoveryVerificationResult?,
    isVerifying: Boolean,
    cooldownSeconds: Int = 0,
    onDismiss: () -> Unit,
    onSubmitCode: (String) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var input by remember { mutableStateOf("") }
    var remainingCooldown by remember(cooldownSeconds) {
        androidx.compose.runtime.mutableIntStateOf(cooldownSeconds.coerceAtLeast(0))
    }

    androidx.compose.runtime.LaunchedEffect(cooldownSeconds) {
        while (remainingCooldown > 0) {
            kotlinx.coroutines.delay(1_000L)
            remainingCooldown--
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary
    ) {
        ProvideSafeTextToolbar {
            CleanupTextInteractionsOnDispose()
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val textToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Emergency Session Recovery",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary
                )
                Text(
                    text = if (remainingCooldown > 0) {
                        "Emergency cooldown active (${remainingCooldown}s remaining) before $expectedLength-digit recovery is enabled."
                    } else {
                        "Enter your $expectedLength-digit recovery code to exit Lock Task Mode."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = { raw ->
                        val digitsOnly = raw.filter { it.isDigit() }
                        if (digitsOnly.length <= expectedLength) {
                            input = digitsOnly
                        }
                    },
                    label = { Text("$expectedLength-digit numeric code (${input.length}/$expectedLength)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recovery_verify_input")
                )

                when (verificationFeedback) {
                    is RecoveryVerificationResult.InvalidCode -> {
                        Text(
                            text = "Incorrect code. ${verificationFeedback.remainingAttemptsBeforeCooldown} attempts left before cooldown.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.error
                        )
                    }
                    is RecoveryVerificationResult.Throttled -> {
                        Text(
                            text = "Too many attempts. Try again in ${verificationFeedback.retryAfterSeconds}s.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.warn
                        )
                    }
                    RecoveryVerificationResult.NotConfigured -> {
                        Text(
                            text = "No recovery code verifier is stored.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.error
                        )
                    }
                    else -> Unit
                }

                Button(
                    onClick = {
                        dismissTextInteractions(focusManager, textToolbar, keyboardController)
                        onSubmitCode(input)
                    },
                    enabled = !isVerifying && remainingCooldown <= 0 && input.length == expectedLength,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryButton,
                        contentColor = palette.onPrimaryButton
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("recovery_verify_submit_button")
                ) {
                    Text(
                        when {
                            isVerifying -> "Verifying…"
                            remainingCooldown > 0 -> "Cooldown (${remainingCooldown}s)…"
                            else -> "Verify & End Lock"
                        }
                    )
                }
            }
        }
    }
}
