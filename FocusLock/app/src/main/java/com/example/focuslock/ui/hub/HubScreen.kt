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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.BuildConfig
import com.example.focuslock.FocusLockApplication
import com.example.focuslock.domain.FocusMode
import com.example.focuslock.domain.SessionType
import com.example.focuslock.domain.toSessionType
import com.example.focuslock.ui.components.AppSelectorCard
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.FocusModalSheet
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.components.PriorityBadge
import com.example.focuslock.ui.components.StatusChip
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration

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

    var showStartFocusSheet by remember { mutableStateOf(false) }
    var validationNoteText by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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
            // ==================== 3.23 EN-TÊTE RÉEL (SÉRIE, GRADE & XP) ====================
            item {
                val lp = uiState.levelProgress
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
                                Text(uiState.greeting, style = typo.cardTitleSerif, color = colors.ink)
                                Text(uiState.dateLabel, style = typo.bodySecondary, color = colors.mutedInk)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.elevatedCard)
                                .border(1.dp, colors.steelLine, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🔥 ${uiState.streakDays} JOURS",
                                style = typo.monoMetric,
                                color = colors.mintClarityStart
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Niv. ${lp.level} • ${lp.gradeName}",
                            style = typo.monoTag,
                            color = colors.electricAzureStart
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(colors.deepSlate)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(lp.progressRatio.coerceIn(0.03f, 1f))
                                    .fillMaxHeight()
                                    .background(colors.electricAzureGradient)
                            )
                        }
                        Text(
                            text = if (lp.nextLevelMinXp != null) {
                                "${lp.totalXp}/${lp.nextLevelMinXp} XP"
                            } else {
                                "${lp.totalXp} XP"
                            },
                            style = typo.monoTag,
                            color = colors.mutedInk
                        )
                    }
                }
            }

            // ==================== 3.23 HERO CARD : OBJECTIF DU JOUR ====================
            item {
                val goal = uiState.dailyGoalMinutes.coerceAtLeast(1)
                val progress = (uiState.completedFocusMinutes.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
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

            // ==================== 3.23 BANDEAU MALUS RÉEL ====================
            if (uiState.activeMalusBanner != null) {
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
                                    text = "PÉNALITÉ DISCIPLINAIRE ACTIVE",
                                    style = typo.monoTag,
                                    color = colors.softCoral
                                )
                                Text(
                                    text = uiState.activeMalusBanner ?: "",
                                    style = typo.bodySecondary,
                                    color = colors.ink
                                )
                            }
                        }
                    }
                }
            }

            // ==================== 3.22 CARTE DE SYNTHÈSE DE FIN DE SESSION (OPTIONNELLE) ====================
            val finishedSession = uiState.sessionAwaitingValidationNote
            if (finishedSession != null) {
                item {
                    ElevatedCard {
                        Text(
                            text = "✓ CONTRAT D'APPRENTISSAGE TERMINÉ",
                            style = typo.monoTag,
                            color = colors.mintClarityStart
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = finishedSession.label,
                            style = typo.cardTitleSerif,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = validationNoteText,
                            onValueChange = { validationNoteText = it },
                            placeholder = { Text("Note rapide (1–2 phrases sur ce que tu as étudié)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        MintPillButton(
                            text = "Archiver la note de session",
                            onClick = {
                                viewModel.submitSessionValidationNote(finishedSession, validationNoteText)
                                validationNoteText = ""
                            }
                        )
                    }
                }
            }

            // ==================== 3.21 & 3.22 FOCUS RING & CONTRAT DE TEMPS ====================
            item {
                val activeSession = uiState.activeFocusSession
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(230.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val sweepAngle = if (activeSession != null) {
                            val totalSec = Duration.between(activeSession.startedAt, activeSession.plannedEndAt)
                                .seconds.coerceAtLeast(1L)
                            (uiState.activeSessionRemainingSeconds.toFloat() / totalSec.toFloat())
                                .coerceIn(0.03f, 1f) * 360f
                        } else {
                            (uiState.selectedDurationMinutes.toFloat() / 240f)
                                .coerceIn(0.04f, 1f) * 360f
                        }

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
                                text = if (activeSession != null) {
                                    "🔒 CONTRAT EN COURS"
                                } else {
                                    uiState.selectedMode.label.uppercase()
                                },
                                style = typo.monoTag,
                                color = if (activeSession != null) colors.mintClarityStart else colors.electricAzureStart
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (activeSession != null) {
                                    formatCountdownSeconds(uiState.activeSessionRemainingSeconds)
                                } else {
                                    formatDurationLabel(uiState.selectedDurationMinutes)
                                },
                                style = typo.timerDisplay.copy(
                                    fontSize = if (activeSession != null) 36.sp else 42.sp
                                ),
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (activeSession != null) {
                                Text(
                                    text = activeSession.label,
                                    style = typo.bodySecondary,
                                    color = colors.mutedInk,
                                    maxLines = 1
                                )
                            } else {
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
                                    .clickable(enabled = activeSession == null) {
                                        viewModel.selectMode(mode)
                                    }
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

                    // 3.21 & 3.22 : Bouton Démarrer (aucun bouton "Arrêter" en release)
                    MintPillButton(
                        text = if (activeSession != null) {
                            "Session verrouillée (${formatCountdownSeconds(uiState.activeSessionRemainingSeconds)})"
                        } else {
                            "Démarrer le Focus (${formatDurationLabel(uiState.selectedDurationMinutes)})"
                        },
                        enabled = activeSession == null,
                        onClick = { showStartFocusSheet = true }
                    )
                }
            }

            // ==================== 3.23 APERÇU DES 3 QUÊTES P1 DU JOUR ====================
            item {
                Text(
                    text = "QUÊTES PRIORITAIRES DU JOUR (P1)",
                    style = typo.monoTag,
                    color = colors.mutedInk
                )
            }

            if (uiState.topP1Quests.isEmpty()) {
                item {
                    ElevatedCard {
                        Text(
                            text = "Aucune quête P1 planifiée pour aujourd'hui. Engage une quête critique dans la section Quêtes & Tâches pour maintenir ta série et débloquer des Crédits de Récréation.",
                            style = typo.bodySecondary,
                            color = colors.mutedInk
                        )
                    }
                }
            } else {
                items(uiState.topP1Quests, key = { it.id }) { quest ->
                    val isDone = quest.completedAt != null
                    ElevatedCard(
                        onClick = if (!isDone) {
                            { viewModel.completeP1QuestFromHub(quest) }
                        } else {
                            null
                        }
                    ) {
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
                                    color = if (isDone) colors.mutedInk else colors.ink
                                )
                            }
                            StatusChip(
                                isCompleted = isDone,
                                onClick = if (!isDone) {
                                    { viewModel.completeP1QuestFromHub(quest) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

            // Outils de test rapides (uniquement en build debug, totalement absents en release — 3.25)
            if (BuildConfig.DEBUG) {
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val appContainer = remember {
                        (context.applicationContext as FocusLockApplication).container
                    }
                    ElevatedCard {
                        Text(
                            text = "OUTILS DEBUG (ABSENTS EN RELEASE)",
                            style = typo.monoTag,
                            color = colors.electricAzureStart
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.deepSlate)
                                    .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                    .clickable {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        )
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Accessibilité", style = typo.bodySecondary, color = colors.ink)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.deepSlate)
                                    .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                    .clickable {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        )
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("UsageStats", style = typo.bodySecondary, color = colors.ink)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.deepSlate)
                                    .border(1.dp, colors.softCoral, RoundedCornerShape(10.dp))
                                    .clickable {
                                        scope.launch {
                                            DebugScenarioSeeder.clearAllScenarios(appContainer)
                                            snackbarHostState.showSnackbar("Sessions et règles de test réinitialisées")
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Reset Test", style = typo.bodySecondary, color = colors.softCoral)
                            }
                        }
                    }
                }
            }
        }
    }

    // 3.21 : Feuille modale de lancement d'un Contrat de Temps Focus
    if (showStartFocusSheet) {
        StartFocusModalSheet(
            initialMode = uiState.selectedMode,
            initialDurationMinutes = uiState.selectedDurationMinutes,
            selectableApps = uiState.selectableApps,
            appsLoader = viewModel.appsLoader,
            onDismiss = { showStartFocusSheet = false },
            onStartSession = { mode, label, durationMins, whitelist ->
                viewModel.startFocusSession(
                    mode = mode,
                    customLabel = label,
                    durationMinutes = durationMins,
                    whitelistedPackages = whitelist,
                    onStarted = { showStartFocusSheet = false }
                )
            },
            onCycleCategory = { pkg, cat ->
                viewModel.updateAppCategory(pkg, cat)
            }
        )
    }
}

@Composable
private fun StartFocusModalSheet(
    initialMode: FocusMode,
    initialDurationMinutes: Int,
    selectableApps: List<com.example.focuslock.data.apps.SelectableAppInfo>,
    appsLoader: com.example.focuslock.data.apps.InstalledAppsLoader,
    onDismiss: () -> Unit,
    onStartSession: (
        mode: FocusMode,
        label: String,
        durationMinutes: Int,
        whitelistedPackages: Set<String>
    ) -> Unit,
    onCycleCategory: (String, com.example.focuslock.domain.AppCategory) -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography

    var mode by remember { mutableStateOf(initialMode) }
    var durationMinutes by remember { mutableIntStateOf(initialDurationMinutes) }
    var label by remember(initialMode) {
        mutableStateOf(
            when (initialMode) {
                FocusMode.LANGUAGE -> "Étude Langue — Tablette"
                FocusMode.READING -> "Lecture hors-écran"
                FocusMode.SLEEP -> "Repos & Sommeil"
                FocusMode.FOCUS -> "Travail Profond"
            }
        )
    }
    var whitelistedPackages by remember { mutableStateOf(emptySet<String>()) }

    val mappedSessionType = mode.toSessionType()

    FocusModalSheet(
        title = "Sceller un Contrat de Temps",
        saveButtonText = "Verrouiller pour ${formatDurationLabel(durationMinutes)}",
        rightActionSymbol = "🔒",
        onDismissRequest = onDismiss,
        onSaveClick = {
            onStartSession(mode, label, durationMinutes, whitelistedPackages)
        }
    ) {
        // Carte 1 : Mode & Type de Session (DEEP_WORK vs ACTIVE_LEARNING)
        ElevatedCard {
            Text("MODE & NATURE DU CONTRAT", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(10.dp))
            FocusMode.entries.chunked(2).forEach { rowModes ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowModes.forEach { itemMode ->
                        val active = mode == itemMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) colors.deepSlate else colors.nightAbyss)
                                .border(
                                    1.dp,
                                    if (active) colors.electricAzureStart else colors.steelLine,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    mode = itemMode
                                    label = itemMode.label
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = itemMode.label,
                                style = typo.bodySecondary,
                                color = if (active) colors.electricAzureStart else colors.mutedInk
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (mappedSessionType) {
                    SessionType.ACTIVE_LEARNING -> "Type : ACTIVE_LEARNING (Apprentissage hors-écran avec note de fin)"
                    SessionType.DEEP_WORK -> "Type : DEEP_WORK (Concentration profonde ininterrompue)"
                    SessionType.FOCUS_DEBT_MALUS -> "Type : FOCUS_DEBT_MALUS"
                },
                style = typo.monoTag,
                color = colors.mintClarityStart
            )
        }

        // Carte 2 : Libellé & Durée du Contrat
        ElevatedCard {
            Text("LIBELLÉ & DURÉE", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Intitulé affiché sur l'écran de verrouillage") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.electricAzureStart,
                    unfocusedBorderColor = colors.steelLine,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DurationStepButton(
                    symbol = "−",
                    onClick = {
                        val step = if (durationMinutes <= 30) -5 else -15
                        durationMinutes = (durationMinutes + step).coerceIn(5, 240)
                    }
                )
                Text(
                    text = formatDurationLabel(durationMinutes),
                    style = typo.screenTitle,
                    color = colors.ink
                )
                DurationStepButton(
                    symbol = "+",
                    onClick = {
                        val step = if (durationMinutes < 30) 5 else 15
                        durationMinutes = (durationMinutes + step).coerceIn(5, 240)
                    }
                )
            }
        }

        // Carte 3 : Liste Blanche Sélective de Session (ex. Spotify, Dictionnaire)
        ElevatedCard {
            Text("LISTE BLANCHE DE LA SESSION (OPTIONNEL)", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Les applications cochées ci-dessous resteront accessibles pendant la session (le Téléphone et l'Horloge sont toujours autorisés).",
                style = typo.bodySecondary,
                color = colors.mutedInk
            )
        }

        AppSelectorCard(
            apps = selectableApps,
            selectedPackages = whitelistedPackages,
            appsLoader = appsLoader,
            onTogglePackage = { pkg ->
                whitelistedPackages = if (pkg in whitelistedPackages) {
                    whitelistedPackages - pkg
                } else {
                    whitelistedPackages + pkg
                }
            },
            onCycleCategory = onCycleCategory
        )
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

private fun formatCountdownSeconds(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}