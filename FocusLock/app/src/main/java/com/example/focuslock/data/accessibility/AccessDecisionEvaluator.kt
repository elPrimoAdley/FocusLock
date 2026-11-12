package com.example.focuslock.data.accessibility

import com.example.focuslock.data.usage.UsageStatsTracker
import com.example.focuslock.domain.BlockDecision
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RecreationCredit
import com.example.focuslock.domain.SecurityState
import com.example.focuslock.domain.ViewingWindow
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.repository.RuleRepository
import com.example.focuslock.domain.store.GamificationState
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.engine.GamificationCoordinator
import com.example.focuslock.engine.RuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant

data class EvaluationSnapshot(
    val decision: BlockDecision,
    val rules: List<BlockRule>,
    val tasks: List<QuestTask>,
    val sessions: List<FocusSession>,
    val gamification: GamificationState
)

/**
 * 4.8 : Source unique de décision partagée entre FocusAccessibilityService et le Coffre de Contenu.
 */
class AccessDecisionEvaluator(
    private val clock: Clock,
    private val usageStatsTracker: UsageStatsTracker,
    private val ruleRepository: RuleRepository,
    private val questTaskRepository: QuestTaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val gamificationStore: GamificationStore,
    private val gamificationCoordinator: GamificationCoordinator
) {

    /**
     * Évalue un package à partir des flux déjà en mémoire (appelé par le service d'accessibilité).
     */
    suspend fun evaluateFromMemory(
        foregroundPackage: String,
        rules: List<BlockRule>,
        tasks: List<QuestTask>,
        sessions: List<FocusSession>,
        gamification: GamificationState,
        ignoreViewingWindow: Boolean = false
    ): BlockDecision {
        val now = clock.instant()
        val todayEpochDay = now.atZone(clock.zone).toLocalDate().toEpochDay()
        val usageMap = withContext(Dispatchers.IO) {
            usageStatsTracker.getTodayUsageMinutes(forceRefresh = false)
        }

        val activeQuotaMalus = if (gamification.quotaMalusEpochDay == todayEpochDay) {
            gamification.quotaMalusReductionPercent
        } else {
            0
        }

        val viewingWindow = if (!ignoreViewingWindow &&
            gamification.viewingWindowPackage != null &&
            gamification.viewingWindowExpiresAtTimestamp != null
        ) {
            ViewingWindow(
                packageName = gamification.viewingWindowPackage,
                expiresAt = Instant.ofEpochMilli(gamification.viewingWindowExpiresAtTimestamp)
            )
        } else {
            null
        }

        val securityState = SecurityState(
            quotaMalusReductionPercent = activeQuotaMalus,
            viewingWindow = viewingWindow
        )

        val activeRecreationCredit = gamification.recreationCreditUntilTimestamp?.let { untilMillis ->
            RecreationCredit(
                packageName = null,
                validUntil = Instant.ofEpochMilli(untilMillis)
            )
        }

        return RuleEngine.evaluate(
            clock = clock,
            foregroundPackage = foregroundPackage,
            rules = rules,
            tasks = tasks,
            sessions = sessions,
            todayUsageMinutes = usageMap,
            securityState = securityState,
            recreationCredit = activeRecreationCredit
        )
    }

    /**
     * Évalue immédiatement l'accès à un package (appelé par « Ouvrir le post » dans le Coffre).
     * Met d'abord à jour les échéances et sessions expirées pour garantir une décision à la seconde près.
     */
    suspend fun evaluateLiveForPackage(
        targetPackage: String,
        ignoreViewingWindow: Boolean = true
    ): EvaluationSnapshot = withContext(Dispatchers.IO) {
        val now = clock.instant()
        focusSessionRepository.completeExpiredSessions(now)
        gamificationCoordinator.applyOverdue(now)

        val rules = ruleRepository.getAllRules().filter { it.isEnabled }
        val tasks = questTaskRepository.getAllTasks()
        val sessions = focusSessionRepository.getRunningSessions()
        val gamification = gamificationStore.getSnapshot()

        val decision = evaluateFromMemory(
            foregroundPackage = targetPackage,
            rules = rules,
            tasks = tasks,
            sessions = sessions,
            gamification = gamification,
            ignoreViewingWindow = ignoreViewingWindow
        )

        EvaluationSnapshot(
            decision = decision,
            rules = rules,
            tasks = tasks,
            sessions = sessions,
            gamification = gamification
        )
    }
}
