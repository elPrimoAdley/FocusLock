package com.example.focuslock.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.data.local.entity.AppMetadataEntity
import com.example.focuslock.data.local.entity.BlockAuditLogEntity
import com.example.focuslock.data.local.entity.FocusSessionEntity
import com.example.focuslock.data.local.entity.QuestTaskEntity
import com.example.focuslock.data.local.entity.RuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusDatabaseDaoTest {

    private lateinit var database: FocusDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ruleDao_insertObserveAndToggleEnabled() = runBlocking {
        val dao = database.ruleDao()
        val rule = RuleEntity(
            id = "r1",
            title = "Détox Instagram 1j/2",
            ruleType = "ALTERNATING_DETOX",
            targetPackagesJson = """["com.instagram.android"]""",
            startTimeMinutes = null,
            endTimeMinutes = null,
            activeDaysBitmask = 127,
            dailyQuotaMinutes = null,
            cycleIntervalDays = 2,
            cycleStartEpochDay = 20760L,
            pauseUntilTimestamp = null,
            isStrictLocked = true,
            isEnabled = true,
            customBlockMessage = "Jour OFF"
        )

        dao.upsertRule(rule)
        assertEquals(1, dao.observeEnabledRules().first().size)

        dao.setRuleEnabled("r1", false)
        assertEquals(0, dao.observeEnabledRules().first().size)
        assertEquals(1, dao.observeAllRules().first().size)
    }

    @Test
    fun questTaskDao_routineChecklistAndCompletion() = runBlocking {
        val dao = database.questTaskDao()
        val item1 = QuestTaskEntity(
            id = "t1",
            title = "Boire 500ml d'eau",
            description = "Hydratation au réveil",
            priority = "P1_CRITICAL",
            xpValue = 100,
            dueAtTimestamp = null,
            completedAtTimestamp = null,
            isRoutineChecklistItem = true,
            parentRoutineId = "routine-matin",
            penaltyApplied = false
        )
        dao.upsertTask(item1)

        val checklist = dao.observeChecklistForRoutine("routine-matin").first()
        assertEquals(1, checklist.size)
        assertEquals(null, checklist.first().completedAtTimestamp)

        dao.updateTaskCompletion("t1", 1762603500000L)
        val updated = dao.observeChecklistForRoutine("routine-matin").first().first()
        assertEquals(1762603500000L, updated.completedAtTimestamp)
    }

    @Test
    fun focusSessionDao_runningSessionsFilterAndStatusUpdate() = runBlocking {
        val dao = database.focusSessionDao()
        val session = FocusSessionEntity(
            id = "s1",
            sessionType = "ACTIVE_LEARNING",
            label = "Espagnol — Tablette",
            startedAt = 1000L,
            plannedEndAt = 4600L,
            actualEndedAt = null,
            whitelistedPackagesJson = """["com.spotify.music"]""",
            summaryValidationNote = null,
            status = "RUNNING"
        )
        dao.upsertSession(session)
        assertEquals(1, dao.getRunningSessions().size)

        dao.updateSessionStatus("s1", "COMPLETED", 4600L, "Vocabulaire appris")
        assertEquals(0, dao.getRunningSessions().size)
        val completed = dao.observeAllSessions().first().first()
        assertEquals("COMPLETED", completed.status)
        assertEquals("Vocabulaire appris", completed.summaryValidationNote)
    }

    @Test
    fun blockAuditLogDao_computesTodayAndTotalCountsPerPackage() = runBlocking {
        val dao = database.blockAuditLogDao()
        val instagram = "com.instagram.android"
        val tiktok = "com.zhiliaoapp.musically"
        val startOfToday = 1_762_600_000_000L

        // 1 tentative hier sur Instagram
        dao.insertLog(
            BlockAuditLogEntity(
                timestamp = startOfToday - 3_600_000L,
                blockedPackageName = instagram,
                triggerRuleId = "r1",
                reasonCode = "SCHEDULE_OR_QUOTA",
                explanationText = "Quota atteint hier"
            )
        )
        // 2 tentatives aujourd'hui sur Instagram
        dao.insertLog(
            BlockAuditLogEntity(
                timestamp = startOfToday + 60_000L,
                blockedPackageName = instagram,
                triggerRuleId = "r1",
                reasonCode = "ACTIVE_FOCUS_SESSION",
                explanationText = "Tentative 1 aujourd'hui"
            )
        )
        dao.insertLog(
            BlockAuditLogEntity(
                timestamp = startOfToday + 120_000L,
                blockedPackageName = instagram,
                triggerRuleId = "r1",
                reasonCode = "ACTIVE_FOCUS_SESSION",
                explanationText = "Tentative 2 aujourd'hui"
            )
        )
        // 1 tentative aujourd'hui sur TikTok
        dao.insertLog(
            BlockAuditLogEntity(
                timestamp = startOfToday + 180_000L,
                blockedPackageName = tiktok,
                triggerRuleId = "r2",
                reasonCode = "SABBATICAL_OR_DETOX",
                explanationText = "Jour OFF TikTok"
            )
        )

        // Vérification "2x aujourd'hui | 3x au total" pour Instagram
        assertEquals(2, dao.getTodayCountForPackage(instagram, startOfToday))
        assertEquals(3, dao.getTotalCountForPackage(instagram))
        assertEquals(2, dao.observeTodayCountForPackage(instagram, startOfToday).first())
        assertEquals(3, dao.observeTotalCountForPackage(instagram).first())

        // Vérification "Distractions évitées aujourd'hui" (2 Instagram + 1 TikTok = 3)
        assertEquals(3, dao.observeTotalDistractionsPreventedSince(startOfToday).first())
    }

    @Test
    fun appMetadataDao_upsertAndQueryCategory() = runBlocking {
        val dao = database.appMetadataDao()
        val meta = AppMetadataEntity(
            packageName = "com.instagram.android",
            category = "DISTRACTING",
            isAlwaysWhitelisted = false
        )
        dao.upsertMetadata(meta)

        val loaded = dao.getMetadataForPackage("com.instagram.android")
        assertNotNull(loaded)
        assertEquals("DISTRACTING", loaded?.category)
    }
}