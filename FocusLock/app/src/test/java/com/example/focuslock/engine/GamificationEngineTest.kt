package com.example.focuslock.engine

import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.EditPermission
import com.example.focuslock.domain.FrictionReason
import com.example.focuslock.domain.GamificationConstants
import com.example.focuslock.domain.Priority
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GamificationEngineTest {

    private val zone: ZoneId = ZoneId.of("America/Toronto")

    private fun instantOf(isoLocal: String): Instant =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant()

    // =========================================================================
    // 3.1 CONSTANTES ET TABLEAU DE LA SECTION 5
    // =========================================================================

    @Test
    fun `3_1 Priority P1 matches Section 5 table exact values`() {
        assertEquals(100, Priority.P1.xpReward)
        assertEquals(50, Priority.P1.xpPenalty)
        assertEquals(30, Priority.P1.focusDebtMinutes)
        assertEquals(50, Priority.P1.quotaReductionPercent)
    }

    @Test
    fun `3_1 Priority P2 matches Section 5 table exact values`() {
        assertEquals(50, Priority.P2.xpReward)
        assertEquals(25, Priority.P2.xpPenalty)
        assertEquals(15, Priority.P2.focusDebtMinutes)
        assertEquals(25, Priority.P2.quotaReductionPercent)
        assertEquals(3, GamificationConstants.P2_STREAK_BONUS_DAYS_THRESHOLD)
        assertEquals(15, GamificationConstants.P2_STREAK_BONUS_XP)
    }

    @Test
    fun `3_1 Priority P3 matches Section 5 table exact values and 3-strike threshold`() {
        assertEquals(20, Priority.P3.xpReward)
        assertEquals(10, Priority.P3.xpPenalty)
        assertEquals(0, Priority.P3.focusDebtMinutes)
        assertEquals(0, Priority.P3.quotaReductionPercent)
        assertEquals(3, GamificationConstants.P3_OVERDUE_STRIKE_THRESHOLD)
        assertEquals(15, GamificationConstants.P3_ACCUMULATED_DEBT_MINUTES)
    }

    @Test
    fun `3_1 Level curve contains Novice at Level 1, Structure at Level 5, Inebranlable at Level 10`() {
        val curve = GamificationConstants.LEVEL_CURVE
        assertEquals(10, curve.size)
        assertEquals("Novice", curve.first { it.level == 1 }.gradeName)
        assertEquals("Structuré", curve.first { it.level == 5 }.gradeName)
        assertEquals("Inébranlable", curve.first { it.level == 10 }.gradeName)
    }

    // =========================================================================
    // 3.2 XP REWARD ENGINE
    // =========================================================================

    @Test
    fun `3_2 XpRewardEngine awards P1, P2, P3 on time, exact due time, and 0 when late or routine checklist`() {
        val due = instantOf("2026-11-09T18:00:00")
        val p1Task = QuestTask(id = "p1", title = "Espagnol", priority = QuestPriority.P1, dueAt = due)
        val p3Task = QuestTask(id = "p3", title = "Courriels", priority = QuestPriority.P3, dueAt = due)

        // 1. P1 validée avant l'échéance -> +100 XP
        val resP1 = XpRewardEngine.evaluateTaskCompletion(
            task = p1Task,
            completedAt = instantOf("2026-11-09T17:30:00"),
            zoneId = zone,
            currentTotalXp = 200
        )
        assertEquals(100, resP1.totalEarnedXp)
        assertEquals(300, resP1.newTotalXp)
        assertTrue(resP1.countsForStreak)

        // 2. P3 validée PILE à l'échéance (completedAt == dueAt) -> +20 XP (à temps !)
        val resP3Exact = XpRewardEngine.evaluateTaskCompletion(
            task = p3Task,
            completedAt = due,
            zoneId = zone,
            currentTotalXp = 300
        )
        assertEquals(20, resP3Exact.totalEarnedXp)
        assertEquals(320, resP3Exact.newTotalXp)

        // 3. P1 validée 1 seconde en retard -> 0 XP
        val resLate = XpRewardEngine.evaluateTaskCompletion(
            task = p1Task,
            completedAt = due.plusSeconds(1),
            zoneId = zone,
            currentTotalXp = 320
        )
        assertEquals(0, resLate.totalEarnedXp)
        assertEquals(320, resLate.newTotalXp)
        assertFalse(resLate.countsForStreak)

        // 4. Élément de checklist de routine -> 0 XP
        val checklistItem = p1Task.copy(id = "chk", isRoutineChecklistItem = true)
        val resChecklist = XpRewardEngine.evaluateTaskCompletion(
            task = checklistItem,
            completedAt = instantOf("2026-11-09T07:10:00"),
            zoneId = zone,
            currentTotalXp = 320
        )
        assertEquals(0, resChecklist.totalEarnedXp)
        assertEquals(320, resChecklist.newTotalXp)
    }

    @Test
    fun `3_2 XpRewardEngine awards P2 regularity bonus +15 XP on days 3 and 4, not on day 2, and floors XP at 0`() {
        val p2Task = QuestTask(id = "p2", title = "Sport", priority = QuestPriority.P2)

        // Jour 1 -> +50 XP (streak P2 = 1)
        val day1 = XpRewardEngine.evaluateTaskCompletion(
            task = p2Task,
            completedAt = instantOf("2026-11-09T12:00:00"),
            zoneId = zone,
            currentTotalXp = 0,
            consecutiveP2Days = 0,
            lastP2CompletionDate = null
        )
        assertEquals(50, day1.totalEarnedXp)
        assertEquals(0, day1.bonusXp)
        assertEquals(1, day1.newConsecutiveP2Days)

        // Jour 2 -> +50 XP, 0 bonus (streak P2 = 2)
        val day2 = XpRewardEngine.evaluateTaskCompletion(
            task = p2Task,
            completedAt = instantOf("2026-11-10T12:00:00"),
            zoneId = zone,
            currentTotalXp = day1.newTotalXp,
            consecutiveP2Days = day1.newConsecutiveP2Days,
            lastP2CompletionDate = day1.updatedLastP2CompletionDate
        )
        assertEquals(50, day2.totalEarnedXp)
        assertEquals(0, day2.bonusXp)
        assertEquals(2, day2.newConsecutiveP2Days)

        // Jour 3 -> +50 XP + 15 XP bonus = 65 XP (streak P2 = 3)
        val day3 = XpRewardEngine.evaluateTaskCompletion(
            task = p2Task,
            completedAt = instantOf("2026-11-11T12:00:00"),
            zoneId = zone,
            currentTotalXp = day2.newTotalXp,
            consecutiveP2Days = day2.newConsecutiveP2Days,
            lastP2CompletionDate = day2.updatedLastP2CompletionDate
        )
        assertEquals(50, day3.baseXp)
        assertEquals(15, day3.bonusXp)
        assertEquals(65, day3.totalEarnedXp)
        assertEquals(3, day3.newConsecutiveP2Days)

        // Jour 4 -> +50 XP + 15 XP bonus = 65 XP (streak P2 = 4)
        val day4 = XpRewardEngine.evaluateTaskCompletion(
            task = p2Task,
            completedAt = instantOf("2026-11-12T12:00:00"),
            zoneId = zone,
            currentTotalXp = day3.newTotalXp,
            consecutiveP2Days = day3.newConsecutiveP2Days,
            lastP2CompletionDate = day3.updatedLastP2CompletionDate
        )
        assertEquals(15, day4.bonusXp)
        assertEquals(65, day4.totalEarnedXp)
        assertEquals(4, day4.newConsecutiveP2Days)

        // Vérification du plancher à 0 XP
        assertEquals(0, XpRewardEngine.applyXpDelta(currentTotalXp = 20, deltaXp = -50))
    }

    // =========================================================================
    // 3.3 DÉTECTION DES ÉCHÉANCES DÉPASSÉES (MALUS CALCULATOR)
    // =========================================================================

    @Test
    fun `3_3 MalusCalculator detects overdue tasks, respects due==now boundary, idempotence, and 3rd P3 strike`() {
        val now = instantOf("2026-11-09T18:00:00")
        val pastDue = instantOf("2026-11-09T17:00:00")

        // 1. Tâche non terminée dépassée -> événement
        val overdueP1 = QuestTask("p1-late", "Livrable", priority = QuestPriority.P1, dueAt = pastDue)
        // 2. Tâche terminée à temps -> rien
        val onTimeP1 = QuestTask(
            "p1-ok", "OK", priority = QuestPriority.P1,
            dueAt = pastDue, completedAt = instantOf("2026-11-09T16:50:00")
        )
        // 3. Tâche terminée en retard avant évaluation (completedAt > dueAt) -> événement
        val completedLateP2 = QuestTask(
            "p2-completed-late", "Sport tardif", priority = QuestPriority.P2,
            dueAt = pastDue, completedAt = instantOf("2026-11-09T17:30:00")
        )
        // 4. penaltyApplied déjà vrai -> rien (idempotence)
        val alreadyPenalized = overdueP1.copy(id = "p1-already", penaltyApplied = true)
        // 5. due == now -> pas encore en retard !
        val dueRightNow = QuestTask("p1-exact-now", "Pile maintenant", priority = QuestPriority.P1, dueAt = now)

        val detection = MalusCalculator.detect(
            now = now,
            tasks = listOf(overdueP1, onTimeP1, completedLateP2, alreadyPenalized, dueRightNow),
            currentP3OverdueCount = 0
        )

        assertEquals(2, detection.events.size)
        assertEquals(setOf("p1-late", "p2-completed-late"), detection.penalizedTaskIds)

        val p1Event = detection.events.first { it.taskId == "p1-late" }
        assertEquals(50, p1Event.xpLost)
        assertEquals(30, p1Event.debtMinutes)
        assertEquals(50, p1Event.quotaReductionPercent)

        val p2Event = detection.events.first { it.taskId == "p2-completed-late" }
        assertEquals(25, p2Event.xpLost)
        assertEquals(15, p2Event.debtMinutes)
        assertEquals(25, p2Event.quotaReductionPercent)

        // 6. Test du compteur P3 : 1re, 2e, 3e P3 en retard (dette 15 min au 3e, puis compteur repart à 0)
        val p3Tasks = listOf(
            QuestTask("p3-1", "Course 1", priority = QuestPriority.P3, dueAt = pastDue.minusSeconds(30)),
            QuestTask("p3-2", "Course 2", priority = QuestPriority.P3, dueAt = pastDue.minusSeconds(20)),
            QuestTask("p3-3", "Course 3", priority = QuestPriority.P3, dueAt = pastDue.minusSeconds(10)),
            QuestTask("p3-4", "Course 4", priority = QuestPriority.P3, dueAt = pastDue)
        )
        val p3Result = MalusCalculator.detect(now = now, tasks = p3Tasks, currentP3OverdueCount = 0)
        assertEquals(4, p3Result.events.size)
        assertEquals(0, p3Result.events[0].debtMinutes)
        assertEquals(0, p3Result.events[1].debtMinutes)
        assertEquals(15, p3Result.events[2].debtMinutes) // 3e P3 déclenche 15 min de dette !
        assertTrue(p3Result.events[2].triggeredByP3StrikeThreshold)
        assertEquals(0, p3Result.events[3].debtMinutes)  // 4e P3 = compteur reparti à 1
        assertEquals(1, p3Result.newP3OverdueCount)
    }

    // =========================================================================
    // 3.4 AGRÉGATION DES MALUS (PENALTY PLAN)
    // =========================================================================

    @Test
    fun `3_4 MalusCalculator aggregates P1+P2 same day, caps quota at 100 percent, resets on new day, and caps debt`() {
        val today = LocalDate.of(2026, 11, 9)
        val p1Event = MalusCalculator.detect(
            now = instantOf("2026-11-09T18:00:00"),
            tasks = listOf(QuestTask("p1", "T1", priority = QuestPriority.P1, dueAt = instantOf("2026-11-09T17:00:00"))),
            currentP3OverdueCount = 0
        ).events.first()
        val p2Event = MalusCalculator.detect(
            now = instantOf("2026-11-09T18:00:00"),
            tasks = listOf(QuestTask("p2", "T2", priority = QuestPriority.P2, dueAt = instantOf("2026-11-09T17:00:00"))),
            currentP3OverdueCount = 0
        ).events.first()

        // 1. P1 (-50%) + P2 (-25%) le même jour -> 75% de réduction de quota et 45 min de dette
        val planSameDay = MalusCalculator.aggregate(
            events = listOf(p1Event, p2Event),
            currentTotalXp = 60,
            currentDebtMinutes = 0,
            currentQuotaReductionPercent = 0,
            lastQuotaEvaluationDate = today,
            todayDate = today
        )
        assertEquals(75, planSameDay.newQuotaReductionPercent)
        assertEquals(45, planSameDay.newTotalDebtMinutes)
        assertEquals(0, planSameDay.newTotalXp) // 60 - 75 -> plancher 0

        // 2. Deuxième P1 le même jour : 75% + 50% -> plafonné à 100%, et dette plafonnée à 120 min
        val planCapped = MalusCalculator.aggregate(
            events = listOf(p1Event),
            currentTotalXp = 500,
            currentDebtMinutes = 105,
            currentQuotaReductionPercent = 75,
            lastQuotaEvaluationDate = today,
            todayDate = today
        )
        assertEquals(100, planCapped.newQuotaReductionPercent)
        assertEquals(120, planCapped.newTotalDebtMinutes)

        // 3. Remise à zéro du % de quota au changement de jour (lendemain)
        val tomorrow = today.plusDays(1)
        val planNextDay = MalusCalculator.aggregate(
            events = emptyList(),
            currentTotalXp = 450,
            currentDebtMinutes = 0,
            currentQuotaReductionPercent = 100,
            lastQuotaEvaluationDate = today,
            todayDate = tomorrow
        )
        assertEquals(0, planNextDay.newQuotaReductionPercent)
    }

    // =========================================================================
    // 3.5 SÉRIE (STREAK EVALUATOR) ET NIVEAU (LEVEL CALCULATOR)
    // =========================================================================

    @Test
    fun `3_5 StreakEvaluator is idempotent, increments on P1 success, and resets on 3-day gap`() {
        val day1 = LocalDate.of(2026, 11, 5)
        val p1Day1 = QuestTask(
            id = "p1-d1",
            title = "Espagnol J1",
            priority = QuestPriority.P1,
            dueAt = day1.atTime(20, 0).atZone(zone).toInstant(),
            completedAt = day1.atTime(19, 0).atZone(zone).toInstant()
        )

        // Validation le soir du Jour 1 -> série passe de 4 à 5
        val evalDay1 = StreakEvaluator.evaluate(
            now = day1.atTime(21, 0).atZone(zone).toInstant(),
            zoneId = zone,
            tasks = listOf(p1Day1),
            currentStreakDays = 4,
            lastEvaluatedEpochDay = day1.minusDays(1).toEpochDay()
        )
        assertEquals(5, evalDay1.currentStreakDays)
        assertEquals(day1.toEpochDay(), evalDay1.lastEvaluatedEpochDay)

        // Idempotence : ré-évaluer le même soir ne ré-incrémente pas la série
        val evalDay1Again = StreakEvaluator.evaluate(
            now = day1.atTime(22, 0).atZone(zone).toInstant(),
            zoneId = zone,
            tasks = listOf(p1Day1),
            currentStreakDays = evalDay1.currentStreakDays,
            lastEvaluatedEpochDay = evalDay1.lastEvaluatedEpochDay
        )
        assertEquals(5, evalDay1Again.currentStreakDays)

        // Trou de 3 jours sans ouverture de l'app (ouverture le 9 novembre) -> série tombe à 0
        val day5AfterGap = LocalDate.of(2026, 11, 9)
        val evalAfterGap = StreakEvaluator.evaluate(
            now = day5AfterGap.atTime(9, 0).atZone(zone).toInstant(),
            zoneId = zone,
            tasks = listOf(p1Day1),
            currentStreakDays = evalDay1.currentStreakDays,
            lastEvaluatedEpochDay = evalDay1.lastEvaluatedEpochDay
        )
        assertEquals(0, evalAfterGap.currentStreakDays)
    }

    @Test
    fun `3_5 LevelCalculator transitions at exact XP threshold`() {
        // 699 XP -> encore Niveau 4 (Assidu)
        val beforeLvl5 = LevelCalculator.fromXp(699)
        assertEquals(4, beforeLvl5.level)
        assertEquals("Assidu", beforeLvl5.gradeName)

        // 700 XP pile -> passage au Niveau 5 (Structuré), progression = 0.0f
        val exactLvl5 = LevelCalculator.fromXp(700)
        assertEquals(5, exactLvl5.level)
        assertEquals("Structuré", exactLvl5.gradeName)
        assertEquals(0f, exactLvl5.progressRatio, 0.0001f)

        // 3200 XP -> Niveau 10 (Inébranlable), nextLevelMinXp = null, progression = 1.0f
        val maxLvl = LevelCalculator.fromXp(3200)
        assertEquals(10, maxLvl.level)
        assertEquals("Inébranlable", maxLvl.gradeName)
        assertNull(maxLvl.nextLevelMinXp)
        assertEquals(1f, maxLvl.progressRatio, 0.0001f)
    }

    // =========================================================================
    // 3.6 EDIT GUARD (DURCIR VS ASSOUPLIR)
    // =========================================================================

    @Test
    fun `3_6 EditGuard allows tightening immediately and requires friction when loosening rules or imminent P1-P2 tasks`() {
        val now = instantOf("2026-11-09T16:00:00")
        val quotaRule = BlockRule(
            id = "r-quota",
            title = "Quota Instagram",
            ruleType = RuleType.DAILY_QUOTA,
            targetPackages = setOf("com.instagram.android"),
            isEnabled = true,
            activeDaysBitmask = DaysBitmask.WEEKDAYS,
            dailyQuotaMinutes = 30
        )

        // 1. Durcir un quota (réduire le plafond de 30 min à 15 min) -> Allowed
        assertEquals(
            EditPermission.Allowed,
            EditGuard.checkRuleEdit(quotaRule, quotaRule.copy(dailyQuotaMinutes = 15), now)
        )

        // 2. Assouplir un quota (augmenter le plafond de 30 min à 60 min) -> RequiresFriction
        assertEquals(
            EditPermission.RequiresFriction(FrictionReason.LOOSENING_DAILY_QUOTA),
            EditGuard.checkRuleEdit(quotaRule, quotaRule.copy(dailyQuotaMinutes = 60), now)
        )

        // 3. Désactiver une règle active -> RequiresFriction
        assertEquals(
            EditPermission.RequiresFriction(FrictionReason.DISABLING_ACTIVE_RULE),
            EditGuard.checkRuleEdit(quotaRule, quotaRule.copy(isEnabled = false), now)
        )

        // 4. Ajouter un package ou un jour actif (durcir) -> Allowed
        assertEquals(
            EditPermission.Allowed,
            EditGuard.checkRuleEdit(
                quotaRule,
                quotaRule.copy(
                    targetPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
                    activeDaysBitmask = DaysBitmask.ALL_DAYS
                ),
                now
            )
        )

        // 5. Supprimer une tâche P1 ou P2 engagée -> RequiresFriction (P3 -> Allowed)
        val p1Task = QuestTask(
            id = "q1",
            title = "Étude Espagnol",
            priority = QuestPriority.P1,
            dueAt = instantOf("2026-11-09T17:30:00") // Dans 1h30 (< 2h !)
        )
        val p3Task = p1Task.copy(id = "q3", priority = QuestPriority.P3)

        assertEquals(
            EditPermission.RequiresFriction(FrictionReason.DELETING_ENGAGED_PRIORITY_TASK),
            EditGuard.checkTaskDeletion(p1Task)
        )
        assertEquals(EditPermission.Allowed, EditGuard.checkTaskDeletion(p3Task))

        // 6. Repousser l'échéance d'une P1 à moins de 2h du terme -> RequiresFriction
        assertEquals(
            EditPermission.RequiresFriction(FrictionReason.POSTPONING_IMMINENT_DEADLINE),
            EditGuard.checkTaskEdit(
                oldTask = p1Task,
                newTask = p1Task.copy(dueAt = instantOf("2026-11-09T20:00:00")),
                now = now
            )
        )

        // 7. Repousser l'échéance d'une P1 quand il reste plus de 2h (ex. 3h avant) -> Allowed
        val nowThreeHoursBefore = instantOf("2026-11-09T14:00:00")
        assertEquals(
            EditPermission.Allowed,
            EditGuard.checkTaskEdit(
                oldTask = p1Task,
                newTask = p1Task.copy(dueAt = instantOf("2026-11-09T20:00:00")),
                now = nowThreeHoursBefore
            )
        )
    }
}