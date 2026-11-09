package com.example.focuslock.engine

import com.example.focuslock.domain.GamificationConstants
import com.example.focuslock.domain.LevelProgress
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.StreakEvaluationResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StreakEvaluator {

    /**
     * Évalue la série de façon idempotente en rattrapant tous les jours écoulés
     * depuis [lastEvaluatedEpochDay] jusqu'à la date de [now].
     */
    fun evaluate(
        now: Instant,
        zoneId: ZoneId,
        tasks: List<QuestTask>,
        currentStreakDays: Int,
        lastEvaluatedEpochDay: Long?
    ): StreakEvaluationResult {
        val today = now.atZone(zoneId).toLocalDate()
        val todayEpochDay = today.toEpochDay()

        // Premier lancement sans historique : on initialise au jour précédent et on évalue aujourd'hui
        val startEpochDay = if (lastEvaluatedEpochDay == null) {
            todayEpochDay
        } else {
            lastEvaluatedEpochDay + 1L
        }

        // Idempotence : si déjà évalué aujourd'hui (ou dans le futur), on ne recompte pas les jours passés
        if (lastEvaluatedEpochDay != null && startEpochDay > todayEpochDay) {
            return StreakEvaluationResult(
                currentStreakDays = currentStreakDays.coerceAtLeast(0),
                lastEvaluatedEpochDay = lastEvaluatedEpochDay
            )
        }

        var streak = currentStreakDays.coerceAtLeast(0)
        var lastProcessedDay = lastEvaluatedEpochDay ?: (todayEpochDay - 1L)

        // 1. Rattrapage séquentiel des jours passés révolus (startEpochDay .. todayEpochDay - 1)
        for (epochDay in startEpochDay until todayEpochDay) {
            val dayOutcome = evaluateCompletedPastDay(epochDay, zoneId, tasks)
            streak = when (dayOutcome) {
                DayStreakOutcome.SUCCESS -> streak + 1
                DayStreakOutcome.BROKEN -> 0
            }
            lastProcessedDay = epochDay
        }

        // 2. Évaluation du jour courant (todayEpochDay) si toutes ses P1 sont déjà validées ou si l'une a échoué
        val todayOutcome = evaluateCurrentDay(todayEpochDay, now, zoneId, tasks)
        when (todayOutcome) {
            CurrentDayOutcome.COMPLETED_SUCCESS -> {
                streak += 1
                lastProcessedDay = todayEpochDay
            }
            CurrentDayOutcome.BROKEN_TODAY -> {
                streak = 0
                lastProcessedDay = todayEpochDay
            }
            CurrentDayOutcome.PENDING -> {
                // La journée est encore en cours : on conserve la série acquise jusqu'à hier
            }
        }

        return StreakEvaluationResult(
            currentStreakDays = streak,
            lastEvaluatedEpochDay = lastProcessedDay
        )
    }

    private enum class DayStreakOutcome { SUCCESS, BROKEN }
    private enum class CurrentDayOutcome { COMPLETED_SUCCESS, BROKEN_TODAY, PENDING }

    private fun evaluateCompletedPastDay(
        epochDay: Long,
        zoneId: ZoneId,
        tasks: List<QuestTask>
    ): DayStreakOutcome {
        val dayTasks = tasksForEpochDay(epochDay, zoneId, tasks)
        val p1Tasks = dayTasks.filter { it.priority == QuestPriority.P1 }

        if (p1Tasks.isNotEmpty()) {
            val allP1OnTime = p1Tasks.all { task ->
                val completedAt = task.completedAt
                val dueAt = task.dueAt
                completedAt != null && (dueAt == null || !completedAt.isAfter(dueAt))
            }
            return if (allP1OnTime) DayStreakOutcome.SUCCESS else DayStreakOutcome.BROKEN
        }

        // Si aucune P1 n'était planifiée ce jour-là, une P2 complétée à temps maintient la série ;
        // sinon (jour d'inactivité / trou), la série se brise.
        val anyP2CompletedOnTime = dayTasks.any { task ->
            task.priority == QuestPriority.P2 &&
                task.completedAt != null &&
                (task.dueAt == null || !task.completedAt.isAfter(task.dueAt))
        }
        return if (anyP2CompletedOnTime) DayStreakOutcome.SUCCESS else DayStreakOutcome.BROKEN
    }

    private fun evaluateCurrentDay(
        todayEpochDay: Long,
        now: Instant,
        zoneId: ZoneId,
        tasks: List<QuestTask>
    ): CurrentDayOutcome {
        val todayTasks = tasksForEpochDay(todayEpochDay, zoneId, tasks)
        val p1Tasks = todayTasks.filter { it.priority == QuestPriority.P1 }
        if (p1Tasks.isEmpty()) return CurrentDayOutcome.PENDING

        val anyP1Failed = p1Tasks.any { task ->
            val dueAt = task.dueAt ?: return@any false
            val completedAt = task.completedAt
            (completedAt == null && now.isAfter(dueAt)) ||
                (completedAt != null && completedAt.isAfter(dueAt))
        }
        if (anyP1Failed) return CurrentDayOutcome.BROKEN_TODAY

        val allP1DoneOnTime = p1Tasks.all { task ->
            val completedAt = task.completedAt ?: return@all false
            val dueAt = task.dueAt
            dueAt == null || !completedAt.isAfter(dueAt)
        }
        return if (allP1DoneOnTime) CurrentDayOutcome.COMPLETED_SUCCESS else CurrentDayOutcome.PENDING
    }

    private fun tasksForEpochDay(
        epochDay: Long,
        zoneId: ZoneId,
        tasks: List<QuestTask>
    ): List<QuestTask> {
        val targetDate = LocalDate.ofEpochDay(epochDay)
        return tasks.filter { task ->
            if (task.isRoutineChecklistItem) return@filter false
            val taskDate = task.dueAt?.atZone(zoneId)?.toLocalDate()
                ?: task.completedAt?.atZone(zoneId)?.toLocalDate()
            taskDate == targetDate
        }
    }
}

object LevelCalculator {

    fun fromXp(totalXp: Int): LevelProgress {
        val safeXp = totalXp.coerceAtLeast(GamificationConstants.MIN_TOTAL_XP)
        val curve = GamificationConstants.LEVEL_CURVE

        val currentIdx = curve.indexOfLast { safeXp >= it.minXp }.coerceAtLeast(0)
        val currentThreshold = curve[currentIdx]
        val nextThreshold = curve.getOrNull(currentIdx + 1)

        val progressRatio = if (nextThreshold == null) {
            1.0f
        } else {
            val span = (nextThreshold.minXp - currentThreshold.minXp).coerceAtLeast(1)
            val earnedInLevel = safeXp - currentThreshold.minXp
            (earnedInLevel.toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }

        return LevelProgress(
            level = currentThreshold.level,
            gradeName = currentThreshold.gradeName,
            totalXp = safeXp,
            currentLevelMinXp = currentThreshold.minXp,
            nextLevelMinXp = nextThreshold?.minXp,
            progressRatio = progressRatio
        )
    }
}