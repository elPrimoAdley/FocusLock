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
import com.example.focuslock.domain.isCompletedToday
import com.example.focuslock.domain.repository.BlockAuditRepository
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.engine.RuleFormatterAndDetoxCalculator
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.repository.RuleRepository
import com.example.focuslock.domain.store.GamificationState
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.engine.GamificationCoordinator
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
import java.time.Instant
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
    private val gamificationStore: GamificationStore,
    private val gamificationCoordinator: GamificationCoordinator,
    private val onBlockTriggered: () -> Unit,
    private val onDismissOverlay: () -> Unit
) {
    companion object {
        private const val TAG = "BlockCoordinator"
        private const val DEDUP_WINDOW_MILLIS = 2_000L
        private const val OVERDUE_THROTTLE_MILLIS = 30_000L // 3.9 : Limité à 1 fois toutes les ~30 s
    }

    private val rulesState: StateFlow<List<BlockRule>> = ruleRepository
        .observeEnabledRules()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val tasksState: StateFlow<List<QuestTask>> = questTaskRepository
        .observeAllTasks()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val sessionsState: StateFlow<List<FocusSession>> = focusSessionRepository
        .observeRunningSessions()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val gamificationState: StateFlow<GamificationState> = gamificationStore
        .state
        .stateIn(scope, SharingStarted.Eagerly, GamificationState())

    // 3.9 : Prochaine échéance mise en cache en mémoire + horodatage du dernier check
    @Volatile
    private var cachedNextDueAt: Instant? = null

    @Volatile
    private var lastOverdueEvalMillis: Long = 0L

    private val lastAuditTimestampByPackage = ConcurrentHashMap<String, Long>()

    private val _overlayState = MutableStateFlow<OverlayUiModel?>(null)
    val overlayState: StateFlow<OverlayUiModel?> = _overlayState.asStateFlow()

    fun start() {
        // Maintient en cache mémoire la prochaine échéance non pénalisée
        scope.launch {
            tasksState.collectLatest { tasks ->
                cachedNextDueAt = tasks
                    .asSequence()
                    .filter { !it.isRoutineChecklistItem && !it.penaltyApplied && it.completedAt == null }
                    .mapNotNull { it.dueAt }
                    .minOrNull()

                // 3.9 : Après chaque modification de tâche, vérifie si une échéance est dépassée
                val nextDue = cachedNextDueAt
                val now = clock.instant()
                if (nextDue != null && now.isAfter(nextDue)) {
                    withContext(Dispatchers.IO) {
                        gamificationCoordinator.applyOverdue(now)
                    }
                }

                // 2.21 : Ré-évaluation automatique si l'overlay est affiché
                val currentOverlay = _overlayState.value
                if (currentOverlay != null) {
                    evaluatePackage(currentOverlay.blockedPackageName, isReevaluation = true)
                }
            }
        }

        // Écoute les changements d'application au premier plan
        scope.launch {
            detector.foregroundPackages.collect { pkgName ->
                evaluatePackage(pkgName, isReevaluation = false)
            }
        }
    }

    fun toggleRoutineChecklistItem(item: QuestTask) {
        scope.launch(Dispatchers.IO) {
            val now = clock.instant()
            val doneToday = item.isCompletedToday(now, clock.zone)
            val newInstant = if (doneToday) null else now
            questTaskRepository.updateTaskCompletion(item.id, newInstant)
        }
    }

    fun clearOverlayState() {
        _overlayState.value = null
    }

    private suspend fun evaluatePackage(pkgName: String, isReevaluation: Boolean) {
        val now = clock.instant()
        val nowMillis = now.toEpochMilli()

        // 3.9 : Vérifie si la prochaine échéance en cache est dépassée ET respecte le throttle de 30 s
        val nextDue = cachedNextDueAt
        val isDueReached = nextDue != null && now.isAfter(nextDue)
        val isThrottleElapsed = (nowMillis - lastOverdueEvalMillis) >= OVERDUE_THROTTLE_MILLIS

        val sessions = if (isDueReached || (nextDue != null && isThrottleElapsed)) {
            lastOverdueEvalMillis = nowMillis
            withContext(Dispatchers.IO) {
                gamificationCoordinator.applyOverdue(now)
                focusSessionRepository.getRunningSessions()
            }
        } else {
            sessionsState.value
        }

        // 3.22 : Si une session en mémoire vient d'expirer (plannedEndAt <= now), on la clôture idempotemment
        val hasExpiredRunningSession = sessionsState.value.any { !now.isBefore(it.plannedEndAt) }
        if (hasExpiredRunningSession) {
            withContext(Dispatchers.IO) {
                focusSessionRepository.completeExpiredSessions(now)
            }
        }

        val rules = rulesState.value
        val tasks = tasksState.value
        val usageMap = withContext(Dispatchers.IO) {
            usageStatsTracker.getTodayUsageMinutes(forceRefresh = false)
        }
        val securityState = SecurityState(
            quotaMalusReductionPercent = gamificationState.value.quotaMalusReductionPercent
        )

        val gState = gamificationState.value
        val activeRecreationCredit = gState.recreationCreditUntilTimestamp?.let { untilMillis ->
            com.example.focuslock.domain.RecreationCredit(
                packageName = null,
                validUntil = Instant.ofEpochMilli(untilMillis)
            )
        }

        val decision = RuleEngine.evaluate(
            clock = clock,
            foregroundPackage = pkgName,
            rules = rules,
            tasks = tasks,
            sessions = sessions,
            todayUsageMinutes = usageMap,
            securityState = securityState,
            recreationCredit = activeRecreationCredit
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

        val todayLocalDate = LocalDate.now(clock)
        val reopenDate: LocalDate? = if (matchedRule?.ruleType == com.example.focuslock.domain.RuleType.ALTERNATING_DETOX) {
            RuleFormatterAndDetoxCalculator.nextReopenDate(matchedRule, todayLocalDate)
        } else {
            null
        }

        val statusPillOverride = when {
            matchedRule?.ruleType == com.example.focuslock.domain.RuleType.SABBATICAL && matchedRule.pauseUntil != null ->
                RuleFormatterAndDetoxCalculator.formatSabbaticalOverlayPill(
                    matchedRule.pauseUntil,
                    clock.zone
                )
            reopenDate != null && reopenDate.isAfter(todayLocalDate) ->
                "PAUSE DÉTOX • VERROUILLÉ JUSQU'AU ${
                    reopenDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                }"
            else -> null
        }

        val targetEnd = when {
            matchedSession != null -> matchedSession.plannedEndAt
            matchedRule?.pauseUntil != null -> matchedRule.pauseUntil
            reopenDate != null && reopenDate.isAfter(todayLocalDate) ->
                reopenDate.atStartOfDay(clock.zone).toInstant()
            matchedRule?.endTimeMinutes != null -> {
                val endMins = matchedRule.endTimeMinutes
                todayLocalDate
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
            statusPillOverride = statusPillOverride,
            targetEndInstant = targetEnd,
            totalDurationSeconds = totalSeconds,
            todayAttempts = todayCount,
            totalAttempts = totalCount,
            routineChecklist = checklist,
            startOfToday = startOfDayInstant
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