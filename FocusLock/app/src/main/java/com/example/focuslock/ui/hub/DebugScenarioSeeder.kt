package com.example.focuslock.ui.hub

import com.example.focuslock.data.AppContainer
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RuleType
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

object DebugScenarioSeeder {

    private val defaultTargets = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.youtube",
        "com.android.chrome"
    )

    suspend fun clearAllScenarios(container: AppContainer) = withContext(Dispatchers.IO) {
        container.ruleRepository.getAllRules().forEach {
            container.ruleRepository.deleteRuleById(it.id)
        }
        container.questTaskRepository.getAllTasks().forEach {
            container.questTaskRepository.deleteTaskById(it.id)
        }
        container.focusSessionRepository.getRunningSessions().forEach {
            container.focusSessionRepository.updateSessionStatus(
                it.id,
                SessionStatus.COMPLETED,
                container.clock.instant()
            )
        }
    }

    /**
     * Scénario 1 : Routine Matinale active toute la journée avec 3 sous-tâches hors-écran (2.21).
     */
    suspend fun seedMorningRoutineScenario(container: AppContainer) = withContext(Dispatchers.IO) {
        clearAllScenarios(container)
        val routineId = "debug-morning-routine"
        container.ruleRepository.upsertRule(
            BlockRule(
                id = routineId,
                title = "Morning Routine (00h–24h Test)",
                ruleType = RuleType.ROUTINE,
                targetPackages = defaultTargets,
                isEnabled = true,
                activeDaysBitmask = DaysBitmask.ALL_DAYS,
                startTimeMinutes = 0,
                endTimeMinutes = 24 * 60 - 1,
                customBlockMessage = "Complète tes 3 actions physiques hors-écran pour débloquer ton téléphone."
            )
        )
        listOf(
            "t-water" to "Boire 500 ml d'eau au réveil",
            "t-pushups" to "Faire 30 pompes ou étirements",
            "t-reading" to "Lire 10 pages hors-écran"
        ).forEach { (id, title) ->
            container.questTaskRepository.upsertTask(
                QuestTask(
                    id = id,
                    title = title,
                    priority = QuestPriority.P1,
                    isRoutineChecklistItem = true,
                    parentRoutineId = routineId,
                    completedAt = null
                )
            )
        }
    }

    /**
     * Scénario 2 : Contrat d'Apprentissage Actif de 30 minutes (Niveau 3).
     */
    suspend fun seedFocusSessionScenario(container: AppContainer) = withContext(Dispatchers.IO) {
        clearAllScenarios(container)
        val now = container.clock.instant()
        container.focusSessionRepository.upsertSession(
            FocusSession(
                id = "debug-session-lang",
                sessionType = SessionType.ACTIVE_LEARNING,
                label = "Espagnol — Tablette (30m)",
                startedAt = now,
                plannedEndAt = now.plusSeconds(30 * 60L),
                whitelistedPackages = setOf("com.spotify.music"),
                status = SessionStatus.RUNNING
            )
        )
    }

    /**
     * Scénario 3 : Dette de Focus automatique de 15 minutes (Niveau 4).
     */
    suspend fun seedFocusDebtScenario(container: AppContainer) = withContext(Dispatchers.IO) {
        clearAllScenarios(container)
        val now = container.clock.instant()
        container.focusSessionRepository.upsertSession(
            FocusSession(
                id = "debug-session-debt",
                sessionType = SessionType.FOCUS_DEBT_MALUS,
                label = "Dette P1 : Retard Quête Critique",
                startedAt = now,
                plannedEndAt = now.plusSeconds(15 * 60L),
                status = SessionStatus.RUNNING
            )
        )
    }

    /**
     * Scénario 4 : Pause Sabbatique & Détox 1j/2 en jour OFF (Niveau 6).
     */
    suspend fun seedSabbaticalDetoxScenario(container: AppContainer) = withContext(Dispatchers.IO) {
        clearAllScenarios(container)
        val yesterdayEpochDay = LocalDate.now(container.clock).minusDays(1).toEpochDay()
        container.ruleRepository.upsertRule(
            BlockRule(
                id = "debug-detox-1-2",
                title = "Détox Intermittente 1j/2 (Jour OFF)",
                ruleType = RuleType.ALTERNATING_DETOX,
                targetPackages = defaultTargets,
                isEnabled = true,
                cycleIntervalDays = 2,
                cycleStartEpochDay = yesterdayEpochDay, // Hier = Jour 0 (ON), Aujourd'hui = Jour 1 (OFF)
                customBlockMessage = "Journée OFF de détox dopaminergique. Reviens demain !"
            )
        )
    }

    /**
     * Scénario 5 : Plage Horaire & Quota Journalier atteint (Niveau 7).
     */
    suspend fun seedScheduleAndQuotaScenario(container: AppContainer) = withContext(Dispatchers.IO) {
        clearAllScenarios(container)
        container.ruleRepository.upsertRule(
            BlockRule(
                id = "debug-quota-zero",
                title = "Quota Réseaux Sociaux (Épuisé)",
                ruleType = RuleType.DAILY_QUOTA,
                targetPackages = defaultTargets,
                isEnabled = true,
                dailyQuotaMinutes = 0,
                customBlockMessage = "Ton quota quotidien de loisirs est atteint pour aujourd'hui."
            )
        )
    }

    /**
     * Scénario 3.9 : Insère uniquement une tâche P1 non pénalisée dont l'échéance est dépassée
     * depuis 1 minute (sans créer de session manuellement).
     * L'ouverture d'Instagram déclenchera applyOverdue -> création de la dette -> FOCUS_DEBT_PENALTY.
     */
    suspend fun seedOverdueP1TaskTriggerScenario(container: AppContainer) = withContext(Dispatchers.IO) {
        clearAllScenarios(container)
        val now = container.clock.instant()
        container.questTaskRepository.upsertTask(
            QuestTask(
                id = "debug-overdue-p1",
                title = "Quête P1 dépassée (1h Espagnol)",
                priority = QuestPriority.P1,
                dueAt = now.minusSeconds(60L), // Échéance dépassée depuis 60 secondes
                completedAt = null,
                penaltyApplied = false
            )
        )
    }
}