package com.example.focuslock.engine

import com.example.focuslock.domain.GamificationConstants
import com.example.focuslock.domain.MalusDetectionResult
import com.example.focuslock.domain.MalusEvent
import com.example.focuslock.domain.PenaltyPlan
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import java.time.Instant
import java.time.LocalDate

object MalusCalculator {

    fun detect(
        now: Instant,
        tasks: List<QuestTask>,
        currentP3OverdueCount: Int
    ): MalusDetectionResult {
        val events = mutableListOf<MalusEvent>()
        var p3Count = currentP3OverdueCount
        val penalizedIds = mutableSetOf<String>()

        tasks.filter { !it.penaltyApplied && !it.isRoutineChecklistItem }.forEach { task ->
            val dueAt = task.dueAt ?: return@forEach
            val completedAt = task.completedAt

            val isOverdue = (completedAt == null && now.isAfter(dueAt)) ||
                    (completedAt != null && completedAt.isAfter(dueAt))

            if (isOverdue) {
                val priority = task.priority
                var debtMinutes = priority.focusDebtMinutes
                var triggeredP3Threshold = false

                if (priority == QuestPriority.P3) {
                    p3Count++
                    if (p3Count >= GamificationConstants.P3_OVERDUE_STRIKE_THRESHOLD) {
                        debtMinutes = GamificationConstants.P3_ACCUMULATED_DEBT_MINUTES
                        p3Count = 0
                        triggeredP3Threshold = true
                    }
                }

                events.add(
                    MalusEvent(
                        taskId = task.id,
                        priority = priority,
                        xpLost = priority.xpPenalty,
                        debtMinutes = debtMinutes,
                        quotaReductionPercent = priority.quotaReductionPercent,
                        triggeredByP3StrikeThreshold = triggeredP3Threshold
                    )
                )
                penalizedIds.add(task.id)
            }
        }

        return MalusDetectionResult(
            events = events,
            newP3OverdueCount = p3Count,
            penalizedTaskIds = penalizedIds
        )
    }

    fun aggregate(
        events: List<MalusEvent>,
        currentTotalXp: Int,
        currentDebtMinutes: Int,
        currentQuotaReductionPercent: Int,
        lastQuotaEvaluationDate: LocalDate?,
        todayDate: LocalDate
    ): PenaltyPlan {
        var runningXp = currentTotalXp
        var totalLostXp = 0
        var addedDebt = 0
        
        val baseQuotaReduction = if (lastQuotaEvaluationDate == todayDate) {
            currentQuotaReductionPercent
        } else {
            0
        }
        var newQuotaReduction = baseQuotaReduction

        events.forEach { event ->
            val lost = Math.min(runningXp, event.xpLost)
            runningXp -= lost
            totalLostXp += lost

            addedDebt += event.debtMinutes
            newQuotaReduction += event.quotaReductionPercent
        }

        val newDebt = (currentDebtMinutes + addedDebt).coerceAtMost(GamificationConstants.MAX_ACCUMULATED_DEBT_MINUTES)
        val cappedQuotaReduction = newQuotaReduction.coerceAtMost(GamificationConstants.MAX_QUOTA_REDUCTION_PERCENT)

        return PenaltyPlan(
            newTotalXp = runningXp,
            totalXpLost = totalLostXp,
            addedDebtMinutes = addedDebt,
            newTotalDebtMinutes = newDebt,
            newQuotaReductionPercent = cappedQuotaReduction,
            evaluatedDate = todayDate
        )
    }
}