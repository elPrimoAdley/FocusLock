package com.example.focuslock.engine.usage

import com.example.focuslock.domain.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageIntervalAndAggregatorTest {

    private val zone = ZoneId.of("America/Toronto")
    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private val systemUi = "com.android.systemui"

    private fun instantOf(iso: String): Instant =
        LocalDateTime.parse(iso).atZone(zone).toInstant()

    // =========================================================================
    // 4.10 USAGE INTERVAL BUILDER
    // =========================================================================

    @Test
    fun `4_10 simple resume and pause produces single interval and ignores excluded packages`() {
        val now = instantOf("2026-11-09T12:00:00")
        val events = listOf(
            RawUsageEvent(instantOf("2026-11-09T10:00:00"), systemUi, "StatusBar", RawEventType.ACTIVITY_RESUMED),
            RawUsageEvent(instantOf("2026-11-09T10:05:00"), ig, "MainActivity", RawEventType.ACTIVITY_RESUMED),
            RawUsageEvent(instantOf("2026-11-09T10:25:00"), ig, "MainActivity", RawEventType.ACTIVITY_PAUSED)
        )

        val intervals = UsageIntervalBuilder.buildIntervals(
            events = events,
            now = now,
            ignoredPackages = setOf(systemUi)
        )

        assertEquals(1, intervals.size)
        assertEquals(ig, intervals[0].packageName)
        assertEquals(20 * 60_000L, intervals[0].durationMillis)
    }

    @Test
    fun `4_10 two activities of the same app transition without gap or double counting`() {
        val now = instantOf("2026-11-09T12:00:00")
        // FeedActivity démarre à 10:00, ReelActivity démarre à 10:10, FeedActivity pause à 10:11, ReelActivity pause à 10:30
        val events = listOf(
            RawUsageEvent(instantOf("2026-11-09T10:00:00"), ig, "FeedActivity", RawEventType.ACTIVITY_RESUMED),
            RawUsageEvent(instantOf("2026-11-09T10:10:00"), ig, "ReelActivity", RawEventType.ACTIVITY_RESUMED),
            RawUsageEvent(instantOf("2026-11-09T10:11:00"), ig, "FeedActivity", RawEventType.ACTIVITY_PAUSED),
            RawUsageEvent(instantOf("2026-11-09T10:30:00"), ig, "ReelActivity", RawEventType.ACTIVITY_PAUSED)
        )

        val intervals = UsageIntervalBuilder.buildIntervals(events, now)
        assertEquals(1, intervals.size)
        assertEquals(instantOf("2026-11-09T10:00:00"), intervals[0].start)
        assertEquals(instantOf("2026-11-09T10:30:00"), intervals[0].end)
        assertEquals(30 * 60_000L, intervals[0].durationMillis)
    }

    @Test
    fun `4_10 missing pause closes interval at SCREEN_NON_INTERACTIVE or at now`() {
        val now = instantOf("2026-11-09T11:00:00")
        val events = listOf(
            // Session 1 : pas de PAUSE, mais écran éteint à 10:15
            RawUsageEvent(instantOf("2026-11-09T10:00:00"), ig, "Main", RawEventType.ACTIVITY_RESUMED),
            RawUsageEvent(instantOf("2026-11-09T10:15:00"), null, null, RawEventType.SCREEN_NON_INTERACTIVE),
            // Session 2 : écran rallumé et app reprise à 10:40, toujours ouverte à [now] (11:00)
            RawUsageEvent(instantOf("2026-11-09T10:40:00"), null, null, RawEventType.SCREEN_INTERACTIVE),
            RawUsageEvent(instantOf("2026-11-09T10:40:00"), ig, "Main", RawEventType.ACTIVITY_RESUMED)
        )

        val intervals = UsageIntervalBuilder.buildIntervals(events, now)
        assertEquals(2, intervals.size)
        assertEquals(15 * 60_000L, intervals[0].durationMillis)
        assertEquals(20 * 60_000L, intervals[1].durationMillis)
    }

    // =========================================================================
    // 4.11 USAGE AGGREGATOR (UNION, FRONTIÈRES & DST 23H / 25H)
    // =========================================================================

    @Test
    fun `4_11 simultaneous Samsung pop-up windows do not double count total screen time via union`() {
        val date = LocalDate.of(2026, 11, 9)
        val now = instantOf("2026-11-09T12:00:00")
        // Instagram et YouTube ouverts simultanément en écran scindé de 10:00 à 10:30 (30 min chacun)
        val events = listOf(
            RawUsageEvent(instantOf("2026-11-09T10:00:00"), ig, "A", RawEventType.ACTIVITY_RESUMED),
            RawUsageEvent(instantOf("2026-11-09T10:00:00"), yt, "B", RawEventType.ACTIVITY_RESUMED),
            RawUsageEvent(instantOf("2026-11-09T10:30:00"), ig, "A", RawEventType.ACTIVITY_PAUSED),
            RawUsageEvent(instantOf("2026-11-09T10:30:00"), yt, "B", RawEventType.ACTIVITY_PAUSED)
        )

        val intervals = UsageIntervalBuilder.buildIntervals(events, now)
        val agg = UsageAggregator.aggregateForDay(
            intervals = intervals,
            date = date,
            zoneId = zone,
            categoryByPackage = mapOf(ig to AppCategory.DISTRACTING, yt to AppCategory.NEUTRAL)
        )

        // Chaque app a bien 30 min individuellement pour ses quotas, mais le temps d'écran total (union) est de 30 min !
        assertEquals(30, agg.minutesByPackage[ig])
        assertEquals(30, agg.minutesByPackage[yt])
        assertEquals(30, agg.totalScreenTimeMinutes)
        assertEquals(30f, agg.hourlyBuckets[10].totalMinutes, 0.01f)
    }

    @Test
    fun `4_11 interval crossing midnight and hour boundary is split accurately between days and hours`() {
        // De 23:40 le 9 nov à 00:25 le 10 nov (45 min au total : 20 min le 9 nov, 25 min le 10 nov)
        val interval = UsageInterval(
            packageName = ig,
            start = instantOf("2026-11-09T23:40:00"),
            end = instantOf("2026-11-10T00:25:00")
        )

        val day1Agg = UsageAggregator.aggregateForDay(
            intervals = listOf(interval),
            date = LocalDate.of(2026, 11, 9),
            zoneId = zone,
            categoryByPackage = mapOf(ig to AppCategory.DISTRACTING)
        )
        val day2Agg = UsageAggregator.aggregateForDay(
            intervals = listOf(interval),
            date = LocalDate.of(2026, 11, 10),
            zoneId = zone,
            categoryByPackage = mapOf(ig to AppCategory.DISTRACTING)
        )

        assertEquals(20, day1Agg.totalScreenTimeMinutes)
        assertEquals(20f, day1Agg.hourlyBuckets[23].distractingMinutes, 0.01f)

        assertEquals(25, day2Agg.totalScreenTimeMinutes)
        assertEquals(25f, day2Agg.hourlyBuckets[0].distractingMinutes, 0.01f)
    }

    @Test
    fun `4_11 handles 23h spring-forward and 25h fall-back DST days and never exceeds elapsed time`() {
        // 8 mars 2026 à Toronto = passage à l'heure d'été (journée de 23h)
        val springDstDate = LocalDate.of(2026, 3, 8)
        val springStart = springDstDate.atStartOfDay(zone).toInstant()
        val springEnd = springDstDate.plusDays(1).atStartOfDay(zone).toInstant()
        val fullSpringInterval = UsageInterval(ig, springStart, springEnd)

        val springAgg = UsageAggregator.aggregateForDay(listOf(fullSpringInterval), springDstDate, zone)
        assertEquals(23 * 60, springAgg.totalScreenTimeMinutes)
        assertTrue(springAgg.hourlyBuckets.all { it.totalMinutes <= 60f })

        // 1er novembre 2026 à Toronto = passage à l'heure d'hiver (journée de 25h)
        val fallDstDate = LocalDate.of(2026, 11, 1)
        val fallStart = fallDstDate.atStartOfDay(zone).toInstant()
        val fallEnd = fallDstDate.plusDays(1).atStartOfDay(zone).toInstant()
        val fullFallInterval = UsageInterval(ig, fallStart, fallEnd)

        val fallAgg = UsageAggregator.aggregateForDay(listOf(fullFallInterval), fallDstDate, zone)
        val realElapsedMinutes = ((fallEnd.toEpochMilli() - fallStart.toEpochMilli()) / 60_000L).toInt()
        assertEquals(25 * 60, realElapsedMinutes)
        assertEquals(realElapsedMinutes, fallAgg.totalScreenTimeMinutes)
        // Même lors du doublement de l'heure 01:00, la barre horaire reste plafonnée à 60 min (4.16)
        assertEquals(60f, fallAgg.hourlyBuckets[1].totalMinutes, 0.01f)
    }
}
