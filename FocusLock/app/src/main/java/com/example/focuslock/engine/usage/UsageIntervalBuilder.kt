package com.example.focuslock.engine.usage

import java.time.Instant

enum class RawEventType {
    ACTIVITY_RESUMED,
    ACTIVITY_PAUSED,
    ACTIVITY_STOPPED,
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    DEVICE_SHUTDOWN
}

data class RawUsageEvent(
    val timestamp: Instant,
    val packageName: String? = null,
    val activityClass: String? = null,
    val type: RawEventType
)

data class UsageInterval(
    val packageName: String,
    val start: Instant,
    val end: Instant
) {
    val durationMillis: Long
        get() = (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(0L)
}

object UsageIntervalBuilder {

    /**
     * 4.10 : Construit la liste des intervalles d'utilisation par application à partir d'événements bruts.
     * Zéro dépendance Android.
     */
    fun buildIntervals(
        events: List<RawUsageEvent>,
        now: Instant,
        ignoredPackages: Set<String> = emptySet()
    ): List<UsageInterval> {
        if (events.isEmpty()) return emptyList()

        val sortedEvents = events.sortedBy { it.timestamp }
        val activeActivitiesByPkg = mutableMapOf<String, MutableSet<String>>()
        val openStartByPkg = mutableMapOf<String, Instant>()
        val rawIntervalsByPkg = mutableMapOf<String, MutableList<UsageInterval>>()
        var isScreenInteractive = true

        fun closeAllOpenPackagesAt(closeTimestamp: Instant) {
            for ((pkg, startInstant) in openStartByPkg) {
                val safeEnd = if (closeTimestamp.isBefore(now)) closeTimestamp else now
                if (safeEnd.isAfter(startInstant)) {
                    rawIntervalsByPkg.getOrPut(pkg) { mutableListOf() }
                        .add(UsageInterval(pkg, startInstant, safeEnd))
                }
            }
            openStartByPkg.clear()
            activeActivitiesByPkg.clear()
        }

        for (event in sortedEvents) {
            if (event.timestamp.isAfter(now)) break

            when (event.type) {
                RawEventType.SCREEN_NON_INTERACTIVE,
                RawEventType.DEVICE_SHUTDOWN -> {
                    isScreenInteractive = false
                    closeAllOpenPackagesAt(event.timestamp)
                }

                RawEventType.SCREEN_INTERACTIVE -> {
                    isScreenInteractive = true
                }

                RawEventType.ACTIVITY_RESUMED -> {
                    val pkg = event.packageName ?: continue
                    if (pkg in ignoredPackages) continue

                    // Si un événement ACTIVITY_RESUMED survient sans événement SCREEN_INTERACTIVE préalable,
                    // l'écran est nécessairement actif.
                    isScreenInteractive = true

                    val activityId = event.activityClass ?: DEFAULT_ACTIVITY_ID
                    val activeSet = activeActivitiesByPkg.getOrPut(pkg) { mutableSetOf() }
                    val wasEmpty = activeSet.isEmpty()
                    activeSet.add(activityId)

                    if (wasEmpty && !openStartByPkg.containsKey(pkg)) {
                        openStartByPkg[pkg] = event.timestamp
                    }
                }

                RawEventType.ACTIVITY_PAUSED,
                RawEventType.ACTIVITY_STOPPED -> {
                    val pkg = event.packageName ?: continue
                    if (pkg in ignoredPackages) continue

                    val activityId = event.activityClass ?: DEFAULT_ACTIVITY_ID
                    val activeSet = activeActivitiesByPkg[pkg]
                    if (activeSet != null) {
                        activeSet.remove(activityId)
                        if (activeSet.isEmpty()) {
                            activeActivitiesByPkg.remove(pkg)
                            val startInstant = openStartByPkg.remove(pkg)
                            if (startInstant != null && event.timestamp.isAfter(startInstant)) {
                                rawIntervalsByPkg.getOrPut(pkg) { mutableListOf() }
                                    .add(UsageInterval(pkg, startInstant, event.timestamp))
                            }
                        }
                    }
                }
            }
        }

        // Ferme à [now] tout intervalle encore ouvert à la fin du flux si l'écran est allumé
        if (isScreenInteractive) {
            closeAllOpenPackagesAt(now)
        }

        // Fusionne les intervalles contigus ou qui se chevauchent au sein d'un même package
        return rawIntervalsByPkg.values
            .flatMap { pkgIntervals -> mergeSamePackageIntervals(pkgIntervals) }
            .sortedWith(compareBy<UsageInterval> { it.start }.thenBy { it.packageName })
    }

    private fun mergeSamePackageIntervals(intervals: List<UsageInterval>): List<UsageInterval> {
        if (intervals.size <= 1) return intervals
        val sorted = intervals.sortedBy { it.start }
        val merged = mutableListOf<UsageInterval>()
        var current = sorted.first()

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            // Si next commence avant ou pile à la fin de current, on fusionne sans trou ni double compte
            if (!next.start.isAfter(current.end)) {
                val maxEnd = if (next.end.isAfter(current.end)) next.end else current.end
                current = current.copy(end = maxEnd)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    private const val DEFAULT_ACTIVITY_ID = "__default_activity__"
}
