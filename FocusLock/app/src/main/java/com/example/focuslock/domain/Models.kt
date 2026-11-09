package com.example.focuslock.domain

import java.time.DayOfWeek
import java.time.Instant

// ==================== MODÈLES PHASE 1 (CONSERVÉS POUR L'UI) ====================

enum class QuestPriority(
    val label: String,
    val dbCode: String,
    val xpReward: Int,
    val xpPenalty: Int,
    val focusDebtMinutes: Int,
    val quotaReductionPercent: Int
) {
    // Section 5 : P1 (+100 XP | -50 XP, 30 min dette, -50 % quota)
    P1(
        label = "P1",
        dbCode = "P1_CRITICAL",
        xpReward = 100,
        xpPenalty = 50,
        focusDebtMinutes = 30,
        quotaReductionPercent = 50
    ),

    // Section 5 : P2 (+50 XP | -25 XP, 15 min dette, -25 % quota)
    P2(
        label = "P2",
        dbCode = "P2_IMPORTANT",
        xpReward = 50,
        xpPenalty = 25,
        focusDebtMinutes = 15,
        quotaReductionPercent = 25
    ),

    // Section 5 : P3 (+20 XP | -10 XP, 0 min individuelle / 15 min au 3e retard, 0 % quota)
    P3(
        label = "P3",
        dbCode = "P3_SECONDARY",
        xpReward = 20,
        xpPenalty = 10,
        focusDebtMinutes = 0,
        quotaReductionPercent = 0
    );

    companion object {
        fun fromDbCode(code: String): QuestPriority {
            return entries.firstOrNull { it.dbCode == code || it.name == code } ?: P3
        }
    }
}

typealias Priority = QuestPriority

enum class FocusMode(val label: String) {
    FOCUS("Focus"),
    LANGUAGE("Étude Langue"),
    READING("Lecture")
}

data class QuestPreviewItem(
    val id: String,
    val title: String,
    val priority: QuestPriority,
    val isCompleted: Boolean
)

// ==================== MODÈLES PURS DU DOMAINE (BLOC B & BLOC C) ====================

enum class RuleType {
    ROUTINE,
    SABBATICAL,
    ALTERNATING_DETOX,
    SCHEDULE,
    DAILY_QUOTA
}

object DaysBitmask {
    const val MONDAY: Int = 1 shl 0     // 1
    const val TUESDAY: Int = 1 shl 1    // 2
    const val WEDNESDAY: Int = 1 shl 2  // 4
    const val THURSDAY: Int = 1 shl 3   // 8
    const val FRIDAY: Int = 1 shl 4     // 16
    const val SATURDAY: Int = 1 shl 5   // 32
    const val SUNDAY: Int = 1 shl 6     // 64

    const val WEEKDAYS: Int = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY // 31
    const val WEEKEND: Int = SATURDAY or SUNDAY                                    // 96
    const val ALL_DAYS: Int = WEEKDAYS or WEEKEND                                  // 127

    fun isDayActive(bitmask: Int, dayOfWeek: DayOfWeek): Boolean {
        val bit = 1 shl (dayOfWeek.value - 1)
        return (bitmask and bit) != 0
    }
}

data class BlockRule(
    val id: String,
    val title: String,
    val ruleType: RuleType,
    val targetPackages: Set<String>,
    val isEnabled: Boolean = true,
    val isStrictLocked: Boolean = false,
    val activeDaysBitmask: Int = DaysBitmask.ALL_DAYS,
    val startTimeMinutes: Int? = null,
    val endTimeMinutes: Int? = null,
    val dailyQuotaMinutes: Int? = null,
    val cycleIntervalDays: Int? = null,
    val cycleStartEpochDay: Long? = null,
    val pauseUntil: Instant? = null,
    val customBlockMessage: String? = null
)

data class QuestTask(
    val id: String,
    val title: String,
    val description: String = "",
    val priority: QuestPriority = QuestPriority.P1,
    val xpValue: Int = priority.xpReward,
    val dueAt: Instant? = null,
    val completedAt: Instant? = null,
    val isRoutineChecklistItem: Boolean = false,
    val parentRoutineId: String? = null,
    val penaltyApplied: Boolean = false
)

enum class SessionType {
    ACTIVE_LEARNING,
    DEEP_WORK,
    FOCUS_DEBT_MALUS
}

enum class SessionStatus {
    RUNNING,
    COMPLETED,
    BROKEN_BY_EMERGENCY
}

data class FocusSession(
    val id: String,
    val sessionType: SessionType,
    val label: String,
    val startedAt: Instant,
    val plannedEndAt: Instant,
    val actualEndedAt: Instant? = null,
    val whitelistedPackages: Set<String> = emptySet(),
    val summaryValidationNote: String? = null,
    val status: SessionStatus = SessionStatus.RUNNING
)

data class SavedPost(
    val id: String,
    val sourceUrl: String,
    val extractedCaption: String? = null,
    val userTitle: String,
    val personalNote: String? = null,
    val tag: String,
    val savedAt: Instant,
    val isReviewed: Boolean = false,
    val isFavorite: Boolean = false,
    val thumbnailLocalPath: String? = null,
    val mediaLocalPath: String? = null
)

data class BlockAuditLog(
    val id: Long = 0L,
    val timestamp: Instant,
    val blockedPackageName: String,
    val triggerRuleId: String? = null,
    val reasonCode: BlockReason,
    val explanationText: String
)

enum class AppCategory {
    DISTRACTING,
    NEUTRAL,
    PRODUCTIVE
}

data class AppMetadata(
    val packageName: String,
    val category: AppCategory = AppCategory.NEUTRAL,
    val isAlwaysWhitelisted: Boolean = false
)

data class SecurityState(
    val hardwareWhitelistedPackages: Set<String> = DEFAULT_HARDWARE_WHITELIST,
    val isCallInProgress: Boolean = false,
    val emergencyUnlockUntil: Instant? = null,
    val quotaMalusReductionPercent: Int = 0
) {
    companion object {
        val DEFAULT_HARDWARE_WHITELIST: Set<String> = setOf(
            "com.example.focuslock",
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.samsung.android.dialer",
            "com.google.android.dialer",
            "com.android.server.telecom",
            "com.sec.android.app.clockpackage", // Horloge Samsung (2.22)
            "com.google.android.deskclock",     // Horloge Google (2.22)
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin"
        )
    }
}

enum class BlockReason {
    ACTIVE_FOCUS_SESSION,
    FOCUS_DEBT_PENALTY,
    MORNING_ROUTINE,
    SABBATICAL_OR_DETOX,
    SCHEDULE_OR_QUOTA
}

sealed interface BlockDecision {
    data object Allow : BlockDecision

    data class Block(
        val reason: BlockReason,
        val ruleId: String? = null,
        val message: String? = null
    ) : BlockDecision
}
