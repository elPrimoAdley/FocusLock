package com.example.focuslock.engine.usage

import com.example.focuslock.domain.repository.DailyUsageRepository
import com.example.focuslock.domain.store.GamificationStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun interface PastDayUsageProvider {
    /**
     * Retourne Map<packageName, durationMs> pour un jour passé [date],
     * ou null si le système Android ne dispose plus des événements pour ce jour.
     */
    suspend fun computeDayUsageMs(date: LocalDate): Map<String, Long>?
}

class UsageSnapshotter(
    private val zoneId: ZoneId,
    private val gamificationStore: GamificationStore,
    private val dailyUsageRepository: DailyUsageRepository,
    private val pastDayUsageProvider: PastDayUsageProvider
) {
    companion object {
        // Au premier lancement sans historique, tente de récupérer jusqu'aux 7 derniers jours révolus
        const val INITIAL_BACKFILL_DAYS: Long = 7L
    }

    private val mutex = Mutex()

    /**
     * 4.13 : Rattrapage idempotent de lastUsageSnapshotEpochDay + 1 jusqu'à hier (todayEpochDay - 1).
     * Retourne le nombre de journées persistées lors de cet appel.
     */
    suspend fun catchUpUntilYesterday(now: Instant): Int = mutex.withLock {
        val today = now.atZone(zoneId).toLocalDate()
        val yesterdayEpochDay = today.toEpochDay() - 1L

        val storeSnapshot = gamificationStore.getSnapshot()
        val lastSnap = storeSnapshot.lastUsageSnapshotEpochDay

        val startEpochDay = if (lastSnap == null) {
            yesterdayEpochDay - INITIAL_BACKFILL_DAYS + 1L
        } else {
            lastSnap + 1L
        }

        if (startEpochDay > yesterdayEpochDay) {
            return@withLock 0
        }

        var recordedDays = 0
        for (epochDay in startEpochDay..yesterdayEpochDay) {
            val date = LocalDate.ofEpochDay(epochDay)
            val usageMap = pastDayUsageProvider.computeDayUsageMs(date)
            if (usageMap != null) {
                dailyUsageRepository.saveDayUsage(epochDay, usageMap)
                recordedDays++
            }
        }

        gamificationStore.update { state ->
            state.copy(lastUsageSnapshotEpochDay = yesterdayEpochDay)
        }

        recordedDays
    }
}
