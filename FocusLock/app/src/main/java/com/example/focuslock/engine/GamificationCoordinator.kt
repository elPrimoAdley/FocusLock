package com.example.focuslock.engine

import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.GamificationConstants
import com.example.focuslock.domain.PenaltyPlan
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import com.example.focuslock.domain.XpAwardResult
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.repository.TransactionRunner
import com.example.focuslock.domain.store.GamificationStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class GamificationCoordinator(
    private val zoneId: ZoneId,
    private val questTaskRepository: QuestTaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val gamificationStore: GamificationStore,
    private val transactionRunner: TransactionRunner
) {
    private val mutex = Mutex()

    /**
     * 3.8 : Valide une tâche à l'instant [now], attribue l'XP méritée (ou applique le malus si en retard)
     * et met à jour la série (Streak). Protégé par le Mutex.
     */
    suspend fun completeTask(taskId: String, now: Instant): XpAwardResult? = mutex.withLock {
        val task = questTaskRepository.getTaskById(taskId) ?: return@withLock null

        // Idempotence : si la tâche est déjà terminée, aucun effet supplémentaire
        if (task.completedAt != null) return@withLock null

        // 1. Persiste l'horodatage de complétion dans Room
        questTaskRepository.updateTaskCompletion(taskId, now)

        // Si c'est un item de checklist de routine, pas d'XP ni de malus
        if (task.isRoutineChecklistItem) {
            val snapshot = gamificationStore.getSnapshot()
            return@withLock XpRewardEngine.evaluateTaskCompletion(
                task = task,
                completedAt = now,
                zoneId = zoneId,
                currentTotalXp = snapshot.totalXp,
                consecutiveP2Days = snapshot.consecutiveP2Days,
                lastP2CompletionDate = snapshot.lastP2CompletionEpochDay?.let(LocalDate::ofEpochDay)
            )
        }

        // 2. Si la tâche a été terminée en retard (now > dueAt), on déclenche le traitement des retards
        val dueAt = task.dueAt
        if (dueAt != null && now.isAfter(dueAt) && !task.penaltyApplied) {
            applyOverdueUnlocked(now)
        }

        // 3. Calcul du gain d'XP et ré-évaluation de la série (Streak)
        val currentStore = gamificationStore.getSnapshot()
        val award = XpRewardEngine.evaluateTaskCompletion(
            task = task.copy(completedAt = now),
            completedAt = now,
            zoneId = zoneId,
            currentTotalXp = currentStore.totalXp,
            consecutiveP2Days = currentStore.consecutiveP2Days,
            lastP2CompletionDate = currentStore.lastP2CompletionEpochDay?.let(LocalDate::ofEpochDay)
        )

        val allTasks = questTaskRepository.getAllTasks()
        val streakResult = StreakEvaluator.evaluate(
            now = now,
            zoneId = zoneId,
            tasks = allTasks,
            currentStreakDays = currentStore.currentStreakDays,
            lastEvaluatedEpochDay = currentStore.lastStreakEvalEpochDay
        )

        gamificationStore.update { state ->
            state.copy(
                totalXp = award.newTotalXp,
                consecutiveP2Days = award.newConsecutiveP2Days,
                lastP2CompletionEpochDay = award.updatedLastP2CompletionDate?.toEpochDay(),
                currentStreakDays = streakResult.currentStreakDays,
                lastStreakEvalEpochDay = streakResult.lastEvaluatedEpochDay
            )
        }

        award
    }

    /**
     * 3.8 : Détecte toutes les tâches en retard à l'instant [now], applique atomiquement
     * la Dette de Focus dans Room puis met à jour le DataStore. Protégé par le Mutex.
     */
    suspend fun applyOverdue(now: Instant): PenaltyPlan = mutex.withLock {
        applyOverdueUnlocked(now)
    }

    private suspend fun applyOverdueUnlocked(now: Instant): PenaltyPlan {
        focusSessionRepository.completeExpiredSessions(now)
        val todayDate = now.atZone(zoneId).toLocalDate()
        val todayEpochDay = todayDate.toEpochDay()
        val storeSnapshot = gamificationStore.getSnapshot()

        val overdueCandidates = questTaskRepository.getOverdueUnpenalizedTasks(now)
        val detection = MalusCalculator.detect(
            now = now,
            tasks = overdueCandidates,
            currentP3OverdueCount = storeSnapshot.p3LateCounter
        )

        val existingDebtSession = focusSessionRepository.getActiveDebtSession(now)
        val currentRemainingDebtMinutes = if (existingDebtSession != null) {
            val remainingSec = Duration.between(now, existingDebtSession.plannedEndAt).seconds.coerceAtLeast(0L)
            ((remainingSec + 59L) / 60L).toInt()
        } else {
            0
        }

        val lastQuotaDate = storeSnapshot.quotaMalusEpochDay?.let(LocalDate::ofEpochDay)
        val penaltyPlan = MalusCalculator.aggregate(
            events = detection.events,
            currentTotalXp = storeSnapshot.totalXp,
            currentDebtMinutes = currentRemainingDebtMinutes,
            currentQuotaReductionPercent = storeSnapshot.quotaMalusReductionPercent,
            lastQuotaEvaluationDate = lastQuotaDate,
            todayDate = todayDate
        )

        if (detection.events.isNotEmpty()) {
            // ÉTAPE 1 : Transaction Room atomique (penaltyApplied = true + insertion/extension FOCUS_DEBT_MALUS)
            transactionRunner.runInTransaction {
                questTaskRepository.markTasksPenalized(detection.penalizedTaskIds)

                if (penaltyPlan.addedDebtMinutes > 0) {
                    val activeDebt = focusSessionRepository.getActiveDebtSession(now)
                    val maxAllowedEnd = now.plusSeconds(
                        GamificationConstants.MAX_ACCUMULATED_DEBT_MINUTES * 60L
                    )

                    if (activeDebt != null) {
                        val proposedEnd = activeDebt.plannedEndAt.plusSeconds(
                            penaltyPlan.addedDebtMinutes * 60L
                        )
                        val cappedEnd = if (proposedEnd.isAfter(maxAllowedEnd)) maxAllowedEnd else proposedEnd
                        focusSessionRepository.upsertSession(
                            activeDebt.copy(
                                plannedEndAt = cappedEnd,
                                label = "Dette de Focus cumulée (${penaltyPlan.newTotalDebtMinutes} min)"
                            )
                        )
                    } else {
                        val durationMinutes = penaltyPlan.addedDebtMinutes
                            .coerceAtMost(GamificationConstants.MAX_ACCUMULATED_DEBT_MINUTES)
                        focusSessionRepository.upsertSession(
                            FocusSession(
                                id = "debt-${UUID.randomUUID()}",
                                sessionType = SessionType.FOCUS_DEBT_MALUS,
                                label = "Dette de Focus (${durationMinutes} min de blocage strict)",
                                startedAt = now,
                                plannedEndAt = now.plusSeconds(durationMinutes * 60L),
                                whitelistedPackages = emptySet(),
                                status = SessionStatus.RUNNING
                            )
                        )
                    }
                }
            }

            // ÉTAPE 2 : Mise à jour du DataStore APRÈS la validation de la transaction Room
            val allTasks = questTaskRepository.getAllTasks()
            val streakEval = StreakEvaluator.evaluate(
                now = now,
                zoneId = zoneId,
                tasks = allTasks,
                currentStreakDays = storeSnapshot.currentStreakDays,
                lastEvaluatedEpochDay = storeSnapshot.lastStreakEvalEpochDay
            )

            gamificationStore.update { state ->
                state.copy(
                    totalXp = penaltyPlan.newTotalXp,
                    quotaMalusReductionPercent = penaltyPlan.newQuotaReductionPercent,
                    quotaMalusEpochDay = todayEpochDay,
                    p3LateCounter = detection.newP3OverdueCount,
                    currentStreakDays = streakEval.currentStreakDays,
                    lastStreakEvalEpochDay = streakEval.lastEvaluatedEpochDay
                )
            }
        } else if (storeSnapshot.quotaMalusEpochDay != todayEpochDay && storeSnapshot.quotaMalusReductionPercent != 0) {
            // Remise à zéro du malus de quota au changement de jour même sans nouveau retard
            gamificationStore.update { state ->
                state.copy(
                    quotaMalusReductionPercent = 0,
                    quotaMalusEpochDay = todayEpochDay
                )
            }
        }

        return penaltyPlan
    }

    /**
     * 3.24 : Dépense 100 XP du jour (sans réduire totalXp ni le grade) pour accorder +15 min
     * de crédit de récréation sur les applications en quota journalier.
     */
    suspend fun redeemRecreationCredit(now: Instant): RecreationEligibility = mutex.withLock {
        focusSessionRepository.completeExpiredSessions(now)
        val todayEpochDay = now.atZone(zoneId).toLocalDate().toEpochDay()
        val storeState = gamificationStore.getSnapshot()
        val tasks = questTaskRepository.getAllTasks()
        val runningSessions = focusSessionRepository.getRunningSessions()

        val spentToday = if (storeState.recreationSpentEpochDay == todayEpochDay) {
            storeState.recreationSpentTodayXp
        } else {
            0
        }
        val currentCredit = storeState.recreationCreditUntilTimestamp?.let {
            com.example.focuslock.domain.RecreationCredit(validUntil = Instant.ofEpochMilli(it))
        }

        val eligibility = RecreationCreditEvaluator.evaluateEligibility(
            now = now,
            zoneId = zoneId,
            tasks = tasks,
            runningSessions = runningSessions,
            spentTodayXp = spentToday,
            activeCredit = currentCredit
        )

        if (!eligibility.isEligible) {
            return@withLock eligibility
        }

        val baseStart = if (currentCredit != null && now.isBefore(currentCredit.validUntil)) {
            currentCredit.validUntil
        } else {
            now
        }
        val newCreditUntil = baseStart.plusSeconds(RecreationCreditEvaluator.DEFAULT_CREDIT_MINUTES * 60L)
        val newSpentToday = spentToday + eligibility.costXp

        gamificationStore.update { state ->
            state.copy(
                recreationSpentTodayXp = newSpentToday,
                recreationSpentEpochDay = todayEpochDay,
                recreationCreditUntilTimestamp = newCreditUntil.toEpochMilli()
            )
        }

        eligibility.copy(
            spentTodayXp = newSpentToday,
            availableTodayXp = (eligibility.earnedTodayXp - newSpentToday).coerceAtLeast(0),
            activeCreditUntil = newCreditUntil
        )
    }
}