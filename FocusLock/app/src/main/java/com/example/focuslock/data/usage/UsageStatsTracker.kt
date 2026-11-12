package com.example.focuslock.data.usage

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.engine.usage.DayUsageAggregation
import com.example.focuslock.engine.usage.UsageAggregator
import com.example.focuslock.engine.usage.UsageIntervalBuilder
import java.time.Clock
import java.time.LocalDate

class UsageStatsTracker(
    private val context: Context,
    private val clock: Clock,
    private val cacheTtlMillis: Long = 5_000L
) {
    companion object {
        private const val TAG = "UsageStatsTracker"
    }

    private val reader = UsageEventsReader(context)

    @Volatile
    private var cachedAtMillis: Long = 0L

    @Volatile
    private var cachedTodayAggregation: DayUsageAggregation? = null

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Retourne Map<packageName, minutes> pour la journée locale courante (depuis 00:00).
     * Utilise un cache de quelques secondes (2.18) sauf si forceRefresh = true.
     */
    fun getTodayUsageMinutes(forceRefresh: Boolean = false): Map<String, Int> {
        val today = LocalDate.now(clock)
        val agg = getDayAggregation(today, emptyMap(), forceRefresh)
        return agg?.minutesByPackage ?: emptyMap()
    }

    /**
     * 4.12 : Récupère l'agrégation complète d'une journée locale [date] via [UsageEventsReader],
     * [UsageIntervalBuilder] et [UsageAggregator].
     */
    fun getDayAggregation(
        date: LocalDate,
        categoryByPackage: Map<String, AppCategory> = emptyMap(),
        forceRefresh: Boolean = false
    ): DayUsageAggregation? {
        if (!hasUsagePermission()) return null

        val now = clock.instant()
        val nowMillis = now.toEpochMilli()
        val zone = clock.zone
        val today = now.atZone(zone).toLocalDate()

        // Cache court de 5 secondes pour aujourd'hui
        if (date == today && !forceRefresh && (nowMillis - cachedAtMillis) in 0..cacheTtlMillis) {
            val cached = cachedTodayAggregation
            if (cached != null) return cached
        }

        val dayStart = date.atStartOfDay(zone).toInstant()
        val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant()
        val queryEnd = if (now.isBefore(nextDayStart)) now else nextDayStart

        if (queryEnd.isBefore(dayStart)) return null

        val rawEvents = reader.readEvents(dayStart, queryEnd)
        val ignoredPackages = reader.getExcludedScreenTimePackages()
        val intervals = UsageIntervalBuilder.buildIntervals(rawEvents, queryEnd, ignoredPackages)

        val aggregation = UsageAggregator.aggregateForDay(
            intervals = intervals,
            date = date,
            zoneId = zone,
            categoryByPackage = categoryByPackage,
            maxInstantCap = now
        )

        if (date == today) {
            cachedTodayAggregation = aggregation
            cachedAtMillis = nowMillis
        }

        return aggregation
    }

    /**
     * 2.17 : Affiche les compteurs du jour dans Logcat pour comparer avec Bien-être numérique.
     */
    fun logTodayUsageToLogcat() {
        val stats = getTodayUsageMinutes(forceRefresh = true)
        if (stats.isEmpty()) {
            Log.i(TAG, "UsageStats du jour vide (ou permission non accordée).")
            return
        }
        Log.i(TAG, "=== USAGE STATS DU JOUR LOCAL (${stats.size} apps) ===")
        stats.entries
            .sortedByDescending { it.value }
            .forEach { (pkg, mins) ->
                Log.i(TAG, "App: $pkg -> $mins min (${mins / 60}h ${mins % 60}m)")
            }
    }
}
