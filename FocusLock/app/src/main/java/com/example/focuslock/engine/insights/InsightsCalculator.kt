package com.example.focuslock.engine.insights

import com.example.focuslock.data.repository.RoomDailyUsageRepository
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.domain.repository.DailyAppUsageRecord
import com.example.focuslock.engine.usage.DayUsageAggregation
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

enum class InsightsPeriod(val label: String) {
    DAY("Jour"),
    WEEK("Semaine"),
    TREND("Tendance")
}

data class StackedBarSegment(
    val label: String,
    val distractingValue: Float,
    val neutralValue: Float,
    val productiveValue: Float,
    val isAvailable: Boolean = true
) {
    val totalValue: Float
        get() = distractingValue + neutralValue + productiveValue
}

data class TopUsedAppItem(
    val packageName: String,
    val totalMinutes: Int,
    val assignedCategory: AppCategory?, // null = "À classer" (compté comme NEUTRAL — Décision #21)
    val effectiveCategory: AppCategory
)

data class PeriodInsightsSummary(
    val period: InsightsPeriod,
    val periodTitle: String,
    val totalMinutes: Int,
    val formattedTotal: String, // ex: "1h 40m", "59m", "0m" (4.15)
    val trendDeltaText: String? = null,
    val bars: List<StackedBarSegment>,
    val maxBarScaleMinutes: Float,
    val topApps: List<TopUsedAppItem>,
    val balancePercentage: Int
)

object InsightsCalculator {

    private val shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM")

    /**
     * 4.15 : Formateur pur du temps d'écran géant.
     * - 0 min -> "0m"
     * - 59 min -> "59m"
     * - 60 min (1 h pile) -> "1h 00m"
     * - 100 min -> "1h 40m"
     */
    fun formatScreenTimeTotal(totalMinutes: Int): String {
        val safeMins = totalMinutes.coerceAtLeast(0)
        return if (safeMins < 60) {
            "${safeMins}m"
        } else {
            val h = safeMins / 60
            val m = safeMins % 60
            String.format("%dh %02dm", h, m)
        }
    }

    /**
     * 4.18 (Décision #19) : Calcule le pourcentage de Balance (% du temps d'éveil passé sur l'écran).
     * Pour aujourd'hui : temps écoulé depuis awakeStartMinutes (plancher 1 min).
     */
    fun computeBalancePercentage(
        screenTimeMinutes: Int,
        isToday: Boolean,
        now: Instant,
        zoneId: ZoneId,
        awakeStartMinutes: Int,
        awakeEndMinutes: Int,
        availableDaysCount: Int = 1
    ): Int {
        if (availableDaysCount <= 0) return 0
        val fullWindowMinutes = if (awakeEndMinutes > awakeStartMinutes) {
            awakeEndMinutes - awakeStartMinutes
        } else {
            (24 * 60 - awakeStartMinutes) + awakeEndMinutes
        }.coerceAtLeast(60)

        val denominatorMinutes = if (isToday) {
            val zonedNow = now.atZone(zoneId)
            val nowMinuteOfDay = zonedNow.hour * 60 + zonedNow.minute
            val elapsedAwake = (nowMinuteOfDay - awakeStartMinutes).coerceIn(1, fullWindowMinutes)
            elapsedAwake
        } else {
            (fullWindowMinutes * availableDaysCount).coerceAtLeast(1)
        }

        return ((screenTimeMinutes.coerceAtLeast(0) * 100.0) / denominatorMinutes.toDouble())
            .roundToInt()
            .coerceIn(0, 100)
    }

    /**
     * 4.14 : Construit l'état pour la vue JOUR (24 barres horaires empilées, échelle fixe 60 min).
     */
    fun buildDaySummary(
        selectedDate: LocalDate,
        todayDate: LocalDate,
        dayAggregation: DayUsageAggregation?,
        explicitCategoryByPackage: Map<String, AppCategory>,
        now: Instant,
        zoneId: ZoneId,
        awakeStartMinutes: Int,
        awakeEndMinutes: Int
    ): PeriodInsightsSummary {
        val title = when (selectedDate) {
            todayDate -> "AUJOURD'HUI"
            todayDate.minusDays(1) -> "HIER"
            else -> selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }

        if (dayAggregation == null) {
            val unavailableBars = (0..23).map { h ->
                StackedBarSegment(
                    label = if (h % 6 == 0) "${h}h" else "",
                    distractingValue = 0f,
                    neutralValue = 0f,
                    productiveValue = 0f,
                    isAvailable = false
                )
            }
            return PeriodInsightsSummary(
                period = InsightsPeriod.DAY,
                periodTitle = "$title (Indisponible)",
                totalMinutes = 0,
                formattedTotal = "Indisponible",
                bars = unavailableBars,
                maxBarScaleMinutes = 60f,
                topApps = emptyList(),
                balancePercentage = 0
            )
        }

        val bars = dayAggregation.hourlyBuckets.map { bucket ->
            StackedBarSegment(
                label = if (bucket.hourOfDay % 6 == 0) "${bucket.hourOfDay}h" else "",
                distractingValue = bucket.distractingMinutes,
                neutralValue = bucket.neutralMinutes,
                productiveValue = bucket.productiveMinutes,
                isAvailable = true
            )
        }

        val topApps = buildTopAppsList(dayAggregation.minutesByPackage, explicitCategoryByPackage)
        val balance = computeBalancePercentage(
            screenTimeMinutes = dayAggregation.totalScreenTimeMinutes,
            isToday = selectedDate == todayDate,
            now = now,
            zoneId = zoneId,
            awakeStartMinutes = awakeStartMinutes,
            awakeEndMinutes = awakeEndMinutes,
            availableDaysCount = 1
        )

        return PeriodInsightsSummary(
            period = InsightsPeriod.DAY,
            periodTitle = title,
            totalMinutes = dayAggregation.totalScreenTimeMinutes,
            formattedTotal = formatScreenTimeTotal(dayAggregation.totalScreenTimeMinutes),
            bars = bars,
            maxBarScaleMinutes = 60f, // 4.16 : Échelle verticale fixe de 60 min pour la vue Jour
            topApps = topApps,
            balancePercentage = balance
        )
    }

    /**
     * 4.14 : Construit l'état pour la vue SEMAINE (Lundi -> Dimanche, 7 barres).
     * Les jours sans données dans DailyUsageEntity (hors aujourd'hui) sont marqués "indisponibles".
     */
    fun buildWeekSummary(
        anchorDate: LocalDate,
        todayDate: LocalDate,
        historyRecords: List<DailyAppUsageRecord>,
        todayMinutesByPackage: Map<String, Int>,
        explicitCategoryByPackage: Map<String, AppCategory>,
        now: Instant,
        zoneId: ZoneId,
        awakeStartMinutes: Int,
        awakeEndMinutes: Int
    ): PeriodInsightsSummary {
        val monday = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val dayLabels = listOf("L", "M", "M", "J", "V", "S", "D")

        val recordsByDay = historyRecords.groupBy { it.epochDay }
        val aggregatedMinutesByPkg = mutableMapOf<String, Int>()
        var availableDaysCount = 0

        val bars = (0..6).map { offset ->
            val dayDate = monday.plusDays(offset.toLong())
            val epochDay = dayDate.toEpochDay()
            val label = dayLabels[offset]

            when {
                dayDate.isAfter(todayDate) -> {
                    StackedBarSegment(label, 0f, 0f, 0f, isAvailable = false)
                }
                dayDate == todayDate -> {
                    availableDaysCount++
                    todayMinutesByPackage.forEach { (pkg, mins) ->
                        aggregatedMinutesByPkg[pkg] = (aggregatedMinutesByPkg[pkg] ?: 0) + mins
                    }
                    val (d, n, p) = splitMinutesByCategory(todayMinutesByPackage, explicitCategoryByPackage)
                    StackedBarSegment(label, d.toFloat(), n.toFloat(), p.toFloat(), isAvailable = true)
                }
                else -> {
                    val dayRecords = recordsByDay[epochDay]
                    if (dayRecords == null) {
                        // Jour manquant dans l'historique -> Indisponible (Décision #18)
                        StackedBarSegment(label, 0f, 0f, 0f, isAvailable = false)
                    } else {
                        availableDaysCount++
                        val dayPkgMins = dayRecords
                            .filter { it.packageName != RoomDailyUsageRepository.SENTINEL_ZERO_USAGE_PACKAGE }
                            .associate { it.packageName to (it.totalMs / 60_000L).toInt() }
                            .filterValues { it > 0 }

                        dayPkgMins.forEach { (pkg, mins) ->
                            aggregatedMinutesByPkg[pkg] = (aggregatedMinutesByPkg[pkg] ?: 0) + mins
                        }
                        val (d, n, p) = splitMinutesByCategory(dayPkgMins, explicitCategoryByPackage)
                        StackedBarSegment(label, d.toFloat(), n.toFloat(), p.toFloat(), isAvailable = true)
                    }
                }
            }
        }

        val totalWeekMinutes = aggregatedMinutesByPkg.values.sum()
        val maxBar = (bars.maxOfOrNull { it.totalValue } ?: 60f).coerceAtLeast(60f)
        val topApps = buildTopAppsList(aggregatedMinutesByPkg, explicitCategoryByPackage)
        val balance = computeBalancePercentage(
            screenTimeMinutes = totalWeekMinutes,
            isToday = false,
            now = now,
            zoneId = zoneId,
            awakeStartMinutes = awakeStartMinutes,
            awakeEndMinutes = awakeEndMinutes,
            availableDaysCount = availableDaysCount
        )

        val title = "SEMAINE DU ${monday.format(shortDateFormatter)} AU ${sunday.format(shortDateFormatter)}"
        return PeriodInsightsSummary(
            period = InsightsPeriod.WEEK,
            periodTitle = title,
            totalMinutes = totalWeekMinutes,
            formattedTotal = if (availableDaysCount == 0) "Indisponible" else formatScreenTimeTotal(totalWeekMinutes),
            bars = bars,
            maxBarScaleMinutes = maxBar,
            topApps = topApps,
            balancePercentage = balance
        )
    }

    /**
     * 4.14 : Construit l'état pour la vue TENDANCE (8 dernières semaines : moyenne quotidienne + écart vs S-1).
     */
    fun buildTrendSummary(
        anchorDate: LocalDate,
        todayDate: LocalDate,
        historyRecords: List<DailyAppUsageRecord>,
        todayMinutesByPackage: Map<String, Int>,
        explicitCategoryByPackage: Map<String, AppCategory>,
        now: Instant,
        zoneId: ZoneId,
        awakeStartMinutes: Int,
        awakeEndMinutes: Int
    ): PeriodInsightsSummary {
        val currentWeekMonday = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val recordsByDay = historyRecords.groupBy { it.epochDay }
        val totalMinutesByPkg = mutableMapOf<String, Int>()
        var totalAvailableDaysAcross8Weeks = 0

        val weekDailyAverages = mutableListOf<Int?>()

        val bars = (7 downTo 0).map { weeksAgo ->
            val weekStart = currentWeekMonday.minusWeeks(weeksAgo.toLong())
            var weekDistracting = 0f
            var weekNeutral = 0f
            var weekProductive = 0f
            var weekAvailableDays = 0

            for (offset in 0..6) {
                val dayDate = weekStart.plusDays(offset.toLong())
                if (dayDate.isAfter(todayDate)) continue

                val dayPkgMins: Map<String, Int>? = if (dayDate == todayDate) {
                    todayMinutesByPackage
                } else {
                    recordsByDay[dayDate.toEpochDay()]?.let { list ->
                        list.filter { it.packageName != RoomDailyUsageRepository.SENTINEL_ZERO_USAGE_PACKAGE }
                            .associate { it.packageName to (it.totalMs / 60_000L).toInt() }
                            .filterValues { it > 0 }
                    }
                }

                if (dayPkgMins != null) {
                    weekAvailableDays++
                    totalAvailableDaysAcross8Weeks++
                    dayPkgMins.forEach { (pkg, mins) ->
                        totalMinutesByPkg[pkg] = (totalMinutesByPkg[pkg] ?: 0) + mins
                    }
                    val (d, n, p) = splitMinutesByCategory(dayPkgMins, explicitCategoryByPackage)
                    weekDistracting += d
                    weekNeutral += n
                    weekProductive += p
                }
            }

            val label = if (weeksAgo == 0) "S0" else "S-$weeksAgo"
            if (weekAvailableDays == 0) {
                weekDailyAverages.add(null)
                StackedBarSegment(label, 0f, 0f, 0f, isAvailable = false)
            } else {
                val avgD = weekDistracting / weekAvailableDays
                val avgN = weekNeutral / weekAvailableDays
                val avgP = weekProductive / weekAvailableDays
                weekDailyAverages.add((avgD + avgN + avgP).roundToInt())
                StackedBarSegment(label, avgD, avgN, avgP, isAvailable = true)
            }
        }

        val currentWeekAvg = weekDailyAverages.lastOrNull() ?: 0
        val previousWeekAvg = weekDailyAverages.getOrNull(weekDailyAverages.size - 2)
        val deltaText = if (previousWeekAvg != null && weekDailyAverages.lastOrNull() != null) {
            val diff = currentWeekAvg - previousWeekAvg
            val sign = if (diff > 0) "+" else ""
            "$sign${diff}m / jour vs semaine précédente"
        } else {
            "Moyenne quotidienne sur la semaine courante"
        }

        val maxBar = (bars.maxOfOrNull { it.totalValue } ?: 60f).coerceAtLeast(60f)
        val topApps = buildTopAppsList(totalMinutesByPkg, explicitCategoryByPackage)
        val balance = computeBalancePercentage(
            screenTimeMinutes = totalMinutesByPkg.values.sum(),
            isToday = false,
            now = now,
            zoneId = zoneId,
            awakeStartMinutes = awakeStartMinutes,
            awakeEndMinutes = awakeEndMinutes,
            availableDaysCount = totalAvailableDaysAcross8Weeks
        )

        return PeriodInsightsSummary(
            period = InsightsPeriod.TREND,
            periodTitle = "TENDANCE 8 SEMAINES (MOY. / JOUR)",
            totalMinutes = currentWeekAvg,
            formattedTotal = formatScreenTimeTotal(currentWeekAvg),
            trendDeltaText = deltaText,
            bars = bars,
            maxBarScaleMinutes = maxBar,
            topApps = topApps,
            balancePercentage = balance
        )
    }

    private fun splitMinutesByCategory(
        minutesByPackage: Map<String, Int>,
        explicitCategoryByPackage: Map<String, AppCategory>
    ): Triple<Int, Int, Int> {
        var distracting = 0
        var neutral = 0
        var productive = 0
        for ((pkg, mins) in minutesByPackage) {
            when (explicitCategoryByPackage[pkg] ?: AppCategory.NEUTRAL) {
                AppCategory.DISTRACTING -> distracting += mins
                AppCategory.NEUTRAL -> neutral += mins
                AppCategory.PRODUCTIVE -> productive += mins
            }
        }
        return Triple(distracting, neutral, productive)
    }

    private fun buildTopAppsList(
        minutesByPackage: Map<String, Int>,
        explicitCategoryByPackage: Map<String, AppCategory>
    ): List<TopUsedAppItem> {
        return minutesByPackage.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(10) // 4.17 : Top 10 de la période
            .map { (pkg, mins) ->
                val explicit = explicitCategoryByPackage[pkg]
                TopUsedAppItem(
                    packageName = pkg,
                    totalMinutes = mins,
                    assignedCategory = explicit, // null = affiché "À classer"
                    effectiveCategory = explicit ?: AppCategory.NEUTRAL
                )
            }
    }
}
