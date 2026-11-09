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
    val lastP2CompletionEpochDay: Long? = null
)

interface GamificationStore {
    val state: Flow<GamificationState>
    suspend fun getSnapshot(): GamificationState
    suspend fun update(transform: (GamificationState) -> GamificationState): GamificationState
}