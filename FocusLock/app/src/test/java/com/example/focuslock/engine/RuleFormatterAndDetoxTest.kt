package com.example.focuslock.engine

import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RuleFormatterAndDetoxTest {

    private val zone = ZoneId.of("America/Toronto")
    private val instagram = setOf("com.instagram.android")

    @Test
    fun `3_16 formatRuleSummary formats daytime schedule, overnight curfew, and daily quota in mono format`() {
        val now = LocalDateTime.parse("2026-11-09T14:00:00").atZone(zone).toInstant()

        val workSchedule = BlockRule(
            id = "r1",
            title = "Travail",
            ruleType = RuleType.SCHEDULE,
            targetPackages = instagram,
            activeDaysBitmask = DaysBitmask.WEEKDAYS,
            startTimeMinutes = 9 * 60,
            endTimeMinutes = 17 * 60
        )
        assertEquals("09:00–17:00 • Lun–Ven", RuleFormatterAndDetoxCalculator.formatRuleSummary(workSchedule, now, zone))

        val nightCurfew = workSchedule.copy(
            id = "r2",
            activeDaysBitmask = DaysBitmask.ALL_DAYS,
            startTimeMinutes = 22 * 60 + 30,
            endTimeMinutes = 7 * 60
        )
        assertEquals(
            "22:30–07:00 (+1j) • Tous les jours",
            RuleFormatterAndDetoxCalculator.formatRuleSummary(nightCurfew, now, zone)
        )

        val quotaRule = BlockRule(
            id = "r3",
            title = "Quota",
            ruleType = RuleType.DAILY_QUOTA,
            targetPackages = instagram,
            activeDaysBitmask = DaysBitmask.WEEKEND,
            dailyQuotaMinutes = 30
        )
        assertEquals("Quota 30m/j • Sam–Dim", RuleFormatterAndDetoxCalculator.formatRuleSummary(quotaRule, now, zone))
    }

    @Test
    fun `3_18 computeQuotaRingStatus applies malus reduction and computes remaining minutes`() {
        val status = RuleFormatterAndDetoxCalculator.computeQuotaRingStatus(
            baseQuotaMinutes = 30,
            quotaMalusReductionPercent = 50,
            usedMinutes = 10
        )
        assertEquals(15, status.effectiveQuotaMinutes)
        assertEquals(5, status.remainingMinutes)
        assertEquals(10f / 15f, status.usedFraction, 0.001f)
    }

    @Test
    fun `3_19 nextReopenDate handles 1-out-of-2 cycle, 1-out-of-N cycle, and Saturday-only preset`() {
        val startMonday = LocalDate.of(2026, 11, 9)
        val detox1In2 = BlockRule(
            id = "d-1-2",
            title = "Détox 1j/2",
            ruleType = RuleType.ALTERNATING_DETOX,
            targetPackages = instagram,
            cycleIntervalDays = 2,
            cycleStartEpochDay = startMonday.toEpochDay()
        )

        assertEquals(startMonday, RuleFormatterAndDetoxCalculator.nextReopenDate(detox1In2, startMonday))

        val tuesday = LocalDate.of(2026, 11, 10)
        assertEquals(
            LocalDate.of(2026, 11, 11),
            RuleFormatterAndDetoxCalculator.nextReopenDate(detox1In2, tuesday)
        )

        val monday9Nov = LocalDate.of(2026, 11, 9)
        val saturdayStartEpoch = RuleFormatterAndDetoxCalculator.saturdayOnlyCycleStartEpochDay(monday9Nov)
        assertEquals(LocalDate.of(2026, 11, 7).toEpochDay(), saturdayStartEpoch)

        val saturdayOnlyRule = BlockRule(
            id = "d-sat",
            title = "Uniquement le samedi",
            ruleType = RuleType.ALTERNATING_DETOX,
            targetPackages = instagram,
            cycleIntervalDays = 7,
            cycleStartEpochDay = saturdayStartEpoch
        )

        assertEquals(
            LocalDate.of(2026, 11, 14),
            RuleFormatterAndDetoxCalculator.nextReopenDate(saturdayOnlyRule, monday9Nov)
        )

        val saturday14Nov = LocalDate.of(2026, 11, 14)
        assertEquals(
            saturday14Nov,
            RuleFormatterAndDetoxCalculator.nextReopenDate(saturdayOnlyRule, saturday14Nov)
        )

        assertNull(
            RuleFormatterAndDetoxCalculator.nextReopenDate(detox1In2.copy(cycleIntervalDays = 0), monday9Nov)
        )
    }

    @Test
    fun `3_20 formatSabbaticalOverlayPill formats exact pill text`() {
        val until = LocalDateTime.parse("2026-11-16T08:00:00").atZone(zone).toInstant()
        assertEquals(
            "PAUSE SABBATIQUE • JUSQU'AU 16/11/2026",
            RuleFormatterAndDetoxCalculator.formatSabbaticalOverlayPill(until, zone)
        )
    }
}