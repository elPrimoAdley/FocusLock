package com.example.focuslock.data

import android.content.Context
import com.example.focuslock.data.local.FocusDatabase
import com.example.focuslock.data.repository.RoomAppMetadataRepository
import com.example.focuslock.data.repository.RoomBlockAuditRepository
import com.example.focuslock.data.repository.RoomFocusSessionRepository
import com.example.focuslock.data.repository.RoomQuestTaskRepository
import com.example.focuslock.data.repository.RoomRuleRepository
import com.example.focuslock.data.repository.RoomSavedContentRepository
import com.example.focuslock.domain.repository.AppMetadataRepository
import com.example.focuslock.domain.repository.BlockAuditRepository
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.repository.RuleRepository
import com.example.focuslock.domain.repository.SavedContentRepository
import com.example.focuslock.data.accessibility.AccessibilityForegroundAppDetector
import com.example.focuslock.data.usage.UsageStatsTracker
import java.time.Clock

class AppContainer(val appContext: Context) {

    val clock: Clock = Clock.systemDefaultZone()

    val database: FocusDatabase by lazy {
        FocusDatabase.getInstance(appContext)
    }

    val ruleRepository: RuleRepository by lazy {
        RoomRuleRepository(database.ruleDao())
    }

    val questTaskRepository: QuestTaskRepository by lazy {
        RoomQuestTaskRepository(database.questTaskDao())
    }

    val focusSessionRepository: FocusSessionRepository by lazy {
        RoomFocusSessionRepository(database.focusSessionDao())
    }

    val blockAuditRepository: BlockAuditRepository by lazy {
        RoomBlockAuditRepository(database.blockAuditLogDao())
    }

    val appMetadataRepository: AppMetadataRepository by lazy {
        RoomAppMetadataRepository(database.appMetadataDao())
    }

    val savedContentRepository: SavedContentRepository by lazy {
        RoomSavedContentRepository(database.savedContentDao())
    }

    // 2.16 Détecteur de package au premier plan
    val foregroundAppDetector: AccessibilityForegroundAppDetector by lazy {
        AccessibilityForegroundAppDetector()
    }

    // 2.17 Tracker UsageStats avec cache de 5 secondes
    val usageStatsTracker: UsageStatsTracker by lazy {
        UsageStatsTracker(appContext, clock)
    }
}
