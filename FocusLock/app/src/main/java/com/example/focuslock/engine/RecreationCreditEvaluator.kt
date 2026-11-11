package com.example.focuslock.engine

import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RecreationCredit
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class CreditIneligibilityReason {
    NO_P1_DUE_TODAY,
    INCOMPLETE_OR_LATE_P1_TODAY,
    ACTIVE_FOCUS_DEBT,
    INSUFFICIENT_TODAY_XP
}

data class RecreationEligibility(
    val isEligible: Boolean,
    val earnedTodayXp: Int,
    val spentTodayXp: Int,
    val availableTodayXp: Int,
    val costXp: Int = RecreationCreditEvaluator.DEFAULT_CREDIT_COST_XP,
    val rewardMinutes: Int = RecreationCreditEvaluator.DEFAULT_CREDIT_MINUTES,
    val ineligibilityReason: CreditIneligibilityReason? = null,
    val activeCreditUntil: Instant? = null
)

object RecreationCreditEvaluator {

    const val DEFAULT_CREDIT_COST_XP: Int = 100
    const val DEFAULT_CREDIT_MINUTES: Int = 15

    fun evaluateEligibility(
        now: Instant,
        zoneId: ZoneId,
        tasks: List<QuestTask>,
        runningSessions: List<FocusSession>,
        spentTodayXp: Int,
        activeCredit: RecreationCredit? = null,
        costXp: Int = DEFAULT_CREDIT_COST_XP
    ): RecreationEligibility {
        val today = now.atZone(zoneId).toLocalDate()
        val startOfToday = today.atStartOfDay(zoneId).toInstant()
        val endOfToday = today.plusDays(1).atStartOfDay(zoneId).toInstant()

        val validCreditUntil = activeCredit?.validUntil?.takeIf { now.isBefore(it) }

        // Calcul de l'XP gagnée aujourd'hui (tâches validées à temps aujourd'hui)
        val earnedTodayXp = tasks
            .filter { task ->
                !task.isRoutineChecklistItem &&
                    task.completedAt != null &&
                    !task.completedAt.isBefore(startOfToday) &&
                    task.completedAt.isBefore(endOfToday) &&
                    (task.dueAt == null || !task.completedAt.isAfter(task.dueAt))
            }
            .sumOf { it.xpValue }

        val safeSpent = spentTodayXp.coerceAtLeast(0)
        val availableTodayXp = (earnedTodayXp - safeSpent).coerceAtLeast(0)

        // 1. Aucune Dette de Focus active
        val hasActiveDebt = runningSessions.any { session ->
            session.status == SessionStatus.RUNNING &&
                session.sessionType == SessionType.FOCUS_DEBT_MALUS &&
                now.isBefore(session.plannedEndAt)
        }
        if (hasActiveDebt) {
            return RecreationEligibility(
                isEligible = false,
                earnedTodayXp = earnedTodayXp,
                spentTodayXp = safeSpent,
                availableTodayXp = availableTodayXp,
                ineligibilityReason = CreditIneligibilityReason.ACTIVE_FOCUS_DEBT,
                activeCreditUntil = validCreditUntil
            )
        }

        // 2. Au moins une P1 échue aujourd'hui
        val todayDueP1s = tasks.filter { task ->
            !task.isRoutineChecklistItem &&
                task.priority == QuestPriority.P1 &&
                task.dueAt != null &&
                !task.dueAt.isBefore(startOfToday) &&
                task.dueAt.isBefore(endOfToday)
        }
        if (todayDueP1s.isEmpty()) {
            return RecreationEligibility(
                isEligible = false,
                earnedTodayXp = earnedTodayXp,
                spentTodayXp = safeSpent,
                availableTodayXp = availableTodayXp,
                ineligibilityReason = CreditIneligibilityReason.NO_P1_DUE_TODAY,
                activeCreditUntil = validCreditUntil
            )
        }

        // 3. Toutes les P1 du jour réussies à temps
        val allTodayP1Succeeded = todayDueP1s.all { task ->
            val completed = task.completedAt ?: return@all false
            val due = task.dueAt ?: return@all true
            !completed.isAfter(due)
        }
        if (!allTodayP1Succeeded) {
            return RecreationEligibility(
                isEligible = false,
                earnedTodayXp = earnedTodayXp,
                spentTodayXp = safeSpent,
                availableTodayXp = availableTodayXp,
                ineligibilityReason = CreditIneligibilityReason.INCOMPLETE_OR_LATE_P1_TODAY,
                activeCreditUntil = validCreditUntil
            )
        }

        // 4. Solde d'XP du jour suffisant (100 XP)
        if (availableTodayXp < costXp) {
            return RecreationEligibility(
                isEligible = false,
                earnedTodayXp = earnedTodayXp,
                spentTodayXp = safeSpent,
                availableTodayXp = availableTodayXp,
                ineligibilityReason = CreditIneligibilityReason.INSUFFICIENT_TODAY_XP,
                activeCreditUntil = validCreditUntil
            )
        }

        return RecreationEligibility(
            isEligible = true,
            earnedTodayXp = earnedTodayXp,
            spentTodayXp = safeSpent,
            availableTodayXp = availableTodayXp,
            ineligibilityReason = null,
            activeCreditUntil = validCreditUntil
        )
    }
}