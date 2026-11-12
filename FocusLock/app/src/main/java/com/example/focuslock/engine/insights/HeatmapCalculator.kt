package com.example.focuslock.engine.insights

import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class HeatmapDayCell(
    val date: LocalDate,
    val focusMinutes: Int,
    val intensityLevel: Int, // 0..4 (0=0m, 1=1..29m, 2=30..59m, 3=60..119m, 4=>=120m)
    val isStreakValidated: Boolean
)

data class MonthlyHeatmapGrid(
    val yearMonth: YearMonth,
    val leadingEmptyCells: Int, // 0 si le 1er du mois est un Lundi .. 6 si Dimanche
    val dayCells: List<HeatmapDayCell>
)

object HeatmapCalculator {

    fun intensityLevelForMinutes(minutes: Int): Int = when {
        minutes <= 0 -> 0
        minutes in 1..29 -> 1
        minutes in 30..59 -> 2
        minutes in 60..119 -> 3
        else -> 4
    }

    /**
     * 4.19 : Génère la grille mensuelle (7 colonnes Lundi -> Dimanche).
     * Répartit les sessions COMPLETED à cheval sur minuit sur chaque jour concerné.
     */
    fun buildMonthlyGrid(
        yearMonth: YearMonth,
        zoneId: ZoneId,
        sessions: List<FocusSession>,
        tasks: List<QuestTask>
    ): MonthlyHeatmapGrid {
        val firstDay = yearMonth.atDay(1)
        val daysInMonth = yearMonth.lengthOfMonth()
        // DayOfWeek.MONDAY.value == 1 -> offset 0 ; SUNDAY.value == 7 -> offset 6
        val leadingEmpty = firstDay.dayOfWeek.value - 1

        val completedSessions = sessions.filter {
            it.status == SessionStatus.COMPLETED && it.sessionType != SessionType.FOCUS_DEBT_MALUS
        }

        val cells = (1..daysInMonth).map { dayOfMonth ->
            val date = yearMonth.atDay(dayOfMonth)
            val dayStart = date.atStartOfDay(zoneId).toInstant()
            val nextDayStart = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            var dayFocusMillis = 0L
            for (session in completedSessions) {
                val end = session.actualEndedAt ?: session.plannedEndAt
                val clampedStart = if (session.startedAt.isBefore(dayStart)) dayStart else session.startedAt
                val clampedEnd = if (end.isAfter(nextDayStart)) nextDayStart else end
                if (clampedEnd.isAfter(clampedStart)) {
                    dayFocusMillis += (clampedEnd.toEpochMilli() - clampedStart.toEpochMilli())
                }
            }

            val focusMinutes = (dayFocusMillis / 60_000L).toInt()
            val streakValid = isDayStreakValidated(date, zoneId, tasks)

            HeatmapDayCell(
                date = date,
                focusMinutes = focusMinutes,
                intensityLevel = intensityLevelForMinutes(focusMinutes),
                isStreakValidated = streakValid
            )
        }

        return MonthlyHeatmapGrid(
            yearMonth = yearMonth,
            leadingEmptyCells = leadingEmpty,
            dayCells = cells
        )
    }

    /**
     * Même critère de validation quotidienne que StreakEvaluator (3.5) :
     * - Si des P1 existent ce jour-là : toutes les P1 sont terminées à temps.
     * - Sinon : au moins une P2 est terminée à temps ce jour-là.
     */
    private fun isDayStreakValidated(
        date: LocalDate,
        zoneId: ZoneId,
        tasks: List<QuestTask>
    ): Boolean {
        val dayTasks = tasks.filter { task ->
            if (task.isRoutineChecklistItem) return@filter false
            val taskDate = task.dueAt?.atZone(zoneId)?.toLocalDate()
                ?: task.completedAt?.atZone(zoneId)?.toLocalDate()
            taskDate == date
        }
        val p1s = dayTasks.filter { it.priority == QuestPriority.P1 }
        if (p1s.isNotEmpty()) {
            return p1s.all { t ->
                t.completedAt != null && (t.dueAt == null || !t.completedAt.isAfter(t.dueAt))
            }
        }
        return dayTasks.any { t ->
            t.priority == QuestPriority.P2 &&
                t.completedAt != null &&
                (t.dueAt == null || !t.completedAt.isAfter(t.dueAt))
        }
    }
}
