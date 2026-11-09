package com.example.focuslock.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 1. Table des Règles de blocage (Section 9.3).
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val ruleType: String, // SCHEDULE, DAILY_QUOTA, ALTERNATING_DETOX, SABBATICAL, ROUTINE
    val targetPackagesJson: String,
    val startTimeMinutes: Int?,
    val endTimeMinutes: Int?,
    val activeDaysBitmask: Int,
    val dailyQuotaMinutes: Int?,
    val cycleIntervalDays: Int?,
    val cycleStartEpochDay: Long?,
    val pauseUntilTimestamp: Long?,
    val isStrictLocked: Boolean,
    val isEnabled: Boolean,
    val customBlockMessage: String?
)

/**
 * 2. Table des Tâches & Routines (Section 9.3).
 */
@Entity(
    tableName = "quest_tasks",
    indices = [Index(value = ["parentRoutineId"])]
)
data class QuestTaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val priority: String, // P1_CRITICAL, P2_IMPORTANT, P3_SECONDARY
    val xpValue: Int,     // 100, 50, 20
    val dueAtTimestamp: Long?,
    val completedAtTimestamp: Long?,
    val isRoutineChecklistItem: Boolean,
    val parentRoutineId: String?,
    val penaltyApplied: Boolean
)

/**
 * 3. Contrats de Temps & Dettes de Focus (Section 9.3).
 */
@Entity(
    tableName = "focus_sessions",
    indices = [Index(value = ["status"])]
)
data class FocusSessionEntity(
    @PrimaryKey
    val id: String,
    val sessionType: String, // ACTIVE_LEARNING, DEEP_WORK, FOCUS_DEBT_MALUS
    val label: String,
    val startedAt: Long,
    val plannedEndAt: Long,
    val actualEndedAt: Long?,
    val whitelistedPackagesJson: String,
    val summaryValidationNote: String?,
    val status: String // RUNNING, COMPLETED, BROKEN_BY_EMERGENCY
)

/**
 * 4. Coffre de Contenu Instagram hors-ligne — Option A & préparation Option B (Section 9.3).
 */
@Entity(
    tableName = "saved_contents",
    indices = [Index(value = ["tag"]), Index(value = ["savedAtTimestamp"])]
)
data class SavedContentEntity(
    @PrimaryKey
    val id: String,
    val sourceUrl: String,
    val extractedCaption: String?,
    val userTitle: String,
    val personalNote: String?,
    val tag: String,
    val savedAtTimestamp: Long,
    val isReviewed: Boolean,
    val isFavorite: Boolean,
    val thumbnailLocalPath: String?,
    val mediaLocalPath: String?
)

/**
 * 5. Journal d'Audit & Compteur de Tentatives (Section 9.3).
 * Permet d'afficher "2x aujourd'hui | 23x au total" et la carte "Distractions évitées".
 */
@Entity(
    tableName = "block_audit_logs",
    indices = [Index(value = ["blockedPackageName", "timestamp"])]
)
data class BlockAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val blockedPackageName: String,
    val triggerRuleId: String?,
    val reasonCode: String,
    val explanationText: String
)

/**
 * 6. Catégories d'applications pour Insights (Section 9.3).
 */
@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey
    val packageName: String,
    val category: String, // DISTRACTING, NEUTRAL, PRODUCTIVE
    val isAlwaysWhitelisted: Boolean
)