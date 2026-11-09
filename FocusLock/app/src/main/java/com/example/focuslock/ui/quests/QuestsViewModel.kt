package com.example.focuslock.ui.quests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focuslock.BuildConfig
import com.example.focuslock.data.AppContainer
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.EditPermission
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.FrictionReason
import com.example.focuslock.domain.GamificationConstants
import com.example.focuslock.domain.LevelProgress
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RuleType
import com.example.focuslock.domain.SessionType
import com.example.focuslock.domain.isCompletedToday
import com.example.focuslock.domain.isRoutineChecklistCompleteToday
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.repository.RuleRepository
import com.example.focuslock.domain.store.GamificationState
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.engine.EditGuard
import com.example.focuslock.engine.GamificationCoordinator
import com.example.focuslock.engine.LevelCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class QuestFilter(val label: String) {
    TODAY("Aujourd'hui"),
    UPCOMING("À venir"),
    OVERDUE("En retard"),
    COMPLETED("Terminées")
}

enum class QuestsTab(val label: String) {
    QUESTS("Quêtes (P1–P3)"),
    ROUTINES("Routines Hors-Écran")
}

data class RoutineWithChecklistUi(
    val rule: BlockRule,
    val checklist: List<QuestTask>,
    val completedTodayCount: Int,
    val isCompleteToday: Boolean
)

data class PendingDebugBypassAction(
    val description: String,
    val onConfirmBypass: () -> Unit
)

data class QuestsUiState(
    val selectedTab: QuestsTab = QuestsTab.QUESTS,
    val selectedFilter: QuestFilter = QuestFilter.TODAY,
    val levelProgress: LevelProgress = LevelCalculator.fromXp(0),
    val streakDays: Int = 0,
    val activeDebtRemainingMinutes: Int = 0,
    val activeQuotaReductionPercent: Int = 0,
    val p3OverdueCounter: Int = 0,
    val imminentTasks: List<QuestTask> = emptyList(),
    val filteredTasks: List<QuestTask> = emptyList(),
    val countsByFilter: Map<QuestFilter, Int> = emptyMap(),
    val routines: List<RoutineWithChecklistUi> = emptyList(),
    val nowInstant: Instant = Instant.now(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val pendingDebugBypass: PendingDebugBypassAction? = null
)

class QuestsViewModel(
    private val clock: Clock,
    private val questTaskRepository: QuestTaskRepository,
    private val ruleRepository: RuleRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val gamificationStore: GamificationStore,
    private val gamificationCoordinator: GamificationCoordinator
) : ViewModel() {

    private val selectedTabFlow = MutableStateFlow(QuestsTab.QUESTS)
    private val selectedFilterFlow = MutableStateFlow(QuestFilter.TODAY)
    private val tickFlow = MutableStateFlow(clock.instant())
    private val pendingBypassFlow = MutableStateFlow<PendingDebugBypassAction?>(null)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        // Rafraîchit l'horloge locale toutes les 15 secondes pour mettre à jour les retards et imminences (< 2h)
        viewModelScope.launch {
            while (true) {
                val now = clock.instant()
                tickFlow.value = now
                with(Dispatchers.IO) {
                    gamificationCoordinator.applyOverdue(now)
                }
                delay(15_000L)
            }
        }
    }

    val uiState: StateFlow<QuestsUiState> = combine(
        questTaskRepository.observeAllTasks(),
        ruleRepository.observeAllRules(),
        focusSessionRepository.observeRunningSessions(),
        gamificationStore.state,
        combine(selectedTabFlow, selectedFilterFlow, tickFlow, pendingBypassFlow) { tab, filter, now, bypass ->
            Quadruple(tab, filter, now, bypass)
        }
    ) { allTasks, allRules, runningSessions, gamification, uiControls ->
        val (selectedTab, selectedFilter, now, pendingBypass) = uiControls
        val zone = clock.zone
        val today = LocalDate.ofInstant(now, zone)
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant()

        // Séparation Quêtes standard vs Sous-tâches de checklist de routine
        val standaloneQuests = allTasks.filter { !it.isRoutineChecklistItem }
        val routineItemsByParent = allTasks
            .filter { it.isRoutineChecklistItem && it.parentRoutineId != null }
            .groupBy { it.parentRoutineId!! }

        // Classement dans les 4 filtres (3.10)
        val overdueTasks = standaloneQuests.filter { task ->
            task.completedAt == null && task.dueAt != null && now.isAfter(task.dueAt)
        }.sortedBy { it.dueAt }

        val todayTasks = standaloneQuests.filter { task ->
            task.completedAt == null &&
                (task.dueAt == null || (!now.isAfter(task.dueAt) && task.dueAt.isBefore(endOfToday)))
        }.sortedWith(compareBy<QuestTask> { it.priority.ordinal }.thenBy { it.dueAt ?: Instant.MAX })

        val upcomingTasks = standaloneQuests.filter { task ->
            task.completedAt == null && task.dueAt != null && !task.dueAt.isBefore(endOfToday)
        }.sortedBy { it.dueAt }

        val completedTasks = standaloneQuests.filter { task ->
            task.completedAt != null
        }.sortedByDescending { it.completedAt }

        val counts = mapOf(
            QuestFilter.TODAY to todayTasks.size,
            QuestFilter.UPCOMING to upcomingTasks.size,
            QuestFilter.OVERDUE to overdueTasks.size,
            QuestFilter.COMPLETED to completedTasks.size
        )

        val currentFiltered = when (selectedFilter) {
            QuestFilter.TODAY -> todayTasks
            QuestFilter.UPCOMING -> upcomingTasks
            QuestFilter.OVERDUE -> overdueTasks
            QuestFilter.COMPLETED -> completedTasks
        }

        // 3.13 : Pénalités actives & Tâches imminentes (dues dans moins de 2 heures)
        val activeDebtSession: FocusSession? = runningSessions.firstOrNull {
            it.sessionType == SessionType.FOCUS_DEBT_MALUS && it.plannedEndAt.isAfter(now)
        }
        val debtRemainingMins = if (activeDebtSession != null) {
            val secs = Duration.between(now, activeDebtSession.plannedEndAt).seconds.coerceAtLeast(0L)
            ((secs + 59L) / 60L).toInt()
        } else {
            0
        }

        val imminentWindowEnd = now.plus(GamificationConstants.QUEST_FREEZE_WINDOW)
        val imminent = standaloneQuests.filter { task ->
            val due = task.dueAt ?: return@filter false
            task.completedAt == null && !now.isAfter(due) && !due.isAfter(imminentWindowEnd)
        }.sortedBy { it.dueAt }

        // 3.14 : Routines récurrentes et dérivation de l'état "fait aujourd'hui"
        val routineRules = allRules.filter { it.ruleType == RuleType.ROUTINE }
        val routinesUi = routineRules.map { rule ->
            val items = routineItemsByParent[rule.id].orEmpty()
            val doneToday = items.count { it.isCompletedToday(now, zone) }
            RoutineWithChecklistUi(
                rule = rule,
                checklist = items,
                completedTodayCount = doneToday,
                isCompleteToday = isRoutineChecklistCompleteToday(items, now, zone)
            )
        }

        val activeQuotaCut = if (gamification.quotaMalusEpochDay == today.toEpochDay()) {
            gamification.quotaMalusReductionPercent
        } else {
            0
        }

        QuestsUiState(
            selectedTab = selectedTab,
            selectedFilter = selectedFilter,
            levelProgress = LevelCalculator.fromXp(gamification.totalXp),
            streakDays = gamification.currentStreakDays,
            activeDebtRemainingMinutes = debtRemainingMins,
            activeQuotaReductionPercent = activeQuotaCut,
            p3OverdueCounter = gamification.p3LateCounter,
            imminentTasks = imminent,
            filteredTasks = currentFiltered,
            countsByFilter = counts,
            routines = routinesUi,
            nowInstant = now,
            zoneId = zone,
            pendingDebugBypass = pendingBypass
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuestsUiState(
            nowInstant = clock.instant(),
            zoneId = clock.zone
        )
    )

    fun selectTab(tab: QuestsTab) {
        selectedTabFlow.value = tab
    }

    fun selectFilter(filter: QuestFilter) {
        selectedFilterFlow.value = filter
    }

    fun dismissDebugBypassPrompt() {
        pendingBypassFlow.value = null
    }

    /**
     * 3.11 & 3.12 : Crée ou édite une quête.
     * Refuse toute échéance dans le passé à la création.
     * Passe par EditGuard en cas d'édition d'une tâche existante.
     */
    fun saveQuestTask(
        existingTask: QuestTask?,
        title: String,
        description: String,
        priority: QuestPriority,
        dueAt: Instant?,
        onSavedSuccess: () -> Unit
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _events.tryEmit("Le titre de la quête est obligatoire.")
            return
        }

        val now = clock.instant()

        if (existingTask == null) {
            // Création : l'échéance ne peut pas être dans le passé (3.11)
            if (dueAt != null && dueAt.isBefore(now)) {
                _events.tryEmit("Échéance refusée : impossible de créer une quête dans le passé.")
                return
            }

            val newTask = QuestTask(
                id = "quest-${UUID.randomUUID()}",
                title = cleanTitle,
                description = description.trim(),
                priority = priority,
                xpValue = priority.xpReward, // 3.11 : Dérivé strictement de la priorité
                dueAt = dueAt,
                completedAt = null,
                isRoutineChecklistItem = false,
                parentRoutineId = null,
                penaltyApplied = false
            )

            viewModelScope.launch(Dispatchers.IO) {
                questTaskRepository.upsertTask(newTask)
                gamificationCoordinator.applyOverdue(clock.instant())
                _events.tryEmit("Quête « $cleanTitle » engagée (${priority.label} +${priority.xpReward} XP).")
            }
            onSavedSuccess()
        } else {
            // Édition : vérification par EditGuard (3.12)
            val updatedTask = existingTask.copy(
                title = cleanTitle,
                description = description.trim(),
                priority = priority,
                xpValue = priority.xpReward,
                dueAt = dueAt
            )

            when (val permission = EditGuard.checkTaskEdit(existingTask, updatedTask, now)) {
                is EditPermission.Allowed -> {
                    persistTaskUpdate(updatedTask, "Quête mise à jour.")
                    onSavedSuccess()
                }
                is EditPermission.RequiresFriction -> {
                    val reasonMsg = formatFrictionMessage(permission.reason)
                    _events.tryEmit("Modification bloquée : $reasonMsg")
                    if (BuildConfig.DEBUG) {
                        pendingBypassFlow.value = PendingDebugBypassAction(
                            description = "Forcer la modification (Debug) : $reasonMsg",
                            onConfirmBypass = {
                                persistTaskUpdate(updatedTask, "Modification forcée (Build Debug).")
                                pendingBypassFlow.value = null
                            }
                        )
                    }
                    onSavedSuccess()
                }
            }
        }
    }

    private fun persistTaskUpdate(task: QuestTask, feedback: String) {
        viewModelScope.launch(Dispatchers.IO) {
            questTaskRepository.upsertTask(task)
            gamificationCoordinator.applyOverdue(clock.instant())
            _events.tryEmit(feedback)
        }
    }

    /**
     * 3.12 : Validation d'une tâche. Une tâche terminée ne peut jamais être décochée (anti-farm d'XP).
     */
    fun completeQuestTask(task: QuestTask) {
        if (task.completedAt != null) {
            _events.tryEmit("Verrouillé : une quête terminée ne peut pas être décochée (anti-farm d'XP).")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val now = clock.instant()
            val award = gamificationCoordinator.completeTask(task.id, now) ?: return@launch
            val msg = when {
                award.totalEarnedXp > 0 && award.bonusXp > 0 ->
                    "Quête validée ! +${award.baseXp} XP (+${award.bonusXp} XP Bonus Régularité P2)"
                award.totalEarnedXp > 0 ->
                    "Quête validée à temps ! +${award.totalEarnedXp} XP"
                else ->
                    "Quête terminée en retard : +0 XP (Malus appliqué)"
            }
            _events.tryEmit(msg)
        }
    }

    /**
     * 3.12 : Suppression protégée par EditGuard.
     */
    fun requestDeleteQuestTask(task: QuestTask) {
        when (val permission = EditGuard.checkTaskDeletion(task)) {
            is EditPermission.Allowed -> {
                viewModelScope.launch(Dispatchers.IO) {
                    questTaskRepository.deleteTaskById(task.id)
                    _events.tryEmit("Tâche « ${task.title} » supprimée.")
                }
            }
            is EditPermission.RequiresFriction -> {
                val reasonMsg = formatFrictionMessage(permission.reason)
                _events.tryEmit("Suppression refusée : $reasonMsg")
                if (BuildConfig.DEBUG) {
                    pendingBypassFlow.value = PendingDebugBypassAction(
                        description = "Forcer la suppression de « ${task.title} » (Debug)",
                        onConfirmBypass = {
                            viewModelScope.launch(Dispatchers.IO) {
                                questTaskRepository.deleteTaskById(task.id)
                                _events.tryEmit("Suppression forcée (Build Debug).")
                            }
                            pendingBypassFlow.value = null
                        }
                    )
                }
            }
        }
    }

    // ==================== 3.14 ROUTINES RÉCURRENTES ====================

    fun toggleRoutineChecklistItem(item: QuestTask) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = clock.instant()
            val isDoneToday = item.isCompletedToday(now, clock.zone)
            val newCompletedAt = if (isDoneToday) null else now
            questTaskRepository.updateTaskCompletion(item.id, newCompletedAt)
        }
    }

    fun saveRoutineWithChecklist(
        existingRoutine: RoutineWithChecklistUi?,
        title: String,
        startTimeMinutes: Int,
        endTimeMinutes: Int,
        activeDaysBitmask: Int,
        targetPackages: Set<String>,
        checklistTitles: List<String>,
        onSavedSuccess: () -> Unit
    ) {
        val cleanTitle = title.trim()
        val cleanItems = checklistTitles.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanTitle.isEmpty()) {
            _events.tryEmit("Le nom de la routine est obligatoire.")
            return
        }
        if (cleanItems.isEmpty()) {
            _events.tryEmit("Ajoute au moins une action hors-écran dans la checklist.")
            return
        }
        if (targetPackages.isEmpty()) {
            _events.tryEmit("Sélectionne au moins une application à verrouiller pendant la routine.")
            return
        }

        val routineId = existingRoutine?.rule?.id ?: "routine-${UUID.randomUUID()}"
        val newRule = BlockRule(
            id = routineId,
            title = cleanTitle,
            ruleType = RuleType.ROUTINE,
            targetPackages = targetPackages,
            isEnabled = existingRoutine?.rule?.isEnabled ?: true,
            isStrictLocked = true,
            activeDaysBitmask = activeDaysBitmask,
            startTimeMinutes = startTimeMinutes,
            endTimeMinutes = endTimeMinutes,
            customBlockMessage = "Complète ta routine « $cleanTitle » hors-écran pour débloquer l'accès."
        )

        val now = clock.instant()
        if (existingRoutine != null) {
            val perm = EditGuard.checkRuleEdit(existingRoutine.rule, newRule, now)
            if (perm is EditPermission.RequiresFriction) {
                val msg = formatFrictionMessage(perm.reason)
                _events.tryEmit("Assouplissement de routine refusé : $msg")
                if (BuildConfig.DEBUG) {
                    pendingBypassFlow.value = PendingDebugBypassAction(
                        description = "Forcer l'édition de la routine « $cleanTitle » (Debug)",
                        onConfirmBypass = {
                            persistRoutineAndChecklist(existingRoutine, newRule, cleanItems)
                            pendingBypassFlow.value = null
                        }
                    )
                }
                onSavedSuccess()
                return
            }
        }

        persistRoutineAndChecklist(existingRoutine, newRule, cleanItems)
        onSavedSuccess()
    }

    private fun persistRoutineAndChecklist(
        existingRoutine: RoutineWithChecklistUi?,
        rule: BlockRule,
        checklistTitles: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleRepository.upsertRule(rule)

            val previousItemsByTitle = existingRoutine?.checklist?.associateBy { it.title }.orEmpty()
            existingRoutine?.checklist?.forEach { oldItem ->
                questTaskRepository.deleteTaskById(oldItem.id)
            }

            checklistTitles.forEachIndexed { idx, itemTitle ->
                val preservedCompletion = previousItemsByTitle[itemTitle]?.completedAt
                questTaskRepository.upsertTask(
                    QuestTask(
                        id = "${rule.id}-item-$idx-${UUID.randomUUID().toString().take(6)}",
                        title = itemTitle,
                        priority = QuestPriority.P1,
                        xpValue = 0,
                        isRoutineChecklistItem = true,
                        parentRoutineId = rule.id,
                        completedAt = preservedCompletion
                    )
                )
            }
            _events.tryEmit("Routine « ${rule.title} » enregistrée (${checklistTitles.size} actions).")
        }
    }

    fun requestDeleteRoutine(routine: RoutineWithChecklistUi) {
        when (val perm = EditGuard.checkRuleDeletion(routine.rule)) {
            is EditPermission.Allowed -> deleteRoutineInternal(routine)
            is EditPermission.RequiresFriction -> {
                val msg = formatFrictionMessage(perm.reason)
                _events.tryEmit("Suppression refusée : $msg")
                if (BuildConfig.DEBUG) {
                    pendingBypassFlow.value = PendingDebugBypassAction(
                        description = "Forcer la suppression de « ${routine.rule.title} » (Debug)",
                        onConfirmBypass = {
                            deleteRoutineInternal(routine)
                            pendingBypassFlow.value = null
                        }
                    )
                }
            }
        }
    }

    private fun deleteRoutineInternal(routine: RoutineWithChecklistUi) {
        viewModelScope.launch(Dispatchers.IO) {
            routine.checklist.forEach { questTaskRepository.deleteTaskById(it.id) }
            ruleRepository.deleteRuleById(routine.rule.id)
            _events.tryEmit("Routine « ${routine.rule.title} » supprimée.")
        }
    }

    private fun formatFrictionMessage(reason: FrictionReason): String = when (reason) {
        FrictionReason.DELETING_ENGAGED_PRIORITY_TASK ->
            "Une quête P1 ou P2 engagée ne peut pas être supprimée sans le sas Mindful Edits & PIN (Phase 5)."
        FrictionReason.DOWNGRADING_TASK_PRIORITY ->
            "Impossible d'abaisser la priorité d'une quête engagée."
        FrictionReason.POSTPONING_IMMINENT_DEADLINE ->
            "Impossible de repousser l'échéance d'une quête P1/P2 à moins de 2 heures du terme."
        FrictionReason.DISABLING_ACTIVE_RULE,
        FrictionReason.DELETING_ACTIVE_RULE,
        FrictionReason.REMOVING_TARGET_PACKAGES,
        FrictionReason.REDUCING_ACTIVE_DAYS,
        FrictionReason.SHORTENING_SCHEDULE_WINDOW ->
            "Assouplir ou supprimer une routine active requiert le sas Mindful Edits & PIN (Phase 5)."
        else -> "Action protégée par le Mode Strict."
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return QuestsViewModel(
                        clock = container.clock,
                        questTaskRepository = container.questTaskRepository,
                        ruleRepository = container.ruleRepository,
                        focusSessionRepository = container.focusSessionRepository,
                        gamificationStore = container.gamificationStore,
                        gamificationCoordinator = container.gamificationCoordinator
                    ) as T
                }
            }
    }
}