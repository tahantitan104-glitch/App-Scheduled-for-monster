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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.designsystem.ResolveColors
import com.example.domain.model.RecoveryVerificationResult
import com.example.security.RecoveryCodeManager

@Composable
fun RecoveryCodeSetupDialog(
    generateRandomCode: (Int) -> String,
    onDismiss: () -> Unit,
    onSaveCode: (String) -> Unit
) {
    var selectedLength by remember { mutableIntStateOf(RecoveryCodeManager.DEFAULT_LENGTH) }
    var codeInput by remember { mutableStateOf(generateRandomCode(RecoveryCodeManager.DEFAULT_LENGTH)) }
    var confirmInput by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ResolveColors.ForestSurface,
        title = {
            Text(
                text = "Configure Recovery Code",
                style = MaterialTheme.typography.titleLarge,
                color = ResolveColors.TextPrimary
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
                        color = ResolveColors.TextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            RecoveryCodeManager.DEFAULT_LENGTH to "16-digit",
                            RecoveryCodeManager.EXTENDED_LENGTH to "32-digit (optional)"
                        ).forEach { (len, title) ->
                            FilterChip(
                                selected = selectedLength == len,
                                onClick = {
                                    selectedLength = len
                                    codeInput = generateRandomCode(len)
                                    confirmInput = ""
                                    acknowledged = false
                                    error = null
                                },
                                label = { Text(title) }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ResolveColors.ForestSecondary)
                            .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = RecoveryCodeManager.formatGrouped(codeInput.ifBlank { "0".repeat(selectedLength) }),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = ResolveColors.MintAccent
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
                            modifier = Modifier.testTag("recovery_setup_ack_checkbox")
                        )
                        Text(
                            text = "I have recorded this recovery code safely.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.TextPrimary
                        )
                    }

                    if (error != null) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.ErrorCoral
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
                    containerColor = ResolveColors.MintAccent,
                    contentColor = ResolveColors.ForestDeep
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
                Text("Cancel", color = ResolveColors.TextSecondary)
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
    onDismiss: () -> Unit,
    onSubmitCode: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ResolveColors.ForestSecondary
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
                    color = ResolveColors.TextPrimary
                )
                Text(
                    text = "Enter your $expectedLength-digit recovery code to exit Lock Task Mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ResolveColors.TextSecondary
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
                            color = ResolveColors.ErrorCoral
                        )
                    }
                    is RecoveryVerificationResult.Throttled -> {
                        Text(
                            text = "Too many attempts. Try again in ${verificationFeedback.retryAfterSeconds}s.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.AmberWarn
                        )
                    }
                    RecoveryVerificationResult.NotConfigured -> {
                        Text(
                            text = "No recovery code verifier is stored.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.ErrorCoral
                        )
                    }
                    else -> Unit
                }

                Button(
                    onClick = {
                        dismissTextInteractions(focusManager, textToolbar, keyboardController)
                        onSubmitCode(input)
                    },
                    enabled = !isVerifying && input.length == expectedLength,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveColors.MintAccent,
                        contentColor = ResolveColors.ForestDeep
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("recovery_verify_submit_button")
                ) {
                    Text(if (isVerifying) "Verifying…" else "Verify & End Lock")
                }
            }
        }
    }
}
