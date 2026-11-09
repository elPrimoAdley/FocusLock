package com.example.focuslock.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
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

    @Volatile
    private var cachedAtMillis: Long = 0L

    @Volatile
    private var cachedUsageMinutes: Map<String, Int> = emptyMap()

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
        if (!hasUsagePermission()) return emptyMap()

        val nowMillis = clock.millis()
        if (!forceRefresh && (nowMillis - cachedAtMillis) in 0..cacheTtlMillis) {
            return cachedUsageMinutes
        }

        val zone = clock.zone
        val startOfDayMillis = LocalDate.now(clock).atStartOfDay(zone).toInstant().toEpochMilli()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val durationPerPackageMs = mutableMapOf<String, Long>()
        val lastResumeTimeMs = mutableMapOf<String, Long>()

        val events = usm.queryEvents(startOfDayMillis, nowMillis)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeTimeMs[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = lastResumeTimeMs.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        durationPerPackageMs[pkg] =
                            (durationPerPackageMs[pkg] ?: 0L) + (event.timeStamp - start)
                    }
                }
            }
        }

        // Si une application est encore ouverte à l'instant nowMillis, on compte jusqu'à nowMillis
        for ((pkg, start) in lastResumeTimeMs) {
            if (nowMillis > start) {
                durationPerPackageMs[pkg] = (durationPerPackageMs[pkg] ?: 0L) + (nowMillis - start)
            }
        }

        val minutesMap = durationPerPackageMs
            .mapValues { (_, ms) -> (ms / 60_000L).toInt() }
            .filterValues { it > 0 }

        cachedUsageMinutes = minutesMap
        cachedAtMillis = nowMillis
        return minutesMap
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