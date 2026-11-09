package com.example.focuslock.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

object GamificationConstants {
    // Bonus de régularité P2 (+15 XP à partir de 3 jours consécutifs)
    const val P2_STREAK_BONUS_DAYS_THRESHOLD: Int = 3
    const val P2_STREAK_BONUS_XP: Int = 15

    // Cumul des tâches P3 en retard (3 retards P3 = 15 min de Dette de Focus)
    const val P3_OVERDUE_STRIKE_THRESHOLD: Int = 3
    const val P3_ACCUMULATED_DEBT_MINUTES: Int = 15

    // Plafonds de sécurité (Section 5 & Section 6.2)
    const val MIN_TOTAL_XP: Int = 0
    const val MAX_QUOTA_REDUCTION_PERCENT: Int = 100
    const val MAX_ACCUMULATED_DEBT_MINUTES: Int = 120 // Plafond fail-safe de 2h max de dette

    // Fenêtre anti-triche avant échéance P1/P2 (moins de 2 heures -> Mindful Edits + PIN)
    val QUEST_FREEZE_WINDOW: Duration = Duration.ofHours(2)

    // Courbe de progression des 10 niveaux et grades officiels (Section 5)
    val LEVEL_CURVE: List<LevelThreshold> = listOf(
        LevelThreshold(level = 1, minXp = 0, gradeName = "Novice"),
        LevelThreshold(level = 2, minXp = 100, gradeName = "Initié"),
        LevelThreshold(level = 3, minXp = 250, gradeName = "Apprenti"),
        LevelThreshold(level = 4, minXp = 450, gradeName = "Assidu"),
        LevelThreshold(level = 5, minXp = 700, gradeName = "Structuré"),
        LevelThreshold(level = 6, minXp = 1000, gradeName = "Méthodique"),
        LevelThreshold(level = 7, minXp = 1400, gradeName = "Rigoureux"),
        LevelThreshold(level = 8, minXp = 1900, gradeName = "Stoïque"),
        LevelThreshold(level = 9, minXp = 2500, gradeName = "Maître"),
        LevelThreshold(level = 10, minXp = 3200, gradeName = "Inébranlable")
    )
}

data class LevelThreshold(
    val level: Int,
    val minXp: Int,
    val gradeName: String
)

data class LevelProgress(
    val level: Int,
    val gradeName: String,
    val totalXp: Int,
    val currentLevelMinXp: Int,
    val nextLevelMinXp: Int?,
    val progressRatio: Float
)

data class XpAwardResult(
    val baseXp: Int,
    val bonusXp: Int,
    val totalEarnedXp: Int,
    val newTotalXp: Int,
    val newConsecutiveP2Days: Int,
    val updatedLastP2CompletionDate: LocalDate?,
    val countsForStreak: Boolean
)

data class MalusEvent(
    val taskId: String,
    val priority: QuestPriority,
    val xpLost: Int,
    val debtMinutes: Int,
    val quotaReductionPercent: Int,
    val triggeredByP3StrikeThreshold: Boolean = false
)

data class MalusDetectionResult(
    val events: List<MalusEvent>,
    val newP3OverdueCount: Int,
    val penalizedTaskIds: Set<String>
)

data class PenaltyPlan(
    val newTotalXp: Int,
    val totalXpLost: Int,
    val addedDebtMinutes: Int,
    val newTotalDebtMinutes: Int,
    val newQuotaReductionPercent: Int,
    val evaluatedDate: LocalDate
)

data class StreakEvaluationResult(
    val currentStreakDays: Int,
    val lastEvaluatedEpochDay: Long
)

enum class FrictionReason {
    DISABLING_ACTIVE_RULE,
    DELETING_ACTIVE_RULE,
    REMOVING_TARGET_PACKAGES,
    REDUCING_ACTIVE_DAYS,
    SHORTENING_SCHEDULE_WINDOW,
    LOOSENING_DAILY_QUOTA,
    SHORTENING_SABBATICAL,
    LOOSENING_DETOX_CYCLE,
    DELETING_ENGAGED_PRIORITY_TASK,
    DOWNGRADING_TASK_PRIORITY,
    POSTPONING_IMMINENT_DEADLINE
}

sealed interface EditPermission {
    data object Allowed : EditPermission
    data class RequiresFriction(val reason: FrictionReason) : EditPermission
}