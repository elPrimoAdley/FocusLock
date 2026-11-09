package com.example.focuslock.engine

import com.example.focuslock.domain.GamificationConstants
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.XpAwardResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object XpRewardEngine {

    fun evaluateTaskCompletion(
        task: QuestTask,
        completedAt: Instant,
        zoneId: ZoneId,
        currentTotalXp: Int,
        consecutiveP2Days: Int = 0,
        lastP2CompletionDate: LocalDate? = null
    ): XpAwardResult {
        val safeCurrentXp = currentTotalXp.coerceAtLeast(GamificationConstants.MIN_TOTAL_XP)

        // 1. Les sous-tâches de checklist de routine n'ont ni gain ni malus
        if (task.isRoutineChecklistItem) {
            return XpAwardResult(
                baseXp = 0,
                bonusXp = 0,
                totalEarnedXp = 0,
                newTotalXp = safeCurrentXp,
                newConsecutiveP2Days = consecutiveP2Days,
                updatedLastP2CompletionDate = lastP2CompletionDate,
                countsForStreak = false
            )
        }

        // 2. Vérifie si la tâche est validée à temps (completedAt <= dueAt)
        val isOnTime = task.dueAt == null || !completedAt.isAfter(task.dueAt)
        if (!isOnTime) {
            val resetP2Streak = if (task.priority == QuestPriority.P2) 0 else consecutiveP2Days
            return XpAwardResult(
                baseXp = 0,
                bonusXp = 0,
                totalEarnedXp = 0,
                newTotalXp = safeCurrentXp,
                newConsecutiveP2Days = resetP2Streak,
                updatedLastP2CompletionDate = lastP2CompletionDate,
                countsForStreak = false
            )
        }

        val completionDate = completedAt.atZone(zoneId).toLocalDate()
        val baseXp = task.priority.xpReward

        // 3. Calcul du bonus de régularité P2 (+15 XP si >= 3 jours consécutifs)
        var newP2Streak = consecutiveP2Days
        var newLastP2Date = lastP2CompletionDate
        var bonusXp = 0

        if (task.priority == QuestPriority.P2) {
            newP2Streak = when (lastP2CompletionDate) {
                null -> 1
                completionDate -> consecutiveP2Days.coerceAtLeast(1)
                completionDate.minusDays(1) -> consecutiveP2Days + 1
                else -> 1
            }
            newLastP2Date = completionDate
            // N'accorde le bonus qu'une seule fois par jour éligible (>= 3e jour consécutif)
            val alreadyAwardedToday = (lastP2CompletionDate == completionDate)
            if (newP2Streak >= GamificationConstants.P2_STREAK_BONUS_DAYS_THRESHOLD && !alreadyAwardedToday) {
                bonusXp = GamificationConstants.P2_STREAK_BONUS_XP
            }
        }

        val totalEarned = baseXp + bonusXp
        val newTotalXp = (safeCurrentXp + totalEarned).coerceAtLeast(GamificationConstants.MIN_TOTAL_XP)

        return XpAwardResult(
            baseXp = baseXp,
            bonusXp = bonusXp,
            totalEarnedXp = totalEarned,
            newTotalXp = newTotalXp,
            newConsecutiveP2Days = newP2Streak,
            updatedLastP2CompletionDate = newLastP2Date,
            countsForStreak = task.priority == QuestPriority.P1
        )
    }

    /**
     * Applique une variation d'XP (gain ou perte) en garantissant le plancher à 0.
     */
    fun applyXpDelta(currentTotalXp: Int, deltaXp: Int): Int {
        return (currentTotalXp + deltaXp).coerceAtLeast(GamificationConstants.MIN_TOTAL_XP)
    }
}