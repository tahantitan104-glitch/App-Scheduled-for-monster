package com.example.presentation.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.presentation.components.KeyboardTypingText
import com.example.presentation.components.ResolveRunnerLogo
import com.example.presentation.components.resolveGlassCard

@Composable
fun AboutResolveScreen(
    reducedMotion: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("about_resolve_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("about_resolve_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textPrimary
                        )
                    }
                    Text(
                        text = "About RESOLVE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textPrimary
                    )
                }
            }

            // Hero Header Card with centered Runner Logo & typed slogan + developer credit
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(
                            cornerRadius = 16.dp,
                            borderColor = palette.cardBorder
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            ResolveRunnerLogo(
                                size = 56.dp,
                                animateRunner = true,
                                reducedMotion = reducedMotion,
                                testTagName = "about_runner_logo"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "RESOLVE",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.6.sp
                                    ),
                                    color = palette.textPrimary
                                )
                                KeyboardTypingText(
                                    fullText = "Your distraction killing is resolve duty.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = palette.textSecondary,
                                    startTyping = true,
                                    startDelayMillis = 80,
                                    typingDurationMillis = 820,
                                    reducedMotion = reducedMotion
                                )
                            }
                        }

                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = palette.champagneStrong,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(palette.champagneContainer)
                                .border(1.dp, palette.champagne.copy(alpha = 0.35f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    KeyboardTypingText(
                        fullText = "Developed by Tahan Official",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.35.sp
                        ),
                        color = palette.textPrimary,
                        startTyping = true,
                        startDelayMillis = 920,
                        typingDurationMillis = 680,
                        reducedMotion = reducedMotion,
                        modifier = Modifier.testTag("about_developer_credit")
                    )
                }
            }

            item {
                AboutInfoCard(
                    title = "1. About RESOLVE",
                    body = "RESOLVE is a calm, offline-first digital discipline shield designed to help you reclaim uninterrupted attention without nagging subscriptions or noisy feeds."
                )
            }

            item {
                AboutInfoCard(
                    title = "2. What RESOLVE Offers",
                    body = "• Instant Lock: Restrict distracting apps immediately for your chosen focus timer.\n" +
                        "• Delayed Lock: Enjoy a fixed free-use window followed by automatic Focus Lock.\n" +
                        "• Schedule Lock: Automate One-Time, Daily, or Custom Weekly focus sessions.\n" +
                        "• App Lock: Guard specific sensitive or distracting apps using PIN, Pattern, or 6-Cell Sequence.\n" +
                        "• Focus Notification Control: Quiet distracting interruptions during focus locks while preserving your prior DND settings."
                )
            }

            item {
                AboutInfoCard(
                    title = "3. How RESOLVE Works",
                    body = "Your phone's Accessibility Service (Focus Guard) and Overlay permission let RESOLVE detect when a restricted app is opened and bring the Focus Lock or App Lock screen forward smoothly. Persistent session deadlines survive device restarts and process stops."
                )
            }

            item {
                AboutInfoCard(
                    title = "4. How to Use RESOLVE Effectively",
                    body = "• Allowlist only essential tools (like Dialer, Calculator, or Notes) before starting a lock.\n" +
                        "• Store your Recovery Code safely so you can unlock early in genuine emergencies.\n" +
                        "• Use Delayed Lock when you want a mindful finish-up countdown before focus begins."
                )
            }

            item {
                AboutInfoCard(
                    title = "5. Practical Tips & Multi-Device Sync",
                    body = "Connect a Google account on two or more devices only if you want active focus locks synced automatically across them. Mascots, Allowed Apps, Recovery Codes, Screen Time, and Insights stay strictly private on each phone."
                )
            }

            item {
                AboutInfoCard(
                    title = "6. Developer Information",
                    body = "Developed by Tahan Official\n" +
                        "Application Package: ${BuildConfig.APPLICATION_ID}\n" +
                        "Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
            }
        }
    }
}

@Composable
private fun AboutInfoCard(
    title: String,
    body: String
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = palette.textPrimary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            lineHeight = 20.sp
        )
    }
}
