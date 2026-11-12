package com.example.focuslock.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.data.accessibility.AccessDecisionEvaluator
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.FocusModalSheet
import com.example.focuslock.ui.components.FocusSwitch
import com.example.focuslock.ui.components.MindfulPinGateContent
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.navigation.Destination
import com.example.focuslock.ui.navigation.TopSectionBar
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter

@Composable
fun ProfileSecurityScreen(
    viewModel: ProfileSecurityViewModel,
    onOpenDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var customBlockMsgInput by remember(uiState.gamification.defaultCustomBlockMessage) {
        mutableStateOf(uiState.gamification.defaultCustomBlockMessage)
    }
    var newPinInput by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = colors.nightAbyss,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.nightAbyss)
                .padding(innerPadding)
                .systemBarsPadding()
        ) {
            TopSectionBar(title = Destination.ProfileSecurity.title, onOpenDrawer = onOpenDrawer)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ==================== 1. SANTÉ SYSTÈME SAMSUNG (S23 / S26) ====================
                item {
                    val health = uiState.health
                    ElevatedCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SANTÉ DU SYSTÈME SAMSUNG (S23 / S26)",
                                style = typo.monoTag,
                                color = colors.electricAzureStart
                            )
                            Text(
                                text = "Actualiser",
                                style = typo.monoTag,
                                color = colors.mintClarityStart,
                                modifier = Modifier.clickable { viewModel.refreshHealthStatus() }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        HealthIndicatorRow(
                            label = "1. Détecteur d'Accessibilité",
                            isOk = health.isAccessibilityServiceEnabled,
                            statusText = if (health.isAccessibilityServiceEnabled) "Actif" else "Inactif (Requis)",
                            onClick = {
                                AccessDecisionEvaluator.grantTemporarySettingsPass(viewModel.clock.millis())
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        HealthIndicatorRow(
                            label = "2. Accès aux données d'utilisation",
                            isOk = health.isUsageStatsGranted,
                            statusText = if (health.isUsageStatsGranted) "Accordé" else "Manquant",
                            onClick = {
                                AccessDecisionEvaluator.grantTemporarySettingsPass(viewModel.clock.millis())
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        HealthIndicatorRow(
                            label = "3. Batterie (« Jamais en veille »)",
                            isOk = health.isBatteryOptimizationIgnored,
                            statusText = if (health.isBatteryOptimizationIgnored) "Exempté" else "Restreint",
                            onClick = {
                                AccessDecisionEvaluator.grantTemporarySettingsPass(viewModel.clock.millis())
                                runCatching {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }.onFailure {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val heartbeatStr = health.lastHeartbeatInstant?.let {
                            it.atZone(uiState.zoneId).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"))
                        } ?: "En attente du 1er événement"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("4. Dernier battement (Heartbeat)", style = typo.bodySecondary, color = colors.mutedInk)
                            Text(heartbeatStr, style = typo.monoTag, color = colors.ink)
                        }
                    }
                }

                // ==================== 2. PRÉFÉRENCES DE BLOCAGE STRICT ====================
                item {
                    val g = uiState.gamification
                    ElevatedCard {
                        Text(
                            text = "PRÉFÉRENCES DE BLOCAGE STRICT",
                            style = typo.monoTag,
                            color = colors.mutedInk
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        StrictToggleRow(
                            title = "Bloquer les fenêtres flottantes (Pop-up)",
                            subtitle = "Neutralise l'affichage pop-up et l'écran scindé sur Samsung One UI",
                            checked = g.blockFloatingWindows,
                            onCheckedChange = { viewModel.setBlockFloatingWindows(it) }
                        )
                        HorizontalDivider(color = colors.steelLine, modifier = Modifier.padding(vertical = 10.dp))

                        StrictToggleRow(
                            title = "Bloquer l'arrêt forcé & la désinstallation",
                            subtitle = "Verrouille l'accès aux Infos de l'application pendant une session stricte",
                            checked = g.blockUninstallAndSettings,
                            onCheckedChange = { viewModel.setBlockUninstallAndSettings(it) }
                        )
                        HorizontalDivider(color = colors.steelLine, modifier = Modifier.padding(vertical = 10.dp))

                        StrictToggleRow(
                            title = "Sécuriser après redémarrage",
                            subtitle = "Maintient les verrous actifs dès l'allumage du téléphone",
                            checked = g.secureAfterRestart,
                            onCheckedChange = { viewModel.setSecureAfterRestart(it) }
                        )
                        HorizontalDivider(color = colors.steelLine, modifier = Modifier.padding(vertical = 10.dp))

                        // Réglage du délai Mindful Edits (10s, 20s, 30s, 60s)
                        Text("Modifications conscientes (Mindful edits)", style = typo.bodyPrimary, color = colors.ink)
                        Text(
                            text = "Délai d'attente obligatoire avant de modifier une règle : ${g.mindfulEditDelaySeconds}s",
                            style = typo.bodySecondary,
                            color = colors.mutedInk
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(10, 20, 30, 60).forEach { sec ->
                                val active = g.mindfulEditDelaySeconds == sec
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) colors.electricAzureStart else colors.deepSlate)
                                        .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setMindfulEditDelaySeconds(sec) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        style = typo.monoMetric,
                                        color = if (active) colors.nightAbyss else colors.ink
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = colors.steelLine, modifier = Modifier.padding(vertical = 10.dp))

                        // Réglage du Bris de glace auto-refermant (5 min, 10 min, 15 min)
                        Text("Déverrouillage Mode Strict (Auto-relock)", style = typo.bodyPrimary, color = colors.ink)
                        Text(
                            text = "Se reverrouille automatiquement après ${g.strictAutoRelockMinutes} min",
                            style = typo.bodySecondary,
                            color = colors.mutedInk
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 10, 15).forEach { mins ->
                                val active = g.strictAutoRelockMinutes == mins
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) colors.mintClarityStart else colors.deepSlate)
                                        .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setStrictAutoRelockMinutes(mins) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${mins} min",
                                        style = typo.monoMetric,
                                        color = if (active) colors.mintOnButton else colors.ink
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 3. CODE PIN INDÉPENDANT (HACHAGE PBKDF2) ====================
                item {
                    val isConfigured = uiState.gamification.isPinConfigured
                    ElevatedCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CODE PIN STRICT (PBKDF2)", style = typo.monoTag, color = colors.mutedInk)
                            Text(
                                text = if (isConfigured) "✓ Configuré" else "Non défini",
                                style = typo.monoTag,
                                color = if (isConfigured) colors.mintClarityStart else colors.softCoral
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Indépendant du code du téléphone et sans biométrie. Haché avec PBKDF2-HMAC-SHA256 (120 000 itérations).",
                            style = typo.bodySecondary,
                            color = colors.mutedInk
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newPinInput,
                                onValueChange = { input ->
                                    if (input.length <= 4 && input.all { it.isDigit() }) {
                                        newPinInput = input
                                    }
                                },
                                label = { Text("Nouveau PIN (4 chiffres)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.mintClarityStart)
                                    .clickable {
                                        viewModel.setOrUpdatePin(newPinInput)
                                        newPinInput = ""
                                    }
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = if (isConfigured) "Changer" else "Activer",
                                    style = typo.monoTag,
                                    color = colors.mintOnButton
                                )
                            }
                        }
                    }
                }

                // ==================== 4. PROFIL DISCIPLINAIRE & MESSAGE D'OVERLAY ====================
                item {
                    val lp = uiState.levelProgress
                    val g = uiState.gamification
                    ElevatedCard {
                        Text("PROFIL DISCIPLINAIRE", style = typo.monoTag, color = colors.electricAzureStart)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Niveau ${lp.level} • ${lp.gradeName}",
                            style = typo.screenTitle,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "XP Totale : ${lp.totalXp} XP | Série actuelle : ${g.currentStreakDays}j | Record : ${g.bestStreakDays}j",
                            style = typo.monoMetric.copy(fontSize = 12.sp),
                            color = colors.mintClarityStart
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Objectif de Focus quotidien :", style = typo.bodySecondary, color = colors.mutedInk)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(60, 90, 120, 180).forEach { goalMins ->
                                val active = g.dailyFocusGoalMinutes == goalMins
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) colors.electricAzureStart else colors.deepSlate)
                                        .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                        .clickable { viewModel.updateDailyFocusGoal(goalMins) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${goalMins}m",
                                        style = typo.monoMetric,
                                        color = if (active) colors.nightAbyss else colors.ink
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = customBlockMsgInput,
                            onValueChange = { customBlockMsgInput = it },
                            label = { Text("Message par défaut de l'écran de verrouillage") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        MintPillButton(
                            text = "Enregistrer le message par défaut",
                            onClick = { viewModel.updateDefaultCustomBlockMessage(customBlockMsgInput) }
                        )
                    }
                }
            }
        }
    }

    // Sas Mindful Edits + Clavier PIN Circulaire pour toute modification assouplissante
    val gate = uiState.pendingGateAction
    if (gate != null) {
        FocusModalSheet(
            title = "Sas de Sécurité Stricte",
            saveButtonText = "Fermer et conserver la protection",
            rightActionSymbol = "🔒",
            onDismissRequest = { viewModel.dismissGate() },
            onSaveClick = { viewModel.dismissGate() }
        ) {
            MindfulPinGateContent(
                actionTitle = gate.title,
                actionDescription = gate.description,
                gamificationState = uiState.gamification,
                gamificationStore = viewModel.gamificationStore,
                clock = viewModel.clock,
                onAuthorized = gate.onAuthorized,
                onCancel = { viewModel.dismissGate() }
            )
        }
    }
}

@Composable
private fun HealthIndicatorRow(
    label: String,
    isOk: Boolean,
    statusText: String,
    onClick: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val badgeColor = if (isOk) colors.mintClarityStart else colors.softCoral

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.deepSlate)
            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = typo.bodyPrimary, color = colors.ink)
        Text(
            text = if (isOk) "● $statusText" else "⚠ $statusText (Configurer)",
            style = typo.monoTag,
            color = badgeColor
        )
    }
}

@Composable
private fun StrictToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = typo.bodyPrimary, color = colors.ink)
            Text(subtitle, style = typo.bodySecondary, color = colors.mutedInk)
        }
        FocusSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
