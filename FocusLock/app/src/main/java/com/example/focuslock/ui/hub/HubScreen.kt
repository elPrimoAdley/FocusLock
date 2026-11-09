package com.example.focuslock.ui.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.domain.FocusMode
import com.example.focuslock.ui.components.*
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.launch

@Composable
fun HubScreen(
    viewModel: HubViewModel,
    onOpenDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = colors.nightAbyss,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.nightAbyss)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ==================== 1.7 HUB A : EN-TÊTE + HERO CARD ====================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(colors.elevatedCard)
                                    .border(1.dp, colors.steelLine, CircleShape)
                                    .clickable(onClick = onOpenDrawer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("☰", color = colors.ink, style = typo.bodyPrimary)
                            }
                            Column {
                                Text(uiState.dateLabel, style = typo.bodyPrimary, color = colors.ink)
                            }
                        }

                        // Badge de Série (Focus Streak)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.elevatedCard)
                                .border(1.dp, colors.steelLine, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🔥 ${uiState.streakDays}",
                                style = typo.monoMetric,
                                color = colors.mintClarityStart
                            )
                        }
                    }

                    // Jauge compacte Niveau / XP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(uiState.levelLabel, style = typo.monoTag, color = colors.electricAzureStart)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(colors.deepSlate)
                        ) {
                            val xpRatio = (uiState.currentXp.toFloat() / uiState.nextLevelXp).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(xpRatio)
                                    .fillMaxHeight()
                                    .background(colors.electricAzureGradient)
                            )
                        }
                        Text(
                            text = "${uiState.currentXp}/${uiState.nextLevelXp} XP",
                            style = typo.monoTag,
                            color = colors.mutedInk
                        )
                    }
                }
            }

            // ==================== BLOC A (2.1 - 2.3) : BOUTON TEMPORAIRE ACCESSIBILITÉ ====================
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                var composeMode by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(
                        com.example.focuslock.data.accessibility.FocusAccessibilityService.useComposeOverlay
                    )
                }

                ElevatedCard {
                    Text(
                        text = "PHASE 2 • BLOC A (POC ACCESSIBILITÉ)",
                        style = typo.monoTag,
                        color = colors.electricAzureStart
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Cibles de test : YouTube, Instagram, TikTok, Chrome",
                        style = typo.bodySecondary,
                        color = colors.mutedInk
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (composeMode) "Mode 2.3 : ComposeView" else "Mode 2.2 : Vue Classique",
                            style = typo.bodyPrimary,
                            color = colors.ink
                        )
                        FocusSwitch(
                            checked = composeMode,
                            onCheckedChange = {
                                composeMode = it
                                com.example.focuslock.data.accessibility.FocusAccessibilityService.useComposeOverlay = it
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    MintPillButton(
                        text = "Ouvrir les Réglages d'Accessibilité",
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Hero Card : « 60 / 120 min » avec barre dégradée
            item {
                val progress = (uiState.completedFocusMinutes.toFloat() / uiState.dailyGoalMinutes).coerceIn(0f, 1f)
                ElevatedCard {
                    Text(
                        text = "OBJECTIF DU JOUR",
                        style = typo.monoTag,
                        color = colors.mutedInk
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${uiState.completedFocusMinutes} / ${uiState.dailyGoalMinutes} min",
                            style = typo.screenTitle,
                            color = colors.ink
                        )
                        Text(
                            text = "${(progress * 100).toInt()} % accompli",
                            style = typo.monoMetric,
                            color = colors.electricAzureStart
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(colors.deepSlate)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(colors.electricAzureGradient)
                        )
                    }
                }
            }

            // ==================== 1.9 HUB C (Partie 1) : BANDEAU MALUS ====================
            if (uiState.activeMalus != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.softCoral.copy(alpha = 0.14f))
                            .border(1.dp, colors.softCoral, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("⚠", style = typo.screenTitle, color = colors.softCoral)
                            Column {
                                Text(
                                    text = "MALUS AUTOMATIQUE ACTIF",
                                    style = typo.monoTag,
                                    color = colors.softCoral
                                )
                                Text(
                                    text = uiState.activeMalus ?: "",
                                    style = typo.bodySecondary,
                                    color = colors.ink
                                )
                            }
                        }
                    }
                }
            }

            // ==================== 1.8 HUB B : FOCUS RING & CONTRÔLES ====================
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // FocusRing en Canvas (gradient Electric Azure, bouts arrondis)
                    Box(
                        modifier = Modifier.size(230.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val sweepAngle = (uiState.selectedDurationMinutes.toFloat() / 240f)
                            .coerceIn(0.04f, 1f) * 360f

                        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            val strokePx = 16.dp.toPx()
                            drawArc(
                                color = colors.elevatedCard,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokePx)
                            )
                            drawArc(
                                brush = colors.electricAzureGradient,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.selectedMode.label.uppercase(),
                                style = typo.monoTag,
                                color = colors.electricAzureStart
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatDurationLabel(uiState.selectedDurationMinutes),
                                style = typo.timerDisplay,
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Boutons − / + (5 min à 4 h)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                DurationStepButton(
                                    symbol = "−",
                                    onClick = {
                                        val step = if (uiState.selectedDurationMinutes <= 30) -5 else -15
                                        viewModel.adjustDuration(step)
                                    }
                                )
                                Text("5m – 4h", style = typo.monoTag, color = colors.mutedInk)
                                DurationStepButton(
                                    symbol = "+",
                                    onClick = {
                                        val step = if (uiState.selectedDurationMinutes < 30) 5 else 15
                                        viewModel.adjustDuration(step)
                                    }
                                )
                            }
                        }
                    }

                    // Chips de mode (Focus, Étude Langue, Lecture, Sommeil)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FocusMode.entries.forEach { mode ->
                            val selected = uiState.selectedMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.elevatedCard else colors.deepSlate)
                                    .border(
                                        1.dp,
                                        if (selected) colors.electricAzureStart else colors.steelLine,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectMode(mode) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    style = typo.bodySecondary,
                                    color = if (selected) colors.ink else colors.mutedInk,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Bouton Démarrer le Focus (affiche uniquement un Snackbar en Phase 1)
                    MintPillButton(
                        text = "Démarrer le Focus (${formatDurationLabel(uiState.selectedDurationMinutes)})",
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Contrat « ${uiState.selectedMode.label} » prêt pour ${uiState.selectedDurationMinutes} min (Phase 1)"
                                )
                            }
                        }
                    )
                }
            }

            // ==================== 1.9 HUB C (Partie 2) : QUÊTES P1 & SWITCH DEBUG ====================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("QUÊTES PRIORITAIRES (P1)", style = typo.monoTag, color = colors.mutedInk)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Simuler Malus", style = typo.bodySecondary, color = colors.softCoral)
                        FocusSwitch(
                            checked = uiState.activeMalus != null,
                            onCheckedChange = { viewModel.toggleDebugMalus(it) }
                        )
                    }
                }
            }

            items(uiState.topQuests, key = { it.id }) { quest ->
                ElevatedCard(onClick = { viewModel.toggleQuestCompletion(quest.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PriorityBadge(priority = quest.priority)
                            Text(
                                text = quest.title,
                                style = typo.bodyPrimary,
                                color = colors.ink
                            )
                        }
                        StatusChip(
                            isCompleted = quest.isCompleted,
                            onClick = { viewModel.toggleQuestCompletion(quest.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationStepButton(
    symbol: String,
    onClick: () -> Unit
) {
    val colors = FocusTheme.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(colors.elevatedCard)
            .border(1.dp, colors.steelLine, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = FocusTheme.typography.cardTitleSerif, color = colors.ink)
    }
}

private fun formatDurationLabel(minutes: Int): String {
    return if (minutes < 60) {
        "$minutes min"
    } else {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h}h 00" else "${h}h ${m}m"
    }
}