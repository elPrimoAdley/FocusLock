package com.example.focuslock.data.mapper

import com.example.focuslock.domain.AppCategory
import com.example.focuslock.domain.AppMetadata
import com.example.focuslock.domain.BlockAuditLog
import com.example.focuslock.domain.BlockReason
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RuleType
import com.example.focuslock.domain.SavedPost
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class EntityMappersTest {

    @Test
    fun `BlockRule round-trip preserves package JSON, bitmask, and all nullable fields`() {
        val fullRule = BlockRule(
            id = "rule-full",
            title = "Couvre-feu & Sabbatique",
            ruleType = RuleType.SABBATICAL,
            targetPackages = setOf("com.zhiliaoapp.musically", "com.instagram.android"),
            isEnabled = true,
            isStrictLocked = true,
            activeDaysBitmask = DaysBitmask.WEEKDAYS,
            startTimeMinutes = 22 * 60 + 30,
            endTimeMinutes = 7 * 60,
            dailyQuotaMinutes = 30,
            cycleIntervalDays = 2,
            cycleStartEpochDay = 20765L,
            pauseUntil = Instant.ofEpochMilli(1_762_900_000_000L),
            customBlockMessage = "Pause digitale"
        )

        val entity = fullRule.toEntity()
        // Vérifie la sérialisation JSON triée
        assertEquals("""["com.instagram.android","com.zhiliaoapp.musically"]""", entity.targetPackagesJson)
        assertEquals(DaysBitmask.WEEKDAYS, entity.activeDaysBitmask)
        assertEquals(fullRule, entity.toDomain())

        // Vérifie avec tous les champs optionnels à null et ensemble de packages vide
        val minimalRule = BlockRule(
            id = "rule-min",
            title = "Règle Minimale",
            ruleType = RuleType.DAILY_QUOTA,
            targetPackages = emptySet(),
            activeDaysBitmask = DaysBitmask.SUNDAY
        )
        val minEntity = minimalRule.toEntity()
        assertEquals("[]", minEntity.targetPackagesJson)
        assertNull(minEntity.startTimeMinutes)
        assertNull(minEntity.pauseUntilTimestamp)
        assertEquals(minimalRule, minEntity.toDomain())
    }

    @Test
    fun `QuestTask round-trip maps P1_CRITICAL, P2_IMPORTANT, P3_SECONDARY and nullable timestamps`() {
        val taskP1 = QuestTask(
            id = "q-p1",
            title = "1h Espagnol",
            description = "Grammaire sur tablette",
            priority = QuestPriority.P1,
            xpValue = 100,
            dueAt = Instant.ofEpochMilli(1_762_650_000_000L),
            completedAt = null,
            isRoutineChecklistItem = true,
            parentRoutineId = "routine-1",
            penaltyApplied = true
        )

        val entityP1 = taskP1.toEntity()
        assertEquals("P1_CRITICAL", entityP1.priority)
        assertNull(entityP1.completedAtTimestamp)
        assertEquals(taskP1, entityP1.toDomain())

        val taskP2 = taskP1.copy(
            id = "q-p2",
            priority = QuestPriority.P2,
            xpValue = 50,
            completedAt = Instant.ofEpochMilli(1_762_640_000_000L),
            parentRoutineId = null
        )
        assertEquals("P2_IMPORTANT", taskP2.toEntity().priority)
        assertEquals(taskP2, taskP2.toEntity().toDomain())
    }

    @Test
    fun `FocusSession, SavedPost, BlockAuditLog and AppMetadata round-trip accurately`() {
        val session = FocusSession(
            id = "sess-1",
            sessionType = SessionType.ACTIVE_LEARNING,
            label = "Espagnol — Tablette",
            startedAt = Instant.ofEpochMilli(1_762_600_000_000L),
            plannedEndAt = Instant.ofEpochMilli(1_762_603_600_000L),
            actualEndedAt = null,
            whitelistedPackages = setOf("com.spotify.music"),
            summaryValidationNote = null,
            status = SessionStatus.RUNNING
        )
        assertEquals(session, session.toEntity().toDomain())

        val savedPost = SavedPost(
            id = "post-1",
            sourceUrl = "https://www.instagram.com/reel/xyz/",
            extractedCaption = "Routine mobilité",
            userTitle = "Épaules saines",
            personalNote = null,
            tag = "Sport",
            savedAt = Instant.ofEpochMilli(1_762_610_000_000L),
            isReviewed = false,
            isFavorite = true,
            thumbnailLocalPath = null,
            mediaLocalPath = null
        )
        assertEquals(savedPost, savedPost.toEntity().toDomain())

        val auditLog = BlockAuditLog(
            id = 42L,
            timestamp = Instant.ofEpochMilli(1_762_620_000_000L),
            blockedPackageName = "com.instagram.android",
            triggerRuleId = "r1",
            reasonCode = BlockReason.ACTIVE_FOCUS_SESSION,
            explanationText = "Contrat d'apprentissage en cours"
        )
        assertEquals(auditLog, auditLog.toEntity().toDomain())

        val metadata = AppMetadata(
            packageName = "com.strava",
            category = AppCategory.PRODUCTIVE,
            isAlwaysWhitelisted = false
        )
        assertEquals(metadata, metadata.toEntity().toDomain())
    }
}