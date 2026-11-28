package com.example.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.AppCategory
import com.example.domain.model.FocusProfile
import com.example.domain.model.InstalledAppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedAppsPickerSheet(
    installedApps: List<InstalledAppInfo>,
    initialSelectedPackages: Set<String>,
    loadIcon: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onSaveSelection: (Set<String>) -> Unit,
    focusProfiles: List<FocusProfile> = emptyList(),
    onApplyFocusProfile: ((FocusProfile) -> Unit)? = null
) {
    val palette = LocalResolveExtendedPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var selectedPackages by remember(initialSelectedPackages) {
        mutableStateOf(initialSelectedPackages)
    }

    val installedPkgSet = remember(installedApps) {
        installedApps.mapTo(HashSet(installedApps.size)) { it.packageName }
    }
    val missingSelectedPackages = remember(initialSelectedPackages, selectedPackages, installedPkgSet) {
        (initialSelectedPackages + selectedPackages)
            .filter { !installedPkgSet.contains(it) }
            .sorted()
    }

    val filteredMissing = remember(missingSelectedPackages, searchQuery, selectedCategory) {
        if (selectedCategory != null) emptyList()
        else {
            val q = searchQuery.trim()
            if (q.isEmpty()) missingSelectedPackages
            else missingSelectedPackages.filter { it.contains(q, ignoreCase = true) }
        }
    }

    val availableCategories = remember(installedApps) {
        AppCategory.entries.filter { cat -> installedApps.any { it.category == cat } }
    }

    val filteredApps = remember(installedApps, searchQuery, selectedCategory) {
        val q = searchQuery.trim()
        installedApps.filter { app ->
            val matchesCategory = selectedCategory == null || app.category == selectedCategory
            val matchesQuery = q.isEmpty() ||
                app.appName.contains(q, ignoreCase = true) ||
                app.packageName.contains(q, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                            color = palette.textPrimary
                        )
                        Text(
                            text = "${selectedPackages.size} selected during Lock Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                    if (selectedPackages.isNotEmpty()) {
                        TextButton(
                            onClick = { selectedPackages = emptySet() },
                            modifier = Modifier.testTag("clear_allowed_apps_button")
                        ) {
                            Text("Clear", color = palette.accentStrong)
                        }
                    }
                }

                if (missingSelectedPackages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.warnContainer)
                            .border(1.dp, palette.warn.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("missing_allowed_apps_warning_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = palette.warn,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${missingSelectedPackages.size} synced package(s) are not installed on this device.",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.warn
                        )
                    }
                }

                if (focusProfiles.isNotEmpty() && onApplyFocusProfile != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(focusProfiles, key = { it.profileId }) { profile ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    if (!profile.builtIn && profile.allowedPackages.isNotEmpty()) {
                                        selectedPackages = profile.allowedPackages.toSet()
                                    } else if (profile.profileId == "preset_study") {
                                        val studyPkgs = installedApps
                                            .filter { it.category == AppCategory.PRODUCTIVITY || it.isDialerOrTelecom }
                                            .map { it.packageName }
                                            .toSet()
                                        if (studyPkgs.isNotEmpty()) selectedPackages = studyPkgs
                                    } else if (!profile.allowSelectedApps) {
                                        selectedPackages = emptySet()
                                    }
                                    onApplyFocusProfile(profile)
                                },
                                label = {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = palette.surfaceElevated,
                                    labelColor = palette.accentStrong
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false,
                                    borderColor = palette.accent.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.testTag("allowed_apps_profile_${profile.profileId}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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

                if (availableCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("allowed_apps_category_row")
                    ) {
                        item(key = "cat_all") {
                            val selected = selectedCategory == null
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = null },
                                label = { Text("All (${installedApps.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.accent,
                                    selectedLabelColor = palette.onAccent,
                                    containerColor = palette.cardBottom,
                                    labelColor = palette.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = palette.cardBorder,
                                    selectedBorderColor = palette.accent
                                ),
                                modifier = Modifier.testTag("allowed_apps_cat_all")
                            )
                        }
                        items(availableCategories, key = { it.name }) { category ->
                            val selected = selectedCategory == category
                            val count = installedApps.count { it.category == category }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCategory = if (selected) null else category
                                },
                                label = { Text("${category.displayName} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.accent,
                                    selectedLabelColor = palette.onAccent,
                                    containerColor = palette.cardBottom,
                                    labelColor = palette.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = palette.cardBorder,
                                    selectedBorderColor = palette.accent
                                ),
                                modifier = Modifier.testTag("allowed_apps_cat_${category.name.lowercase()}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredApps.isEmpty() && filteredMissing.isEmpty()) {
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
                            color = palette.textSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredMissing, key = { "missing_$it" }) { pkg ->
                            val isSelected = selectedPackages.contains(pkg)
                            MissingAppPickerRow(
                                packageName = pkg,
                                isSelected = isSelected,
                                onToggle = {
                                    selectedPackages = if (isSelected) {
                                        selectedPackages - pkg
                                    } else {
                                        selectedPackages + pkg
                                    }
                                }
                            )
                        }

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
                        containerColor = palette.primaryButton,
                        contentColor = palette.onPrimaryButton
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
private fun MissingAppPickerRow(
    packageName: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) palette.warnContainer.copy(alpha = 0.65f)
                else palette.background.copy(alpha = 0.65f)
            )
            .border(1.dp, palette.warn.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("missing_app_row_$packageName"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.warnContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = palette.warn
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = packageName,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Not installed on this device",
                style = MaterialTheme.typography.labelMedium,
                color = palette.warn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (isSelected) Icons.Outlined.CheckCircle else ResolveIcons.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) palette.warn else palette.textMuted
        )
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledAppInfo,
    isSelected: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
    onToggle: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        iconBitmap = loadIcon(app.packageName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) palette.surfaceElevated
                else palette.cardBottom
            )
            .border(
                width = 1.dp,
                color = if (isSelected) palette.accentSecondary.copy(alpha = 0.45f) else palette.cardBorder,
                shape = RoundedCornerShape(16.dp)
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
                    .background(palette.cardTop),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ResolveIcons.Apps,
                    contentDescription = null,
                    tint = palette.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(app.category.displayName)
                    if (app.isDialerOrTelecom) append(" • System Dialer")
                    append(" • ")
                    append(app.packageName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (isSelected) Icons.Outlined.CheckCircle else ResolveIcons.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) palette.success else palette.textMuted
        )
    }
}
