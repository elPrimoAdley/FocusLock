package com.example.focuslock.data.accessibility

import android.content.Context
import android.media.AudioManager
import com.example.focuslock.data.usage.UsageStatsTracker
import com.example.focuslock.domain.BlockDecision
import com.example.focuslock.domain.BlockReason
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

class AccessDecisionEvaluator(
    private val context: Context,
    private val clock: Clock,
    private val usageStatsTracker: UsageStatsTracker,
    private val ruleRepository: RuleRepository,
    private val questTaskRepository: QuestTaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val gamificationStore: GamificationStore,
    private val gamificationCoordinator: GamificationCoordinator
) {
    companion object {
        // Autorisation temporaire de 60 s quand l'utilisateur clique sur un raccourci de l'écran Santé
        @Volatile
        var settingsPassUntilMillis: Long = 0L

        fun grantTemporarySettingsPass(nowMillis: Long, durationMillis: Long = 60_000L) {
            settingsPassUntilMillis = nowMillis + durationMillis
        }
    }

    fun isPhoneCallActive(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return when (audioManager.mode) {
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_COMMUNICATION -> true
            else -> false
        }
    }

    suspend fun evaluateFromMemory(
        foregroundPackage: String,
        foregroundClassName: String? = null,
        rules: List<BlockRule>,
        tasks: List<QuestTask>,
        sessions: List<FocusSession>,
        gamification: GamificationState,
        ignoreViewingWindow: Boolean = false
    ): BlockDecision {
        val now = clock.instant()
        val nowMillis = now.toEpochMilli()
        val callInProgress = isPhoneCallActive()

        val emergencyUnlockUntil = gamification.emergencyUnlockUntilTimestamp?.let {
            Instant.ofEpochMilli(it)
        }

        // 5.7 : Protection anti-arrêt forcé / anti-désinstallation sur com.android.settings
        if (!callInProgress &&
            foregroundPackage == "com.android.settings" &&
            gamification.blockUninstallAndSettings &&
            nowMillis > settingsPassUntilMillis &&
            (emergencyUnlockUntil == null || !now.isBefore(emergencyUnlockUntil))
        ) {
            val isSensitiveSettingsScreen = foregroundClassName == null ||
                foregroundClassName.contains("InstalledAppDetails", ignoreCase = true) ||
                foregroundClassName.contains("Accessibility", ignoreCase = true) ||
                foregroundClassName.contains("DeviceAdmin", ignoreCase = true) ||
                foregroundClassName.contains("SpaActivity", ignoreCase = true)

            val hasActiveLock = sessions.any { now.isBefore(it.plannedEndAt) } ||
                rules.any { it.isEnabled && it.isStrictLocked }

            if (isSensitiveSettingsScreen && hasActiveLock) {
                return BlockDecision.Block(
                    reason = BlockReason.ACTIVE_FOCUS_SESSION,
                    ruleId = null,
                    message = "Protection Anti-Sabotage : Réglages système verrouillés pendant une session stricte"
                )
            }
        }

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
            isCallInProgress = callInProgress,
            emergencyUnlockUntil = emergencyUnlockUntil,
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
