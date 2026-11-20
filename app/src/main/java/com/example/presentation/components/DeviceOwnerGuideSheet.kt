package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.DeviceOwnerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceOwnerGuideSheet(
    deviceOwnerState: DeviceOwnerState,
    onDismiss: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBottom
    ) {
        ProvideSafeTextToolbar {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Text(
                text = "Device Management Setup",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary
            )
            Text(
                text = when (deviceOwnerState) {
                    DeviceOwnerState.DEVICE_OWNER_ACTIVE ->
                        "Status: Device Owner Active. Resolve can enforce Android Lock Task Mode and restrict non-allowlisted apps."
                    DeviceOwnerState.DEVICE_ADMIN_ONLY ->
                        "Status: Device Admin Only. Standard Device Admin cannot enable Lock Task allowlisting; full Device Owner provisioning is required."
                    DeviceOwnerState.NOT_PROVISIONED ->
                        "Status: Not Provisioned. Android requires Device Owner authority (not a normal permission toggle) to lock the device to selected apps."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textSecondary
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.background.copy(alpha = 0.7f))
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Provision via ADB (personal device)",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent
                )
                Text(
                    text = "adb shell dpm set-device-owner com.resolve.focus/com.example.devicepolicy.ResolveDeviceAdminReceiver",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = palette.textPrimary
                )
            }

            Text(
                text = "Note: Android only permits setting a Device Owner when no user accounts are temporarily present on the device (or during fresh setup). Package ID change from any previous app requires provisioning this package separately.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            Spacer(Modifier.height(4.dp))
            }
        }
    }
}
