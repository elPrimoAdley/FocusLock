package com.example.focuslock.ui.quests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.BuildConfig
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.isCompletedToday
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.FocusModalSheet
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.components.PriorityBadge
import com.example.focuslock.ui.navigation.Destination
import com.example.focuslock.ui.navigation.TopSectionBar
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.flow.collectLatest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun QuestsScreen(
    viewModel: QuestsViewModel,
    onOpenDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val snackbarHostState = remember { SnackbarHostState() }

    // États des feuilles modales (3.11 & 3.14)
    var showQuestSheet by remember { mutableStateOf(false) }
    var questBeingEdited by remember { mutableStateOf<QuestTask?>(null) }

    var showRoutineSheet by remember { mutableStateOf(false) }
    var routineBeingEdited by remember { mutableStateOf<RoutineWithChecklistUi?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
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
            TopSectionBar(title = Destination.Quests.title, onOpenDrawer = onOpenDrawer)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==================== 3.13 PANNEAU XP, GRADE ET PÉNALITÉS ====================
                item {
                    XpAndPenaltiesPanel(uiState = uiState)
                }

                // Bandeau de contournement Debug (uniquement si BuildConfig.DEBUG && EditGuard a bloqué)
                val debugBypass = uiState.pendingDebugBypass
                if (BuildConfig.DEBUG && debugBypass != null) {
                    item {
                        DebugBypassBanner(
                            action = debugBypass,
                            onDismiss = { viewModel.dismissDebugBypassPrompt() }
                        )
                    }
                }

                // Sélecteur segmenté : Quêtes (P1-P3) vs Routines Hors-Écran (3.14)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.deepSlate)
                            .border(1.dp, colors.steelLine, RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuestsTab.entries.forEach { tab ->
                            val selected = uiState.selectedTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.elevatedCard else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selected) colors.electricAzureStart else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectTab(tab) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    style = typo.bodyPrimary,
                                    color = if (selected) colors.ink else colors.mutedInk
                                )
                            }
                        }
                    }
                }

                if (uiState.selectedTab == QuestsTab.QUESTS) {
                    // ==================== 3.10 FILTRES & LISTE DES TÂCHES ====================
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MintPillButton(
                                text = "+ Nouvelle Quête Disciplinaire",
                                onClick = {
                                    questBeingEdited = null
                                    showQuestSheet = true
                                }
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(QuestFilter.entries) { filter ->
                                    val isSelected = uiState.selectedFilter == filter
                                    val count = uiState.countsByFilter[filter] ?: 0
                                    val isOverdueFilter = filter == QuestFilter.OVERDUE && count > 0
                                    val borderColor = when {
                                        isSelected && isOverdueFilter -> colors.softCoral
                                        isSelected -> colors.electricAzureStart
                                        isOverdueFilter -> colors.softCoral.copy(alpha = 0.6f)
                                        else -> colors.steelLine
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) colors.elevatedCard else colors.deepSlate)
                                            .border(1.dp, borderColor, CircleShape)
                                            .clickable { viewModel.selectFilter(filter) }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = filter.label,
                                                style = typo.bodySecondary,
                                                color = if (isOverdueFilter) colors.softCoral
                                                else if (isSelected) colors.ink
                                                else colors.mutedInk
                                            )
                                            Text(
                                                text = "$count",
                                                style = typo.monoTag,
                                                color = if (isOverdueFilter) colors.softCoral
                                                else colors.electricAzureStart
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.filteredTasks.isEmpty()) {
                        item {
                            EmptyQuestsCard(filter = uiState.selectedFilter)
                        }
                    } else {
                        items(uiState.filteredTasks, key = { it.id }) { task ->
                            SwipeableQuestTaskCard(
                                task = task,
                                now = uiState.nowInstant,
                                zoneId = uiState.zoneId,
                                onComplete = { viewModel.completeQuestTask(task) },
                                onEdit = {
                                    questBeingEdited = task
                                    showQuestSheet = true
                                },
                                onDelete = { viewModel.requestDeleteQuestTask(task) }
                            )
                        }
                    }
                } else {
                    // ==================== 3.14 ROUTINES RÉCURRENTES ====================
                    item {
                        MintPillButton(
                            text = "+ Nouvelle Routine Hors-Écran",
                            onClick = {
                                routineBeingEdited = null
                                showRoutineSheet = true
                            }
                        )
                    }

                    if (uiState.routines.isEmpty()) {
                        item {
                            ElevatedCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🌅", fontSize = 28.sp)
                                    Text(
                                        text = "Aucune routine configurée",
                                        style = typo.cardTitleSerif,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = "Crée une Morning Routine (ex. 07h00–07h30) avec une checklist d'actions physiques hors-écran.",
                                        style = typo.bodySecondary,
                                        color = colors.mutedInk,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.routines, key = { it.rule.id }) { routineUi ->
                            RoutineCard(
                                routineUi = routineUi,
                                now = uiState.nowInstant,
                                zoneId = uiState.zoneId,
                                onToggleItem = { viewModel.toggleRoutineChecklistItem(it) },
                                onEdit = {
                                    routineBeingEdited = routineUi
                                    showRoutineSheet = true
                                },
                                onDelete = { viewModel.requestDeleteRoutine(routineUi) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Feuille modale de création / édition de Quête (3.11)
    if (showQuestSheet) {
        QuestEditorModalSheet(
            existingTask = questBeingEdited,
            now = uiState.nowInstant,
            zoneId = uiState.zoneId,
            onDismiss = { showQuestSheet = false },
            onSave = { title, desc, priority, dueAt ->
                viewModel.saveQuestTask(
                    existingTask = questBeingEdited,
                    title = title,
                    description = desc,
                    priority = priority,
                    dueAt = dueAt,
                    onSavedSuccess = { showQuestSheet = false }
                )
            },
            onDelete = questBeingEdited?.let { task ->
                {
                    showQuestSheet = false
                    viewModel.requestDeleteQuestTask(task)
                }
            }
        )
    }

    // Feuille modale de création / édition de Routine (3.14)
    if (showRoutineSheet) {
        RoutineEditorModalSheet(
            existingRoutine = routineBeingEdited,
            onDismiss = { showRoutineSheet = false },
            onSave = { title, startMins, endMins, daysMask, pkgs, checklistTitles ->
                viewModel.saveRoutineWithChecklist(
                    existingRoutine = routineBeingEdited,
                    title = title,
                    startTimeMinutes = startMins,
                    endTimeMinutes = endMins,
                    activeDaysBitmask = daysMask,
                    targetPackages = pkgs,
                    checklistTitles = checklistTitles,
                    onSavedSuccess = { showRoutineSheet = false }
                )
            },
            onDelete = routineBeingEdited?.let { routine ->
                {
                    showRoutineSheet = false
                    viewModel.requestDeleteRoutine(routine)
                }
            }
        )
    }
}

// ==================== 3.13 COMPOSANT : PANNEAU XP, GRADE ET PÉNALITÉS ====================

@Composable
private fun XpAndPenaltiesPanel(uiState: QuestsUiState) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val lp = uiState.levelProgress

    ElevatedCard {
        // 1. En-tête Niveau, Grade & Série
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NIVEAU ${lp.level} • ${lp.gradeName.uppercase()}",
                    style = typo.monoTag,
                    color = colors.electricAzureStart
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (lp.nextLevelMinXp != null) {
                        "${lp.totalXp} / ${lp.nextLevelMinXp} XP"
                    } else {
                        "${lp.totalXp} XP (Niveau Max)"
                    },
                    style = typo.cardTitleSerif,
                    color = colors.ink
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.deepSlate)
                    .border(1.dp, colors.steelLine, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🔥 ${uiState.streakDays}j • P3: ${uiState.p3OverdueCounter}/3",
                    style = typo.monoTag,
                    color = colors.mintClarityStart
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Barre de progression d'XP
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
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

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Pénalités actives (Dette de Focus en cours & Amputation de quota)
        val hasActivePenalty = uiState.activeDebtRemainingMinutes > 0 || uiState.activeQuotaReductionPercent > 0
        if (hasActivePenalty) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.softCoral.copy(alpha = 0.14f))
                    .border(1.dp, colors.softCoral, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "⚠ PÉNALITÉS ACTIVES AUJOURD'HUI",
                        style = typo.monoTag,
                        color = colors.softCoral
                    )
                    if (uiState.activeDebtRemainingMinutes > 0) {
                        Text(
                            text = "• Dette de Focus stricte en cours : ${uiState.activeDebtRemainingMinutes} min restantes",
                            style = typo.bodySecondary,
                            color = colors.ink
                        )
                    }
                    if (uiState.activeQuotaReductionPercent > 0) {
                        Text(
                            text = "• Quotas journaliers de loisirs réduits de −${uiState.activeQuotaReductionPercent} %",
                            style = typo.bodySecondary,
                            color = colors.ink
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 3. Tâches imminentes (< 2 heures avant échéance)
        if (uiState.imminentTasks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.deepSlate)
                    .border(1.dp, colors.electricAzureStart.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "⏳ ÉCHÉANCES IMMINENTES (< 2H • GEL ANTI-TRICHE ACTIF)",
                        style = typo.monoTag,
                        color = colors.electricAzureStart
                    )
                    uiState.imminentTasks.forEach { task ->
                        val minsLeft = task.dueAt?.let {
                            Duration.between(uiState.nowInstant, it).toMinutes().coerceAtLeast(0)
                        } ?: 0
                        Text(
                            text = "• [${task.priority.label}] ${task.title} — reste ${minsLeft} min",
                            style = typo.monoMetric.copy(fontSize = 12.sp),
                            color = colors.ink
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 4. Placeholder Crédits de Récréation (3.13 -> sera activé en 3.24)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.deepSlate)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Crédits de récréation (Mériter son temps)",
                style = typo.bodySecondary,
                color = colors.mutedInk
            )
            Text(
                text = "Bientôt (3.24)",
                style = typo.monoTag,
                color = colors.mintClarityStart
            )
        }
    }
}

// ==================== 3.10 & 3.12 CARTE DE TÂCHE AVEC CASE À COCHER & GLISSEMENT ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableQuestTaskCard(
    task: QuestTask,
    now: Instant,
    zoneId: ZoneId,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = task.completedAt != null
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (!isCompleted && (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart)) {
                onComplete()
            }
            false // Revient en place après avoir déclenché la complétion
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isCompleted,
        enableDismissFromEndToStart = !isCompleted,
        backgroundContent = {
            val colors = FocusTheme.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.mintClarityStart.copy(alpha = 0.22f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "✓ Glisser pour valider (+${task.xpValue} XP)",
                    style = FocusTheme.typography.monoMetric,
                    color = colors.mintClarityStart
                )
            }
        }
    ) {
        QuestTaskCardContent(
            task = task,
            now = now,
            zoneId = zoneId,
            onComplete = onComplete,
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

@Composable
private fun QuestTaskCardContent(
    task: QuestTask,
    now: Instant,
    zoneId: ZoneId,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val isCompleted = task.completedAt != null
    val isOverdue = !isCompleted && task.dueAt != null && now.isAfter(task.dueAt)
    val completedLate = isCompleted && task.dueAt != null && task.completedAt!!.isAfter(task.dueAt)

    ElevatedCard(
        onClick = if (!isCompleted) onEdit else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Case à cocher circulaire (3.12 : verrouillée une fois terminée)
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) colors.mintClarityStart else Color.Transparent)
                    .border(
                        width = 1.8.dp,
                        color = when {
                            isCompleted -> colors.mintClarityStart
                            isOverdue -> colors.softCoral
                            else -> colors.electricAzureStart
                        },
                        shape = CircleShape
                    )
                    .clickable { onComplete() },
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Text(
                        text = "✓",
                        color = colors.mintOnButton,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityBadge(priority = task.priority)

                    if (task.dueAt != null) {
                        val formattedDue = formatDueInstant(task.dueAt, zoneId)
                        Text(
                            text = if (isOverdue) "⚠ EN RETARD • $formattedDue" else "⏰ $formattedDue",
                            style = typo.monoMetric.copy(fontSize = 11.sp),
                            color = if (isOverdue || completedLate) colors.softCoral else colors.mutedInk
                        )
                    } else {
                        Text(
                            text = "Sans échéance",
                            style = typo.monoTag,
                            color = colors.mutedInk
                        )
                    }
                }

                Text(
                    text = task.title,
                    style = typo.bodyPrimary.copy(
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isCompleted) colors.mutedInk else colors.ink
                )

                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = typo.bodySecondary,
                        color = colors.mutedInk
                    )
                }

                // Pied de carte : statut de pénalité ou boutons d'édition/suppression
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when {
                        completedLate -> "Terminée hors délai (+0 XP • Malus appliqué)"
                        isCompleted -> "Validée (+${task.xpValue} XP acquis)"
                        isOverdue -> "Échéance dépassée (−${task.priority.xpPenalty} XP)"
                        else -> "Récompense : +${task.xpValue} XP"
                    }
                    Text(
                        text = statusText,
                        style = typo.monoTag,
                        color = when {
                            isOverdue || completedLate -> colors.softCoral
                            isCompleted -> colors.mintClarityStart
                            else -> colors.electricAzureStart
                        }
                    )

                    if (!isCompleted) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Éditer",
                                style = typo.monoTag,
                                color = colors.mutedInk,
                                modifier = Modifier.clickable { onEdit() }
                            )
                            Text(
                                text = "Suppr.",
                                style = typo.monoTag,
                                color = colors.softCoral,
                                modifier = Modifier.clickable { onDelete() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 3.11 MODAL SHEET DE CRÉATION / ÉDITION DE QUÊTE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestEditorModalSheet(
    existingTask: QuestTask?,
    now: Instant,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, priority: QuestPriority, dueAt: Instant?) -> Unit,
    onDelete: (() -> Unit)?
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography

    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var description by remember { mutableStateOf(existingTask?.description ?: "") }
    var priority by remember { mutableStateOf(existingTask?.priority ?: QuestPriority.P1) }

    val initialZoned = remember {
        (existingTask?.dueAt ?: now.plusSeconds(3600)).atZone(zoneId)
    }
    var hasDeadline by remember { mutableStateOf(existingTask?.dueAt != null || existingTask == null) }
    var selectedDate by remember { mutableStateOf(initialZoned.toLocalDate()) }
    var selectedTime by remember {
        mutableStateOf(LocalTime.of(initialZoned.hour, initialZoned.minute))
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    val computedDueInstant: Instant? = if (hasDeadline) {
        selectedDate.atTime(selectedTime).atZone(zoneId).toInstant()
    } else {
        null
    }

    FocusModalSheet(
        title = if (existingTask == null) "Nouvelle Quête" else "Modifier la Quête",
        onDismissRequest = onDismiss,
        rightActionSymbol = if (onDelete != null) "🗑" else "✦",
        onRightActionClick = onDelete,
        onSaveClick = {
            if (title.isBlank()) {
                inlineError = "Saisis un titre pour ta quête."
                return@FocusModalSheet
            }
            if (existingTask == null && computedDueInstant != null && computedDueInstant.isBefore(clockNowOr(now))) {
                inlineError = "Impossible de fixer une échéance dans le passé."
                return@FocusModalSheet
            }
            onSave(title, description, priority, computedDueInstant)
        }
    ) {
        if (inlineError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.softCoral.copy(alpha = 0.16f))
                    .border(1.dp, colors.softCoral, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = inlineError!!, style = typo.bodySecondary, color = colors.softCoral)
            }
        }

        // Carte 1 : Titre et Description
        ElevatedCard {
            Text("IDENTITÉ DE LA QUÊTE", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    inlineError = null
                },
                label = { Text("Titre (ex. 1h d'Espagnol sur tablette)") },
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
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Critère de réussite (optionnel)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.electricAzureStart,
                    unfocusedBorderColor = colors.steelLine,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink
                )
            )
        }

        // Carte 2 : Degré de Priorité (xpValue dérivé automatiquement — Décision 7)
        ElevatedCard {
            Text("DEGRÉ DE PRIORITÉ & IMPACT XP", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuestPriority.entries.forEach { p ->
                    val selected = priority == p
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) colors.deepSlate else colors.nightAbyss)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) colors.electricAzureStart else colors.steelLine,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { priority = p }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PriorityBadge(priority = p, showXp = true)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when (priority) {
                    QuestPriority.P1 ->
                        "Critique : +100 XP à temps | Retard : −50 XP, 30 min de Dette de Focus et −50 % de quotas."
                    QuestPriority.P2 ->
                        "Importante : +50 XP (+15 XP dès 3j consécutifs) | Retard : −25 XP, 15 min de Dette et −25 % de quotas."
                    QuestPriority.P3 ->
                        "Secondaire : +20 XP | Retard : −10 XP et 15 min de Dette au 3e retard P3 cumulé."
                },
                style = typo.bodySecondary,
                color = colors.mutedInk
            )
        }

        // Carte 3 : Échéance Date & Heure (Material 3 DatePicker & TimePicker)
        ElevatedCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ÉCHÉANCE STRICTE", style = typo.monoTag, color = colors.mutedInk)
                Switch(
                    checked = hasDeadline,
                    onCheckedChange = { hasDeadline = it }
                )
            }

            if (hasDeadline) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.deepSlate)
                            .border(1.dp, colors.steelLine, RoundedCornerShape(12.dp))
                            .clickable { showDatePicker = true }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📅 ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                            style = typo.monoMetric,
                            color = colors.ink
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.deepSlate)
                            .border(1.dp, colors.steelLine, RoundedCornerShape(12.dp))
                            .clickable { showTimePicker = true }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🕒 ${selectedTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                            style = typo.monoMetric,
                            color = colors.electricAzureStart
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Valider", color = colors.mintClarityStart)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Annuler", color = colors.mutedInk)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = colors.deepSlate,
            title = { Text("Heure limite", style = typo.cardTitleSerif, color = colors.ink) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("Valider", color = colors.mintClarityStart)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Annuler", color = colors.mutedInk)
                }
            }
        )
    }
}

private fun clockNowOr(fallback: Instant): Instant = fallback

// ==================== 3.14 CARTES & MODAL SHEET DES ROUTINES RÉCURRENTES ====================

@Composable
private fun RoutineCard(
    routineUi: RoutineWithChecklistUi,
    now: Instant,
    zoneId: ZoneId,
    onToggleItem: (QuestTask) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val rule = routineUi.rule
    val startStr = formatMinutesOfDay(rule.startTimeMinutes ?: 420)
    val endStr = formatMinutesOfDay(rule.endTimeMinutes ?: 450)

    ElevatedCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ROUTINE HORS-ÉCRAN • $startStr – $endStr",
                    style = typo.monoTag,
                    color = colors.electricAzureStart
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = rule.title,
                    style = typo.cardTitleSerif,
                    color = colors.ink
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (routineUi.isCompleteToday) colors.mintClarityStart.copy(alpha = 0.18f)
                        else colors.deepSlate
                    )
                    .border(
                        1.dp,
                        if (routineUi.isCompleteToday) colors.mintClarityStart else colors.steelLine,
                        CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (routineUi.isCompleteToday) {
                        "✓ Complète (${routineUi.completedTodayCount}/${routineUi.checklist.size})"
                    } else {
                        "${routineUi.completedTodayCount}/${routineUi.checklist.size} aujourd'hui"
                    },
                    style = typo.monoTag,
                    color = if (routineUi.isCompleteToday) colors.mintClarityStart else colors.mutedInk
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Liste interactive des sous-tâches de checklist (même logique que l'Overlay Phase 2)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            routineUi.checklist.forEach { item ->
                val doneToday = item.isCompletedToday(now, zoneId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.deepSlate)
                        .border(
                            1.dp,
                            if (doneToday) colors.mintClarityStart else colors.steelLine,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onToggleItem(item) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (doneToday) colors.mintClarityStart else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (doneToday) colors.mintClarityStart else colors.mutedInk,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (doneToday) {
                            Text(
                                text = "✓",
                                color = colors.mintOnButton,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = item.title,
                        style = typo.bodyPrimary.copy(
                            textDecoration = if (doneToday) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (doneToday) colors.mutedInk else colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${rule.targetPackages.size} apps verrouillées pendant le créneau",
                style = typo.monoTag,
                color = colors.mutedInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Éditer",
                    style = typo.monoTag,
                    color = colors.electricAzureStart,
                    modifier = Modifier.clickable { onEdit() }
                )
                Text(
                    text = "Suppr.",
                    style = typo.monoTag,
                    color = colors.softCoral,
                    modifier = Modifier.clickable { onDelete() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditorModalSheet(
    existingRoutine: RoutineWithChecklistUi?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        startMinutes: Int,
        endMinutes: Int,
        activeDaysBitmask: Int,
        targetPackages: Set<String>,
        checklistTitles: List<String>
    ) -> Unit,
    onDelete: (() -> Unit)?
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography

    var title by remember { mutableStateOf(existingRoutine?.rule?.title ?: "Morning Routine") }
    var startMinutes by remember { mutableIntStateOf(existingRoutine?.rule?.startTimeMinutes ?: 7 * 60) }
    var endMinutes by remember { mutableIntStateOf(existingRoutine?.rule?.endTimeMinutes ?: (7 * 60 + 30)) }
    var daysBitmask by remember {
        mutableIntStateOf(existingRoutine?.rule?.activeDaysBitmask ?: DaysBitmask.ALL_DAYS)
    }

    val presetDistractingApps = remember {
        listOf(
            "Instagram" to "com.instagram.android",
            "TikTok" to "com.zhiliaoapp.musically",
            "YouTube" to "com.google.android.youtube",
            "Chrome" to "com.android.chrome"
        )
    }
    var selectedPackages by remember {
        mutableStateOf(
            existingRoutine?.rule?.targetPackages ?: presetDistractingApps.map { it.second }.toSet()
        )
    }

    var checklistItems by remember {
        mutableStateOf(
            existingRoutine?.checklist?.map { it.title }
                ?: listOf(
                    "Boire 500 ml d'eau au réveil",
                    "Faire 30 pompes ou étirements",
                    "Lire 10 pages hors-écran"
                )
        )
    }
    var newChecklistText by remember { mutableStateOf("") }
    var editingStartClock by remember { mutableStateOf<Boolean?>(null) }

    FocusModalSheet(
        title = if (existingRoutine == null) "Nouvelle Routine" else "Modifier la Routine",
        onDismissRequest = onDismiss,
        rightActionSymbol = if (onDelete != null) "🗑" else "🌅",
        onRightActionClick = onDelete,
        onSaveClick = {
            onSave(title, startMinutes, endMinutes, daysBitmask, selectedPackages, checklistItems)
        }
    ) {
        // Carte 1 : Nom & Créneau Horaire
        ElevatedCard {
            Text("NOM & PLAGE HORAIRE", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nom de la routine") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.deepSlate)
                        .border(1.dp, colors.steelLine, RoundedCornerShape(12.dp))
                        .clickable { editingStartClock = true }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Début : ${formatMinutesOfDay(startMinutes)}",
                        style = typo.monoMetric,
                        color = colors.ink
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.deepSlate)
                        .border(1.dp, colors.steelLine, RoundedCornerShape(12.dp))
                        .clickable { editingStartClock = false }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Fin : ${formatMinutesOfDay(endMinutes)}",
                        style = typo.monoMetric,
                        color = colors.electricAzureStart
                    )
                }
            }
        }

        // Carte 2 : Jours Actifs (L–D)
        ElevatedCard {
            Text("JOURS ACTIFS (L–D)", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(10.dp))
            val dayLabels = listOf("L", "M", "M", "J", "V", "S", "D")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEachIndexed { index, label ->
                    val bit = 1 shl index
                    val active = (daysBitmask and bit) != 0
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.electricAzureStart else colors.deepSlate)
                            .border(
                                1.dp,
                                if (active) colors.electricAzureEnd else colors.steelLine,
                                CircleShape
                            )
                            .clickable {
                                val next = daysBitmask xor bit
                                if (next != 0) daysBitmask = next
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = typo.monoMetric,
                            color = if (active) colors.nightAbyss else colors.mutedInk
                        )
                    }
                }
            }
        }

        // Carte 3 : Checklist d'actions hors-écran (sans XP ni malus — 3.2 & 3.14)
        ElevatedCard {
            Text("CHECKLIST D'ACTIONS HORS-ÉCRAN", style = typo.monoTag, color = colors.mintClarityStart)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                checklistItems.forEachIndexed { idx, itemText ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.deepSlate)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. $itemText",
                            style = typo.bodySecondary,
                            color = colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            style = typo.monoMetric,
                            color = colors.softCoral,
                            modifier = Modifier
                                .clickable {
                                    checklistItems = checklistItems.filterIndexed { i, _ -> i != idx }
                                }
                                .padding(horizontal = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newChecklistText,
                    onValueChange = { newChecklistText = it },
                    placeholder = { Text("Nouvelle action (ex. 20 pompes)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.mintClarityStart)
                        .clickable {
                            if (newChecklistText.isNotBlank()) {
                                checklistItems = checklistItems + newChecklistText.trim()
                                newChecklistText = ""
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Text("+ Ajouter", style = typo.monoTag, color = colors.mintOnButton)
                }
            }
        }

        // Carte 4 : Applications verrouillées tant que la checklist est incomplète
        ElevatedCard {
            Text("APPLICATIONS BLOQUÉES PAR LA ROUTINE", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(8.dp))
            presetDistractingApps.forEach { (label, pkg) ->
                val selected = pkg in selectedPackages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.deepSlate)
                        .clickable {
                            selectedPackages = if (selected) selectedPackages - pkg else selectedPackages + pkg
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = typo.bodyPrimary, color = colors.ink)
                    Text(
                        text = if (selected) "✓ Verrouillée" else "Ignorée",
                        style = typo.monoTag,
                        color = if (selected) colors.mintClarityStart else colors.mutedInk
                    )
                }
            }
        }
    }

    val isStartClock = editingStartClock
    if (isStartClock != null) {
        val currentMins = if (isStartClock) startMinutes else endMinutes
        val pickerState = rememberTimePickerState(
            initialHour = currentMins / 60,
            initialMinute = currentMins % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingStartClock = null },
            containerColor = colors.deepSlate,
            title = {
                Text(
                    text = if (isStartClock) "Heure de début" else "Heure de fin",
                    style = typo.cardTitleSerif,
                    color = colors.ink
                )
            },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val total = pickerState.hour * 60 + pickerState.minute
                        if (isStartClock) startMinutes = total else endMinutes = total
                        editingStartClock = null
                    }
                ) {
                    Text("Valider", color = colors.mintClarityStart)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStartClock = null }) {
                    Text("Annuler", color = colors.mutedInk)
                }
            }
        )
    }
}

// ==================== COMPOSANTS AUXILIAIRES ====================

@Composable
private fun EmptyQuestsCard(filter: QuestFilter) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⚔", fontSize = 28.sp, color = colors.electricAzureStart)
            Text(
                text = when (filter) {
                    QuestFilter.TODAY -> "Aucune quête engagée pour aujourd'hui"
                    QuestFilter.UPCOMING -> "Aucune quête planifiée à venir"
                    QuestFilter.OVERDUE -> "Zéro quête en retard — Discipline exemplaire"
                    QuestFilter.COMPLETED -> "Aucune quête terminée pour le moment"
                },
                style = typo.cardTitleSerif,
                color = colors.ink,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Appuie sur « + Nouvelle Quête Disciplinaire » pour lier tes objectifs réels à ton accès au téléphone.",
                style = typo.bodySecondary,
                color = colors.mutedInk,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DebugBypassBanner(
    action: PendingDebugBypassAction,
    onDismiss: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.softCoral.copy(alpha = 0.16f))
            .border(1.dp, colors.softCoral, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "🛠 BUILD DEBUG UNIQUEMENT — GARDE-FOU EDITGUARD DÉCLENCHÉ",
                style = typo.monoTag,
                color = colors.softCoral
            )
            Text(
                text = action.description,
                style = typo.bodySecondary,
                color = colors.ink
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Forcer (Debug)",
                    style = typo.monoMetric,
                    color = colors.mintClarityStart,
                    modifier = Modifier.clickable { action.onConfirmBypass() }
                )
                Text(
                    text = "Conserver le refus",
                    style = typo.monoMetric,
                    color = colors.mutedInk,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        }
    }
}

private fun formatDueInstant(dueAt: Instant, zoneId: ZoneId): String {
    val zoned = dueAt.atZone(zoneId)
    val today = LocalDate.now(zoneId)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    return when (zoned.toLocalDate()) {
        today -> "Auj. ${zoned.format(timeFormatter)}"
        today.plusDays(1) -> "Demain ${zoned.format(timeFormatter)}"
        else -> zoned.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    }
}

private fun formatMinutesOfDay(minutes: Int): String {
    val h = (minutes / 60).coerceIn(0, 23)
    val m = (minutes % 60).coerceIn(0, 59)
    return String.format("%02d:%02d", h, m)
}