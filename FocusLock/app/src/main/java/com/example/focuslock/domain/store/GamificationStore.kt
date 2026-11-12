package com.example.focuslock.domain.store

import kotlinx.coroutines.flow.Flow

data class GamificationState(
    val totalXp: Int = 0,
    val currentStreakDays: Int = 0,
    val lastStreakEvalEpochDay: Long? = null,
    val quotaMalusReductionPercent: Int = 0,
    val quotaMalusEpochDay: Long? = null,
    val p3LateCounter: Int = 0,
    val dailyFocusGoalMinutes: Int = 120,
    val consecutiveP2Days: Int = 0,
    val lastP2CompletionEpochDay: Long? = null,
    // 3.24 : Crédit de récréation (dépense d'XP du jour sans baisser totalXp / le grade)
    val recreationSpentTodayXp: Int = 0,
    val recreationSpentEpochDay: Long? = null,
    val recreationCreditUntilTimestamp: Long? = null,
    // 4.8 : Fenêtre de consultation contrôlée du Coffre (3 min)
    val viewingWindowPackage: String? = null,
    val viewingWindowExpiresAtTimestamp: Long? = null,
    // 4.13 : Rattrapage idempotent de DailyUsageEntity
    val lastUsageSnapshotEpochDay: Long? = null,
    // 4.18 (Décision #19) : Fenêtre d'éveil réglable (défaut 07:00 - 23:00)
    val awakeStartMinutes: Int = 7 * 60,
    val awakeEndMinutes: Int = 23 * 60
)

interface GamificationStore {
    val state: Flow<GamificationState>
    suspend fun getSnapshot(): GamificationState
    suspend fun update(transform: (GamificationState) -> GamificationState): GamificationState
}
