package com.example.focuslock.engine.insights

import com.example.focuslock.domain.AppCategory
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import com.example.focuslock.domain.repository.DailyAppUsageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class InsightsAndHeatmapCalculatorTest {

    private val zone = ZoneId.of("America/Toronto")
    private val ig = "com.instagram.android"

    private fun instantOf(iso: String) = LocalDateTime.parse(iso).atZone(zone).toInstant()

    @Test
    fun `4_15 formatScreenTimeTotal formats 0m, 59m, exact 1h, and 1h 40m`() {
        assertEquals("0m", InsightsCalculator.formatScreenTimeTotal(0))
        assertEquals("59m", InsightsCalculator.formatScreenTimeTotal(59))
        assertEquals("1h 00m", InsightsCalculator.formatScreenTimeTotal(60))
        assertEquals("1h 40m", InsightsCalculator.formatScreenTimeTotal(100))
    }

    @Test
    fun `4_14 buildWeekSummary aligns on Monday, marks missing days unavailable, and classifies unassigned apps`() {
        val wednesday = LocalDate.of(2026, 11, 11) // Semaine du lundi 9 nov au dimanche 15 nov
        val mondayEpoch = LocalDate.of(2026, 11, 9).toEpochDay()
        // Lundi = 40 min dans DailyUsageEntity ; Mardi = manquant (trou) ; Mercredi = aujourd'hui (60 min)
        val records = listOf(
            DailyAppUsageRecord(mondayEpoch, ig, 40 * 60_000L)
        )

        val summary = InsightsCalculator.buildWeekSummary(
            anchorDate = wednesday,
            todayDate = wednesday,
            historyRecords = records,
            todayMinutesByPackage = mapOf(ig to 60),
            explicitCategoryByPackage = emptyMap(), // non classé -> assignedCategory == null ("À classer"), compté NEUTRAL
            now = instantOf("2026-11-11T15:00:00"),
            zoneId = zone,
            awakeStartMinutes = 7 * 60,
            awakeEndMinutes = 23 * 60
        )

        assertEquals(7, summary.bars.size)
        assertTrue(summary.bars[0].isAvailable)   // Lundi disponible (40m)
        assertFalse(summary.bars[1].isAvailable)  // Mardi manquant -> Indisponible !
        assertTrue(summary.bars[2].isAvailable)   // Mercredi (aujourd'hui) disponible (60m)
        assertFalse(summary.bars[3].isAvailable)  // Jeudi (futur) -> Indisponible

        assertEquals(100, summary.totalMinutes)
        assertEquals("1h 40m", summary.formattedTotal)
        assertEquals(null, summary.topApps.first().assignedCategory)
        assertEquals(AppCategory.NEUTRAL, summary.topApps.first().effectiveCategory)
    }

    @Test
    fun `4_14 buildTrendSummary computes 8-week daily average and delta against previous week`() {
        val todayMonday = LocalDate.of(2026, 11, 9)
        val prevWeekMonday = todayMonday.minusWeeks(1)

        // Semaine précédente (S-1) : 2 jours enregistrés à 60 min et 80 min -> moyenne = 70 min/j
        val records = listOf(
            DailyAppUsageRecord(prevWeekMonday.toEpochDay(), ig, 60 * 60_000L),
            DailyAppUsageRecord(prevWeekMonday.plusDays(1).toEpochDay(), ig, 80 * 60_000L)
        )

        // Semaine courante (S0) : aujourd'hui = 50 min/j -> écart = -20m / jour vs semaine précédente
        val trend = InsightsCalculator.buildTrendSummary(
            anchorDate = todayMonday,
            todayDate = todayMonday,
            historyRecords = records,
            todayMinutesByPackage = mapOf(ig to 50),
            explicitCategoryByPackage = mapOf(ig to AppCategory.DISTRACTING),
            now = instantOf("2026-11-09T18:00:00"),
            zoneId = zone,
            awakeStartMinutes = 7 * 60,
            awakeEndMinutes = 23 * 60
        )

        assertEquals(8, trend.bars.size)
        assertEquals(50, trend.totalMinutes)
        assertEquals("-20m / jour vs semaine précédente", trend.trendDeltaText)
    }

    @Test
    fun `4_19 HeatmapCalculator splits midnight-crossing session across two days and handles 28 to 31 day months`() {
        // Session de 23:30 le 9 nov à 01:00 le 10 nov (30m le 9 nov = niveau 2, 60m le 10 nov = niveau 3)
        val midnightSession = FocusSession(
            id = "s-mid",
            sessionType = SessionType.DEEP_WORK,
            label = "Travail",
            startedAt = instantOf("2026-11-09T23:30:00"),
            plannedEndAt = instantOf("2026-11-10T01:00:00"),
            actualEndedAt = instantOf("2026-11-10T01:00:00"),
            status = SessionStatus.COMPLETED
        )

        val p1OnNov9 = QuestTask(
            id = "p1",
            title = "Espagnol",
            priority = QuestPriority.P1,
            dueAt = instantOf("2026-11-09T20:00:00"),
            completedAt = instantOf("2026-11-09T19:00:00")
        )

        val novGrid = HeatmapCalculator.buildMonthlyGrid(
            yearMonth = YearMonth.of(2026, 11), // 30 jours, 1er nov 2026 = Dimanche -> 6 cellules vides en tête
            zoneId = zone,
            sessions = listOf(midnightSession),
            tasks = listOf(p1OnNov9)
        )

        assertEquals(6, novGrid.leadingEmptyCells)
        assertEquals(30, novGrid.dayCells.size)

        val cellNov9 = novGrid.dayCells[8] // index 8 = 9 novembre
        assertEquals(30, cellNov9.focusMinutes)
        assertEquals(2, cellNov9.intensityLevel)
        assertTrue(cellNov9.isStreakValidated)

        val cellNov10 = novGrid.dayCells[9] // index 9 = 10 novembre
        assertEquals(60, cellNov10.focusMinutes)
        assertEquals(3, cellNov10.intensityLevel)
        assertFalse(cellNov10.isStreakValidated)

        // Vérifie Février 2026 (28 jours) et Octobre 2026 (31 jours)
        assertEquals(28, HeatmapCalculator.buildMonthlyGrid(YearMonth.of(2026, 2), zone, emptyList(), emptyList()).dayCells.size)
        assertEquals(31, HeatmapCalculator.buildMonthlyGrid(YearMonth.of(2026, 10), zone, emptyList(), emptyList()).dayCells.size)
    }
}
