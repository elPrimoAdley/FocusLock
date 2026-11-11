package com.example.focuslock.engine

import com.example.focuslock.domain.BlockDecision
import com.example.focuslock.domain.BlockReason
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.FocusMode
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RecreationCredit
import com.example.focuslock.domain.RuleType
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import com.example.focuslock.domain.toSessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

class RecreationCreditAndFocusTest {

    private val zone = ZoneId.of("America/Toronto")
    private val instagram = "com.instagram.android"

    private fun fixedClock(iso: String): Clock =
        Clock.fixed(LocalDateTime.parse(iso).atZone(zone).toInstant(), zone)

    @Test
    fun `3_21 FocusMode maps accurately to DEEP_WORK and ACTIVE_LEARNING`() {
        assertEquals(SessionType.DEEP_WORK, FocusMode.FOCUS.toSessionType())
        assertEquals(SessionType.DEEP_WORK, FocusMode.SLEEP.toSessionType())
        assertEquals(SessionType.ACTIVE_LEARNING, FocusMode.LANGUAGE.toSessionType())
        assertEquals(SessionType.ACTIVE_LEARNING, FocusMode.READING.toSessionType())
    }

    @Test
    fun `3_24 recreationCredit lifts DAILY_QUOTA until exact second and never lifts SCHEDULE or Levels 3-6`() {
        val creditUntil = fixedClock("2026-11-09T20:15:00").instant()
        val credit = RecreationCredit(packageName = null, validUntil = creditUntil)

        val quotaRule = BlockRule(
            id = "q-ig",
            title = "Quota 30m",
            ruleType = RuleType.DAILY_QUOTA,
            targetPackages = setOf(instagram),
            dailyQuotaMinutes = 30
        )
        val usageExhausted = mapOf(instagram to 45)

        // 1. 1 seconde avant l'expiration (20:14:59) -> lève le blocage de quota -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                clock = fixedClock("2026-11-09T20:14:59"),
                foregroundPackage = instagram,
                rules = listOf(quotaRule),
                todayUsageMinutes = usageExhausted,
                recreationCredit = credit
            )
        )

        // 2. À la seconde pile d'expiration (20:15:00) -> le crédit expire -> Block(SCHEDULE_OR_QUOTA)
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "q-ig", "Quota 30m"),
            RuleEngine.evaluate(
                clock = fixedClock("2026-11-09T20:15:00"),
                foregroundPackage = instagram,
                rules = listOf(quotaRule),
                todayUsageMinutes = usageExhausted,
                recreationCredit = credit
            )
        )

        // 3. Portée : le crédit ne lève JAMAIS une plage horaire (SCHEDULE - Niveau 7a) ni une session (Niveau 3)
        val scheduleRule = BlockRule(
            id = "sched-evening",
            title = "Couvre-feu",
            ruleType = RuleType.SCHEDULE,
            targetPackages = setOf(instagram),
            startTimeMinutes = 20 * 60,
            endTimeMinutes = 23 * 60
        )
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "sched-evening", "Couvre-feu"),
            RuleEngine.evaluate(
                clock = fixedClock("2026-11-09T20:10:00"),
                foregroundPackage = instagram,
                rules = listOf(scheduleRule, quotaRule),
                todayUsageMinutes = usageExhausted,
                recreationCredit = credit
            )
        )
    }

    @Test
    fun `3_24 RecreationCreditEvaluator refuses credit when any condition is missing and allows when all met`() {
        val now = fixedClock("2026-11-09T19:00:00").instant()
        val dueToday = fixedClock("2026-11-09T18:00:00").instant()

        // Cas A : Aucune P1 échue aujourd'hui -> refusé (NO_P1_DUE_TODAY)
        val noP1Res = RecreationCreditEvaluator.evaluateEligibility(
            now = now,
            zoneId = zone,
            tasks = emptyList(),
            runningSessions = emptyList(),
            spentTodayXp = 0
        )
        assertFalse(noP1Res.isEligible)
        assertEquals(CreditIneligibilityReason.NO_P1_DUE_TODAY, noP1Res.ineligibilityReason)

        // Cas B : 1 P1 échue aujourd'hui mais non terminée -> refusé (INCOMPLETE_OR_LATE_P1_TODAY)
        val incompleteP1 = QuestTask(
            id = "p1-1",
            title = "Espagnol",
            priority = QuestPriority.P1,
            xpValue = 100,
            dueAt = dueToday,
            completedAt = null
        )
        val incRes = RecreationCreditEvaluator.evaluateEligibility(
            now = now,
            zoneId = zone,
            tasks = listOf(incompleteP1),
            runningSessions = emptyList(),
            spentTodayXp = 0
        )
        assertFalse(incRes.isEligible)
        assertEquals(CreditIneligibilityReason.INCOMPLETE_OR_LATE_P1_TODAY, incRes.ineligibilityReason)

        // Cas C : P1 terminée à temps (+100 XP du jour), mais Dette de Focus active -> refusé (ACTIVE_FOCUS_DEBT)
        val completedP1 = incompleteP1.copy(completedAt = dueToday.minusSeconds(600))
        val activeDebt = FocusSession(
            id = "debt-1",
            sessionType = SessionType.FOCUS_DEBT_MALUS,
            label = "Dette",
            startedAt = now.minusSeconds(300),
            plannedEndAt = now.plusSeconds(1500),
            status = SessionStatus.RUNNING
        )
        val debtRes = RecreationCreditEvaluator.evaluateEligibility(
            now = now,
            zoneId = zone,
            tasks = listOf(completedP1),
            runningSessions = listOf(activeDebt),
            spentTodayXp = 0
        )
        assertFalse(debtRes.isEligible)
        assertEquals(CreditIneligibilityReason.ACTIVE_FOCUS_DEBT, debtRes.ineligibilityReason)

        // Cas D : P1 terminée à temps, aucune dette active, 100 XP du jour disponibles -> ÉLIGIBLE !
        val okRes = RecreationCreditEvaluator.evaluateEligibility(
            now = now,
            zoneId = zone,
            tasks = listOf(completedP1),
            runningSessions = emptyList(),
            spentTodayXp = 0
        )
        assertTrue(okRes.isEligible)
        assertEquals(100, okRes.availableTodayXp)

        // Cas E : Après avoir dépensé les 100 XP du jour -> refusé (INSUFFICIENT_TODAY_XP)
        val spentRes = RecreationCreditEvaluator.evaluateEligibility(
            now = now,
            zoneId = zone,
            tasks = listOf(completedP1),
            runningSessions = emptyList(),
            spentTodayXp = 100
        )
        assertFalse(spentRes.isEligible)
        assertEquals(CreditIneligibilityReason.INSUFFICIENT_TODAY_XP, spentRes.ineligibilityReason)
    }
}