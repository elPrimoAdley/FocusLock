package com.example.focuslock.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focuslock.data.apps.InstalledAppsLoader
import com.example.focuslock.data.apps.SelectableAppInfo
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.ui.theme.FocusTheme

@Composable
fun AppSelectorCard(
    apps: List<SelectableAppInfo>,
    selectedPackages: Set<String>,
    appsLoader: InstalledAppsLoader,
    onTogglePackage: (String) -> Unit,
    onCycleCategory: ((String, AppCategory) -> Unit)? = null
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery, selectedPackages) {
        val query = searchQuery.trim().lowercase()
        val matching = if (query.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
        // Place les applications déjà sélectionnées en tête de liste
        matching.sortedWith(
            compareByDescending<SelectableAppInfo> { it.packageName in selectedPackages }
                .thenBy { it.label.lowercase() }
        )
    }

    ElevatedCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("APPLICATIONS CIBLES", style = typo.monoTag, color = colors.mutedInk)
            Text(
                text = "${selectedPackages.size} sélectionnée(s)",
                style = typo.monoMetric,
                color = colors.mintClarityStart
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher parmi ${apps.size} applications...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.electricAzureStart,
                unfocusedBorderColor = colors.steelLine,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Liste fenêtrée pour un rendu fluide à l'intérieur du FocusModalSheet
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val visibleSlice = filteredApps.take(40)
            visibleSlice.forEach { app ->
                key(app.packageName) {
                    val isSelected = app.packageName in selectedPackages
                    AppSelectorRow(
                        app = app,
                        isSelected = isSelected,
                        appsLoader = appsLoader,
                        onToggle = { onTogglePackage(app.packageName) },
                        onCategoryClick = onCycleCategory?.let { callback ->
                            {
                                val nextCategory = when (app.category) {
                                    AppCategory.NEUTRAL -> AppCategory.DISTRACTING
                                    AppCategory.DISTRACTING -> AppCategory.PRODUCTIVE
                                    AppCategory.PRODUCTIVE -> AppCategory.NEUTRAL
                                }
                                callback(app.packageName, nextCategory)
                            }
                        }
                    )
                }
            }

            if (filteredApps.size > visibleSlice.size) {
                Text(
                    text = "+ ${filteredApps.size - visibleSlice.size} autres applications (utilise la recherche ci-dessus)",
                    style = typo.monoTag,
                    color = colors.mutedInk,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AppSelectorRow(
    app: SelectableAppInfo,
    isSelected: Boolean,
    appsLoader: InstalledAppsLoader,
    onToggle: () -> Unit,
    onCategoryClick: (() -> Unit)?
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography

    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = appsLoader.getAppIcon(app.packageName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.deepSlate)
            .border(
                width = 1.dp,
                color = if (isSelected) colors.electricAzureStart else colors.steelLine,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icône mise en cache hors thread UI (ou monogramme de secours)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.elevatedCard),
            contentAlignment = Alignment.Center
        ) {
            val bmp = iconBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.label,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    text = app.label.take(2).uppercase(),
                    style = typo.monoMetric,
                    color = colors.electricAzureStart
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = typo.bodyPrimary,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = typo.monoTag.copy(fontSize = 10.sp),
                color = colors.mutedInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        AppCategoryBadge(
            category = app.category,
            onClick = onCategoryClick
        )

        // Pastille de sélection
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) colors.mintClarityStart else Color.Transparent)
                .border(
                    1.5.dp,
                    if (isSelected) colors.mintClarityStart else colors.mutedInk,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text("✓", color = colors.mintOnButton, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AppCategoryBadge(
    category: AppCategory,
    onClick: (() -> Unit)? = null
) {
    val colors = FocusTheme.colors
    val (label, badgeColor) = when (category) {
        AppCategory.DISTRACTING -> "Distrayant" to colors.softCoral
        AppCategory.NEUTRAL -> "Neutre" to colors.electricAzureStart
        AppCategory.PRODUCTIVE -> "Productif" to colors.mintClarityStart
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(badgeColor.copy(alpha = 0.14f))
            .border(1.dp, badgeColor.copy(alpha = 0.6f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = FocusTheme.typography.monoTag.copy(fontSize = 10.sp),
            color = badgeColor
        )
    }
}