package com.example.focuslock.engine.usage

import com.example.focuslock.domain.AppCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HourlyCategoryBucket(
    val hourOfDay: Int, // 0..23
    val distractingMinutes: Float = 0f,
    val neutralMinutes: Float = 0f,
    val productiveMinutes: Float = 0f
) {
    val totalMinutes: Float
        get() = (distractingMinutes + neutralMinutes + productiveMinutes).coerceAtMost(60f)
}

data class DayUsageAggregation(
    val date: LocalDate,
    val totalScreenTimeMs: Long,
    val totalScreenTimeMinutes: Int,
    val durationMsByPackage: Map<String, Long>,
    val minutesByPackage: Map<String, Int>,
    val hourlyBuckets: List<HourlyCategoryBucket> // Toujours 24 entrées (0..23)
)

object UsageAggregator {

    /**
     * Découpe une liste d'intervalles pour ne conserver que la portion incluse dans [windowStart, windowEnd).
     */
    fun clipIntervals(
        intervals: List<UsageInterval>,
        windowStart: Instant,
        windowEnd: Instant
    ): List<UsageInterval> {
        if (!windowEnd.isAfter(windowStart)) return emptyList()
        return intervals.mapNotNull { interval ->
            val clampedStart = if (interval.start.isBefore(windowStart)) windowStart else interval.start
            val clampedEnd = if (interval.end.isAfter(windowEnd)) windowEnd else interval.end
            if (clampedEnd.isAfter(clampedStart)) {
                interval.copy(start = clampedStart, end = clampedEnd)
            } else {
                null
            }
        }
    }

    /**
     * Calcule la durée totale (en ms) de l'union d'une liste d'intervalles (indépendamment du package).
     * Deux fenêtres ouvertes simultanément en pop-up ou écran scindé ne comptent qu'une seule fois.
     */
    fun unionDurationMillis(intervals: List<UsageInterval>): Long {
        if (intervals.isEmpty()) return 0L
        val sorted = intervals.sortedBy { it.start }
        var totalMs = 0L
        var curStart = sorted.first().start.toEpochMilli()
        var curEnd = sorted.first().end.toEpochMilli()

        for (i in 1 until sorted.size) {
            val nextStart = sorted[i].start.toEpochMilli()
            val nextEnd = sorted[i].end.toEpochMilli()
            if (nextStart <= curEnd) {
                if (nextEnd > curEnd) curEnd = nextEnd
            } else {
                totalMs += (curEnd - curStart).coerceAtLeast(0L)
                curStart = nextStart
                curEnd = nextEnd
            }
        }
        totalMs += (curEnd - curStart).coerceAtLeast(0L)
        return totalMs
    }

    /**
     * 4.11 : Agrège les intervalles sur une journée locale [date] dans le fuseau [zoneId].
     * Supporte les journées de 23 h, 24 h et 25 h (changement d'heure DST).
     */
    fun aggregateForDay(
        intervals: List<UsageInterval>,
        date: LocalDate,
        zoneId: ZoneId,
        categoryByPackage: Map<String, AppCategory> = emptyMap(),
        maxInstantCap: Instant? = null
    ): DayUsageAggregation {
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val nextDayStart = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val effectiveDayEnd = if (maxInstantCap != null && maxInstantCap.isBefore(nextDayStart)) {
            if (maxInstantCap.isAfter(dayStart)) maxInstantCap else dayStart
        } else {
            nextDayStart
        }

        val dayIntervals = clipIntervals(intervals, dayStart, effectiveDayEnd)

        // 1. Durée par package (somme des intervalles déjà fusionnés par package)
        val durationMsByPkg = mutableMapOf<String, Long>()
        for (interval in dayIntervals) {
            durationMsByPkg[interval.packageName] =
                (durationMsByPkg[interval.packageName] ?: 0L) + interval.durationMillis
        }

        val minutesByPkg = durationMsByPkg
            .mapValues { (_, ms) -> (ms / 60_000L).toInt() }
            .filterValues { it > 0 }

        // 2. Temps d'écran total de la journée par union stricte des intervalles
        val elapsedCapMs = (effectiveDayEnd.toEpochMilli() - dayStart.toEpochMilli()).coerceAtLeast(0L)
        val totalUnionMs = unionDurationMillis(dayIntervals).coerceAtMost(elapsedCapMs)
        val totalUnionMinutes = (totalUnionMs / 60_000L).toInt()

        // 3. Découpage dans les 24 tranches horaires (0h à 23h locales)
        // Pour supporter les jours de 23h (printemps) et 25h (automne), on parcourt les tranches de 1h
        // entre dayStart et nextDayStart et on rattache chaque tranche à son heure locale (0..23).
        val rawMsByHourAndCat = Array(24) { LongArray(3) }
        val unionMsByHour = LongArray(24)

        var cursor = dayStart
        while (cursor.isBefore(effectiveDayEnd)) {
            val sliceEnd = cursor.plusSeconds(3600L).let {
                if (it.isAfter(effectiveDayEnd)) effectiveDayEnd else it
            }
            val localHour = cursor.atZone(zoneId).hour.coerceIn(0, 23)
            val sliceIntervals = clipIntervals(dayIntervals, cursor, sliceEnd)

            if (sliceIntervals.isNotEmpty()) {
                val sliceUnionMs = unionDurationMillis(sliceIntervals)
                unionMsByHour[localHour] = (unionMsByHour[localHour] + sliceUnionMs).coerceAtMost(3_600_000L)

                val byCat = sliceIntervals.groupBy {
                    categoryByPackage[it.packageName] ?: AppCategory.NEUTRAL // Décision #21 : défaut NEUTRAL
                }
                rawMsByHourAndCat[localHour][0] += unionDurationMillis(byCat[AppCategory.DISTRACTING].orEmpty())
                rawMsByHourAndCat[localHour][1] += unionDurationMillis(byCat[AppCategory.NEUTRAL].orEmpty())
                rawMsByHourAndCat[localHour][2] += unionDurationMillis(byCat[AppCategory.PRODUCTIVE].orEmpty())
            }

            cursor = sliceEnd
        }

        val buckets = (0..23).map { hour ->
            val dMs = rawMsByHourAndCat[hour][0]
            val nMs = rawMsByHourAndCat[hour][1]
            val pMs = rawMsByHourAndCat[hour][2]
            val sumCatMs = dMs + nMs + pMs
            val capHourMs = unionMsByHour[hour].coerceAtMost(3_600_000L)

            if (sumCatMs <= 0L || capHourMs <= 0L) {
                HourlyCategoryBucket(hourOfDay = hour)
            } else {
                // Normalise proportionnellement si deux catégories étaient en écran scindé simultané
                val scale = if (sumCatMs > capHourMs) capHourMs.toFloat() / sumCatMs.toFloat() else 1f
                HourlyCategoryBucket(
                    hourOfDay = hour,
                    distractingMinutes = (dMs * scale) / 60_000f,
                    neutralMinutes = (nMs * scale) / 60_000f,
                    productiveMinutes = (pMs * scale) / 60_000f
                )
            }
        }

        return DayUsageAggregation(
            date = date,
            totalScreenTimeMs = totalUnionMs,
            totalScreenTimeMinutes = totalUnionMinutes,
            durationMsByPackage = durationMsByPkg,
            minutesByPackage = minutesByPkg,
            hourlyBuckets = buckets
        )
    }
}
