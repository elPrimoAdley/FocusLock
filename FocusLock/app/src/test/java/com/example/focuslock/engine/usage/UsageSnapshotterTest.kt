package com.example.focuslock.engine.usage

import com.example.focuslock.domain.repository.DailyAppUsageRecord
import com.example.focuslock.domain.repository.DailyUsageRepository
import com.example.focuslock.domain.store.GamificationState
import com.example.focuslock.domain.store.GamificationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageSnapshotterTest {

    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun `4_13 first launch without history backfills available past days up to yesterday and never snapshots today`() = runTest {
        val today = LocalDate.of(2026, 11, 10)
        val now = LocalDateTime.of(2026, 11, 10, 14, 0).atZone(zone).toInstant()

        val fakeStore = InMemoryGamificationStore(GamificationState(lastUsageSnapshotEpochDay = null))
        val fakeRepo = InMemoryDailyUsageRepository()

        // Simule qu'Android possède seulement les événements des 3 derniers jours passés (7, 8, 9 nov)
        val availableDays = setOf(
            LocalDate.of(2026, 11, 7),
            LocalDate.of(2026, 11, 8),
            LocalDate.of(2026, 11, 9)
        )
        val snapshotter = UsageSnapshotter(
            zoneId = zone,
            gamificationStore = fakeStore,
            dailyUsageRepository = fakeRepo,
            pastDayUsageProvider = { date ->
                if (date in availableDays) mapOf("com.instagram.android" to 600_000L) else null
            }
        )

        val count1 = snapshotter.catchUpUntilYesterday(now)
        assertEquals(3, count1)
        assertEquals(today.minusDays(1).toEpochDay(), fakeStore.getSnapshot().lastUsageSnapshotEpochDay)
        // Aujourd'hui (10 nov) n'est jamais enregistré dans DailyUsageEntity
        assertFalse(fakeRepo.savedDays.containsKey(today.toEpochDay()))

        // Relance le même jour -> idempotent, 0 jour recalculé
        val count2 = snapshotter.catchUpUntilYesterday(now.plusSeconds(300))
        assertEquals(0, count2)
    }

    @Test
    fun `4_13 multi-day gap catches up exact missing days in sequence`() = runTest {
        val lastRecorded = LocalDate.of(2026, 11, 5).toEpochDay()
        val nowOnNov9 = LocalDateTime.of(2026, 11, 9, 10, 0).atZone(zone).toInstant()

        val fakeStore = InMemoryGamificationStore(GamificationState(lastUsageSnapshotEpochDay = lastRecorded))
        val fakeRepo = InMemoryDailyUsageRepository()
        val snapshotter = UsageSnapshotter(
            zoneId = zone,
            gamificationStore = fakeStore,
            dailyUsageRepository = fakeRepo,
            pastDayUsageProvider = { mapOf("com.youtube" to 1_200_000L) }
        )

        // Doit rattraper les 6, 7 et 8 novembre (3 jours)
        val recorded = snapshotter.catchUpUntilYesterday(nowOnNov9)
        assertEquals(3, recorded)
        assertEquals(
            setOf(
                LocalDate.of(2026, 11, 6).toEpochDay(),
                LocalDate.of(2026, 11, 7).toEpochDay(),
                LocalDate.of(2026, 11, 8).toEpochDay()
            ),
            fakeRepo.savedDays.keys
        )
    }

    private class InMemoryGamificationStore(initial: GamificationState) : GamificationStore {
        private val flow = MutableStateFlow(initial)
        override val state: Flow<GamificationState> = flow
        override suspend fun getSnapshot(): GamificationState = flow.value
        override suspend fun update(transform: (GamificationState) -> GamificationState): GamificationState {
            val next = transform(flow.value)
            flow.value = next
            return next
        }
    }

    private class InMemoryDailyUsageRepository : DailyUsageRepository {
        val savedDays = mutableMapOf<Long, Map<String, Long>>()
        override suspend fun saveDayUsage(epochDay: Long, usageByPackageMs: Map<String, Long>) {
            savedDays[epochDay] = usageByPackageMs
        }
        override suspend fun getUsageBetweenDays(startEpochDay: Long, endEpochDay: Long): List<DailyAppUsageRecord> = emptyList()
        override fun observeUsageBetweenDays(startEpochDay: Long, endEpochDay: Long): Flow<List<DailyAppUsageRecord>> =
            MutableStateFlow(emptyList())
    }
}
