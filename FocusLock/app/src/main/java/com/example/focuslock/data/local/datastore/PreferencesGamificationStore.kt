package com.example.focuslock.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.focuslock.domain.store.GamificationState
import com.example.focuslock.domain.store.GamificationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class PreferencesGamificationStore(
    private val dataStore: DataStore<Preferences>
) : GamificationStore {

    companion object {
        private const val STORE_FILE_NAME = "focuslock_gamification_prefs"

        val KEY_TOTAL_XP = intPreferencesKey("totalXp")
        val KEY_CURRENT_STREAK_DAYS = intPreferencesKey("currentStreakDays")
        val KEY_LAST_STREAK_EVAL_EPOCH_DAY = longPreferencesKey("lastStreakEvalEpochDay")
        val KEY_QUOTA_MALUS_REDUCTION_PERCENT = intPreferencesKey("quotaMalusReductionPercent")
        val KEY_QUOTA_MALUS_EPOCH_DAY = longPreferencesKey("quotaMalusEpochDay")
        val KEY_P3_LATE_COUNTER = intPreferencesKey("p3LateCounter")
        val KEY_DAILY_FOCUS_GOAL_MINUTES = intPreferencesKey("dailyFocusGoalMinutes")
        val KEY_CONSECUTIVE_P2_DAYS = intPreferencesKey("consecutiveP2Days")
        val KEY_LAST_P2_COMPLETION_EPOCH_DAY = longPreferencesKey("lastP2CompletionEpochDay")

        @Volatile
        private var INSTANCE: DataStore<Preferences>? = null

        fun createDefault(context: Context): PreferencesGamificationStore {
            val ds = INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceDataStoreFactory.create(
                    produceFile = { context.applicationContext.preferencesDataStoreFile(STORE_FILE_NAME) }
                ).also { INSTANCE = it }
            }
            return PreferencesGamificationStore(ds)
        }
    }

    override val state: Flow<GamificationState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs.toGamificationState() }

    override suspend fun getSnapshot(): GamificationState = state.first()

    override suspend fun update(
        transform: (GamificationState) -> GamificationState
    ): GamificationState {
        var updatedState = GamificationState()
        dataStore.edit { prefs ->
            val current = prefs.toGamificationState()
            val next = transform(current)
            updatedState = next

            prefs[KEY_TOTAL_XP] = next.totalXp.coerceAtLeast(0)
            prefs[KEY_CURRENT_STREAK_DAYS] = next.currentStreakDays.coerceAtLeast(0)
            if (next.lastStreakEvalEpochDay != null) {
                prefs[KEY_LAST_STREAK_EVAL_EPOCH_DAY] = next.lastStreakEvalEpochDay
            } else {
                prefs.remove(KEY_LAST_STREAK_EVAL_EPOCH_DAY)
            }

            prefs[KEY_QUOTA_MALUS_REDUCTION_PERCENT] = next.quotaMalusReductionPercent.coerceIn(0, 100)
            if (next.quotaMalusEpochDay != null) {
                prefs[KEY_QUOTA_MALUS_EPOCH_DAY] = next.quotaMalusEpochDay
            } else {
                prefs.remove(KEY_QUOTA_MALUS_EPOCH_DAY)
            }

            prefs[KEY_P3_LATE_COUNTER] = next.p3LateCounter.coerceAtLeast(0)
            prefs[KEY_DAILY_FOCUS_GOAL_MINUTES] = next.dailyFocusGoalMinutes.coerceAtLeast(5)
            prefs[KEY_CONSECUTIVE_P2_DAYS] = next.consecutiveP2Days.coerceAtLeast(0)
            if (next.lastP2CompletionEpochDay != null) {
                prefs[KEY_LAST_P2_COMPLETION_EPOCH_DAY] = next.lastP2CompletionEpochDay
            } else {
                prefs.remove(KEY_LAST_P2_COMPLETION_EPOCH_DAY)
            }
        }
        return updatedState
    }

    private fun Preferences.toGamificationState(): GamificationState = GamificationState(
        totalXp = this[KEY_TOTAL_XP] ?: 0,
        currentStreakDays = this[KEY_CURRENT_STREAK_DAYS] ?: 0,
        lastStreakEvalEpochDay = this[KEY_LAST_STREAK_EVAL_EPOCH_DAY],
        quotaMalusReductionPercent = this[KEY_QUOTA_MALUS_REDUCTION_PERCENT] ?: 0,
        quotaMalusEpochDay = this[KEY_QUOTA_MALUS_EPOCH_DAY],
        p3LateCounter = this[KEY_P3_LATE_COUNTER] ?: 0,
        dailyFocusGoalMinutes = this[KEY_DAILY_FOCUS_GOAL_MINUTES] ?: 120,
        consecutiveP2Days = this[KEY_CONSECUTIVE_P2_DAYS] ?: 0,
        lastP2CompletionEpochDay = this[KEY_LAST_P2_COMPLETION_EPOCH_DAY]
    )
}