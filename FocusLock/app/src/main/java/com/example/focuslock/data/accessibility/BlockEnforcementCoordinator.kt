package com.example.focuslock.data.accessibility

import android.content.pm.PackageManager
import android.util.Log
import com.example.focuslock.data.usage.UsageStatsTracker
import com.example.focuslock.domain.BlockAuditLog
import com.example.focuslock.domain.BlockDecision
import com.example.focuslock.domain.BlockReason
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.SecurityState
import com.example.focuslock.domain.detector.ForegroundAppDetector
import com.example.focuslock.domain.repository.BlockAuditRepository
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.repository.RuleRepository
import com.example.focuslock.engine.RuleEngine
import com.example.focuslock.ui.overlay.OverlayUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class BlockEnforcementCoordinator(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val packageManager: PackageManager,
    private val detector: ForegroundAppDetector,
    private val usageStatsTracker: UsageStatsTracker,
    private val ruleRepository: RuleRepository,
    private val questTaskRepository: QuestTaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val blockAuditRepository: BlockAuditRepository,
    private val onBlockTriggered: () -> Unit,
    private val onDismissOverlay: () -> Unit
) {
    companion object {
        private const val TAG = "BlockCoordinator"
        private const val DEDUP_WINDOW_MILLIS = 2_000L // 2.19 : Anti-doublon de 2 secondes
    }

    // 2.18 : Règles, tâches et sessions gardées en mémoire via StateFlow
    private val rulesState: StateFlow<List<BlockRule>> = ruleRepository
        .observeEnabledRules()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val tasksState: StateFlow<List<QuestTask>> = questTaskRepository
        .observeAllTasks()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val sessionsState: StateFlow<List<FocusSession>> = focusSessionRepository
        .observeRunningSessions()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val lastAuditTimestampByPackage = ConcurrentHashMap<String, Long>()

    private val _overlayState = MutableStateFlow<OverlayUiModel?>(null)
    val overlayState: StateFlow<OverlayUiModel?> = _overlayState.asStateFlow()

    fun start() {
        // 1. Écoute chaque changement de package au premier plan
        scope.launch {
            detector.foregroundPackages.collect { pkgName ->
                evaluatePackage(pkgName, isReevaluation = false)
            }
        }

        // 2. 2.21 : Ré-évalue automatiquement quand les tâches/règles/sessions changent pendant que l'overlay est affiché
        scope.launch {
            tasksState.collectLatest {
                val currentOverlay = _overlayState.value ?: return@collectLatest
                evaluatePackage(currentOverlay.blockedPackageName, isReevaluation = true)
            }
        }
    }

    fun toggleRoutineChecklistItem(item: QuestTask) {
        scope.launch(Dispatchers.IO) {
            val newInstant = if (item.completedAt == null) clock.instant() else null
            questTaskRepository.updateTaskCompletion(item.id, newInstant)
        }
    }

    fun clearOverlayState() {
        _overlayState.value = null
    }

    private suspend fun evaluatePackage(pkgName: String, isReevaluation: Boolean) {
        val rules = rulesState.value
        val tasks = tasksState.value
        val sessions = sessionsState.value
        val usageMap = withContext(Dispatchers.IO) {
            usageStatsTracker.getTodayUsageMinutes(forceRefresh = false)
        }

        val decision = RuleEngine.evaluate(
            clock = clock,
            foregroundPackage = pkgName,
            rules = rules,
            tasks = tasks,
            sessions = sessions,
            todayUsageMinutes = usageMap,
            securityState = SecurityState()
        )

        when (decision) {
            is BlockDecision.Allow -> {
                if (isReevaluation && _overlayState.value?.blockedPackageName == pkgName) {
                    Log.i(TAG, "Conditions remplies pour $pkgName -> fermeture automatique de l'overlay.")
                    _overlayState.value = null
                    onDismissOverlay()
                }
            }
            is BlockDecision.Block -> {
                handleBlockDecision(pkgName, decision, rules, tasks, sessions, isReevaluation)
            }
        }
    }

    private suspend fun handleBlockDecision(
        pkgName: String,
        decision: BlockDecision.Block,
        rules: List<BlockRule>,
        tasks: List<QuestTask>,
        sessions: List<FocusSession>,
        isReevaluation: Boolean
    ) {
        val nowMillis = clock.millis()
        val startOfDayInstant = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()

        // 2.19 : Anti-doublon sur une fenêtre de 2 secondes par application
        val lastLoggedAt = lastAuditTimestampByPackage[pkgName] ?: 0L
        val shouldInsertLog = !isReevaluation && (nowMillis - lastLoggedAt) >= DEDUP_WINDOW_MILLIS

        if (shouldInsertLog) {
            lastAuditTimestampByPackage[pkgName] = nowMillis
            withContext(Dispatchers.IO) {
                blockAuditRepository.insertLog(
                    BlockAuditLog(
                        timestamp = clock.instant(),
                        blockedPackageName = pkgName,
                        triggerRuleId = decision.ruleId,
                        reasonCode = decision.reason,
                        explanationText = decision.message ?: decision.reason.name
                    )
                )
            }
        }

        val (todayCount, totalCount) = withContext(Dispatchers.IO) {
            val today = blockAuditRepository.getTodayCountForPackage(pkgName, startOfDayInstant)
            val total = blockAuditRepository.getTotalCountForPackage(pkgName)
            today.coerceAtLeast(1) to total.coerceAtLeast(1)
        }

        val matchedRule = rules.firstOrNull { it.id == decision.ruleId }
        val matchedSession = sessions.firstOrNull { it.id == decision.ruleId }

        val targetEnd = when {
            matchedSession != null -> matchedSession.plannedEndAt
            matchedRule?.pauseUntil != null -> matchedRule.pauseUntil
            matchedRule?.endTimeMinutes != null -> {
                val endMins = matchedRule.endTimeMinutes
                LocalDate.now(clock)
                    .atTime(endMins / 60, endMins % 60)
                    .atZone(clock.zone)
                    .toInstant()
            }
            else -> null
        }

        val totalSeconds = if (matchedSession != null) {
            Duration.between(matchedSession.startedAt, matchedSession.plannedEndAt).seconds.coerceAtLeast(60L)
        } else {
            1800L
        }

        val checklist = if (decision.reason == BlockReason.MORNING_ROUTINE && decision.ruleId != null) {
            tasks.filter { it.isRoutineChecklistItem && it.parentRoutineId == decision.ruleId }
        } else {
            emptyList()
        }

        val defaultGentleMessage = "C'est ton temps d'étude. Une minute ici est une minute que tu ne récupéreras pas."
        val customMsg = matchedRule?.customBlockMessage
            ?.takeIf { it.isNotBlank() }
            ?: defaultGentleMessage

        val uiModel = OverlayUiModel(
            blockedPackageName = pkgName,
            appDisplayName = resolveAppLabel(pkgName),
            reason = decision.reason,
            ruleOrSessionId = decision.ruleId,
            ruleTitle = matchedRule?.title ?: matchedSession?.label ?: decision.message ?: "Règle Active",
            customMessage = customMsg,
            targetEndInstant = targetEnd,
            totalDurationSeconds = totalSeconds,
            todayAttempts = todayCount,
            totalAttempts = totalCount,
            routineChecklist = checklist
        )

        _overlayState.value = uiModel
        if (!isReevaluation) {
            onBlockTriggered()
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrElse {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}