package com.example.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.designsystem.ResolveColors
import com.example.domain.model.InstalledAppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedAppsPickerSheet(
    installedApps: List<InstalledAppInfo>,
    initialSelectedPackages: Set<String>,
    loadIcon: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onSaveSelection: (Set<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember(initialSelectedPackages) {
        mutableStateOf(initialSelectedPackages)
    }

    val filteredApps = remember(installedApps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) installedApps
        else installedApps.filter {
            it.appName.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Allowed Applications",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ResolveColors.TextPrimary
                        )
                        Text(
                            text = "${selectedPackages.size} selected during Lock Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.TextSecondary
                        )
                    }
                    if (selectedPackages.isNotEmpty()) {
                        TextButton(
                            onClick = { selectedPackages = emptySet() },
                            modifier = Modifier.testTag("clear_allowed_apps_button")
                        ) {
                            Text("Clear", color = ResolveColors.SageAccent)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search apps"
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("allowed_apps_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (installedApps.isEmpty()) "No launchable apps discovered."
                            else "No apps match \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedPackages.contains(app.packageName)
                            AppPickerRow(
                                app = app,
                                isSelected = isSelected,
                                loadIcon = loadIcon,
                                onToggle = {
                                    selectedPackages = if (isSelected) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        dismissTextInteractions(focusManager, textToolbar, keyboardController)
                        onSaveSelection(selectedPackages)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveColors.MintAccent,
                        contentColor = ResolveColors.ForestDeep
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_allowed_apps_button")
                ) {
                    Text(
                        text = "Save Selection (${selectedPackages.size})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledAppInfo,
    isSelected: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
    onToggle: () -> Unit
) {
    var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        iconBitmap = loadIcon(app.packageName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) ResolveColors.MintSoftContainer.copy(alpha = 0.7f)
                else ResolveColors.ForestSurface
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("app_row_${app.packageName}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = "${app.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ResolveColors.ForestSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ResolveIcons.Apps,
                    contentDescription = null,
                    tint = ResolveColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                color = ResolveColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (app.isDialerOrTelecom) "${app.packageName} • System Dialer" else app.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = ResolveColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (isSelected) Icons.Outlined.CheckCircle else ResolveIcons.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) ResolveColors.MintAccent else ResolveColors.TextDisabled
        )
    }
}
