package com.example.focuslock.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val KEY_BEST_STREAK_DAYS = intPreferencesKey("bestStreakDays")
        val KEY_LAST_STREAK_EVAL_EPOCH_DAY = longPreferencesKey("lastStreakEvalEpochDay")
        val KEY_QUOTA_MALUS_REDUCTION_PERCENT = intPreferencesKey("quotaMalusReductionPercent")
        val KEY_QUOTA_MALUS_EPOCH_DAY = longPreferencesKey("quotaMalusEpochDay")
        val KEY_P3_LATE_COUNTER = intPreferencesKey("p3LateCounter")
        val KEY_DAILY_FOCUS_GOAL_MINUTES = intPreferencesKey("dailyFocusGoalMinutes")
        val KEY_CONSECUTIVE_P2_DAYS = intPreferencesKey("consecutiveP2Days")
        val KEY_LAST_P2_COMPLETION_EPOCH_DAY = longPreferencesKey("lastP2CompletionEpochDay")
        val KEY_RECREATION_SPENT_TODAY_XP = intPreferencesKey("recreationSpentTodayXp")
        val KEY_RECREATION_SPENT_EPOCH_DAY = longPreferencesKey("recreationSpentEpochDay")
        val KEY_RECREATION_CREDIT_UNTIL_TIMESTAMP = longPreferencesKey("recreationCreditUntilTimestamp")
        val KEY_VIEWING_WINDOW_PACKAGE = stringPreferencesKey("viewingWindowPackage")
        val KEY_VIEWING_WINDOW_EXPIRES_AT = longPreferencesKey("viewingWindowExpiresAtTimestamp")
        val KEY_LAST_USAGE_SNAPSHOT_EPOCH_DAY = longPreferencesKey("lastUsageSnapshotEpochDay")
        val KEY_AWAKE_START_MINUTES = intPreferencesKey("awakeStartMinutes")
        val KEY_AWAKE_END_MINUTES = intPreferencesKey("awakeEndMinutes")

        // Phase 5 : Clés de sécurité stricte, PIN PBKDF2 & Santé Système
        val KEY_PIN_HASH_PBKDF2 = stringPreferencesKey("pinHashPBKDF2")
        val KEY_PIN_SALT = stringPreferencesKey("pinSalt")
        val KEY_FAILED_PIN_ATTEMPTS = intPreferencesKey("failedPinAttempts")
        val KEY_PIN_LOCKOUT_UNTIL_TIMESTAMP = longPreferencesKey("pinLockoutUntilTimestamp")
        val KEY_MINDFUL_EDIT_DELAY_SECONDS = intPreferencesKey("mindfulEditDelaySeconds")
        val KEY_STRICT_AUTO_RELOCK_MINUTES = intPreferencesKey("strictAutoRelockMinutes")
        val KEY_EMERGENCY_UNLOCK_UNTIL_TIMESTAMP = longPreferencesKey("emergencyUnlockUntilTimestamp")
        val KEY_BLOCK_FLOATING_WINDOWS = booleanPreferencesKey("blockFloatingWindows")
        val KEY_ENABLE_SCHEDULE_PAUSE = booleanPreferencesKey("enableSchedulePause")
        val KEY_BLOCK_UNINSTALL_AND_SETTINGS = booleanPreferencesKey("blockUninstallAndSettings")
        val KEY_SECURE_AFTER_RESTART = booleanPreferencesKey("secureAfterRestart")
        val KEY_LAST_SERVICE_HEARTBEAT = longPreferencesKey("lastServiceHeartbeat")
        val KEY_DEFAULT_CUSTOM_BLOCK_MESSAGE = stringPreferencesKey("defaultCustomBlockMessage")

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
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> prefs.toGamificationState() }

    override suspend fun getSnapshot(): GamificationState = state.first()

    override suspend fun update(
        transform: (GamificationState) -> GamificationState
    ): GamificationState {
        var updatedState = GamificationState()
        dataStore.edit { prefs ->
            val current = prefs.toGamificationState()
            val rawNext = transform(current)
            val next = rawNext.copy(
                bestStreakDays = maxOf(rawNext.bestStreakDays, rawNext.currentStreakDays)
            )
            updatedState = next

            prefs[KEY_TOTAL_XP] = next.totalXp.coerceAtLeast(0)
            prefs[KEY_CURRENT_STREAK_DAYS] = next.currentStreakDays.coerceAtLeast(0)
            prefs[KEY_BEST_STREAK_DAYS] = next.bestStreakDays.coerceAtLeast(0)
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
            prefs[KEY_DAILY_FOCUS_GOAL_MINUTES] = next.dailyFocusGoalMinutes.coerceIn(5, 720)
            prefs[KEY_CONSECUTIVE_P2_DAYS] = next.consecutiveP2Days.coerceAtLeast(0)
            if (next.lastP2CompletionEpochDay != null) {
                prefs[KEY_LAST_P2_COMPLETION_EPOCH_DAY] = next.lastP2CompletionEpochDay
            } else {
                prefs.remove(KEY_LAST_P2_COMPLETION_EPOCH_DAY)
            }

            prefs[KEY_RECREATION_SPENT_TODAY_XP] = next.recreationSpentTodayXp.coerceAtLeast(0)
            if (next.recreationSpentEpochDay != null) {
                prefs[KEY_RECREATION_SPENT_EPOCH_DAY] = next.recreationSpentEpochDay
            } else {
                prefs.remove(KEY_RECREATION_SPENT_EPOCH_DAY)
            }
            if (next.recreationCreditUntilTimestamp != null) {
                prefs[KEY_RECREATION_CREDIT_UNTIL_TIMESTAMP] = next.recreationCreditUntilTimestamp
            } else {
                prefs.remove(KEY_RECREATION_CREDIT_UNTIL_TIMESTAMP)
            }

            if (next.viewingWindowPackage != null && next.viewingWindowExpiresAtTimestamp != null) {
                prefs[KEY_VIEWING_WINDOW_PACKAGE] = next.viewingWindowPackage
                prefs[KEY_VIEWING_WINDOW_EXPIRES_AT] = next.viewingWindowExpiresAtTimestamp
            } else {
                prefs.remove(KEY_VIEWING_WINDOW_PACKAGE)
                prefs.remove(KEY_VIEWING_WINDOW_EXPIRES_AT)
            }

            if (next.lastUsageSnapshotEpochDay != null) {
                prefs[KEY_LAST_USAGE_SNAPSHOT_EPOCH_DAY] = next.lastUsageSnapshotEpochDay
            } else {
                prefs.remove(KEY_LAST_USAGE_SNAPSHOT_EPOCH_DAY)
            }

            prefs[KEY_AWAKE_START_MINUTES] = next.awakeStartMinutes.coerceIn(0, 24 * 60 - 1)
            prefs[KEY_AWAKE_END_MINUTES] = next.awakeEndMinutes.coerceIn(0, 24 * 60 - 1)

            // Phase 5
            if (next.pinHashPBKDF2 != null && next.pinSalt != null) {
                prefs[KEY_PIN_HASH_PBKDF2] = next.pinHashPBKDF2
                prefs[KEY_PIN_SALT] = next.pinSalt
            } else {
                prefs.remove(KEY_PIN_HASH_PBKDF2)
                prefs.remove(KEY_PIN_SALT)
            }
            prefs[KEY_FAILED_PIN_ATTEMPTS] = next.failedPinAttempts.coerceAtLeast(0)
            if (next.pinLockoutUntilTimestamp != null) {
                prefs[KEY_PIN_LOCKOUT_UNTIL_TIMESTAMP] = next.pinLockoutUntilTimestamp
            } else {
                prefs.remove(KEY_PIN_LOCKOUT_UNTIL_TIMESTAMP)
            }
            prefs[KEY_MINDFUL_EDIT_DELAY_SECONDS] = next.mindfulEditDelaySeconds.coerceIn(10, 60)
            prefs[KEY_STRICT_AUTO_RELOCK_MINUTES] = next.strictAutoRelockMinutes.coerceIn(1, 60)
            if (next.emergencyUnlockUntilTimestamp != null) {
                prefs[KEY_EMERGENCY_UNLOCK_UNTIL_TIMESTAMP] = next.emergencyUnlockUntilTimestamp
            } else {
                prefs.remove(KEY_EMERGENCY_UNLOCK_UNTIL_TIMESTAMP)
            }
            prefs[KEY_BLOCK_FLOATING_WINDOWS] = next.blockFloatingWindows
            prefs[KEY_ENABLE_SCHEDULE_PAUSE] = next.enableSchedulePause
            prefs[KEY_BLOCK_UNINSTALL_AND_SETTINGS] = next.blockUninstallAndSettings
            prefs[KEY_SECURE_AFTER_RESTART] = next.secureAfterRestart
            if (next.lastServiceHeartbeatTimestamp != null) {
                prefs[KEY_LAST_SERVICE_HEARTBEAT] = next.lastServiceHeartbeatTimestamp
            } else {
                prefs.remove(KEY_LAST_SERVICE_HEARTBEAT)
            }
            prefs[KEY_DEFAULT_CUSTOM_BLOCK_MESSAGE] = next.defaultCustomBlockMessage
        }
        return updatedState
    }

    private fun Preferences.toGamificationState(): GamificationState {
        val currentStreak = this[KEY_CURRENT_STREAK_DAYS] ?: 0
        val bestStreak = maxOf(this[KEY_BEST_STREAK_DAYS] ?: 0, currentStreak)
        return GamificationState(
            totalXp = this[KEY_TOTAL_XP] ?: 0,
            currentStreakDays = currentStreak,
            bestStreakDays = bestStreak,
            lastStreakEvalEpochDay = this[KEY_LAST_STREAK_EVAL_EPOCH_DAY],
            quotaMalusReductionPercent = this[KEY_QUOTA_MALUS_REDUCTION_PERCENT] ?: 0,
            quotaMalusEpochDay = this[KEY_QUOTA_MALUS_EPOCH_DAY],
            p3LateCounter = this[KEY_P3_LATE_COUNTER] ?: 0,
            dailyFocusGoalMinutes = this[KEY_DAILY_FOCUS_GOAL_MINUTES] ?: 120,
            consecutiveP2Days = this[KEY_CONSECUTIVE_P2_DAYS] ?: 0,
            lastP2CompletionEpochDay = this[KEY_LAST_P2_COMPLETION_EPOCH_DAY],
            recreationSpentTodayXp = this[KEY_RECREATION_SPENT_TODAY_XP] ?: 0,
            recreationSpentEpochDay = this[KEY_RECREATION_SPENT_EPOCH_DAY],
            recreationCreditUntilTimestamp = this[KEY_RECREATION_CREDIT_UNTIL_TIMESTAMP],
            viewingWindowPackage = this[KEY_VIEWING_WINDOW_PACKAGE],
            viewingWindowExpiresAtTimestamp = this[KEY_VIEWING_WINDOW_EXPIRES_AT],
            lastUsageSnapshotEpochDay = this[KEY_LAST_USAGE_SNAPSHOT_EPOCH_DAY],
            awakeStartMinutes = this[KEY_AWAKE_START_MINUTES] ?: (7 * 60),
            awakeEndMinutes = this[KEY_AWAKE_END_MINUTES] ?: (23 * 60),
            pinHashPBKDF2 = this[KEY_PIN_HASH_PBKDF2],
            pinSalt = this[KEY_PIN_SALT],
            failedPinAttempts = this[KEY_FAILED_PIN_ATTEMPTS] ?: 0,
            pinLockoutUntilTimestamp = this[KEY_PIN_LOCKOUT_UNTIL_TIMESTAMP],
            mindfulEditDelaySeconds = this[KEY_MINDFUL_EDIT_DELAY_SECONDS] ?: 10,
            strictAutoRelockMinutes = this[KEY_STRICT_AUTO_RELOCK_MINUTES] ?: 15,
            emergencyUnlockUntilTimestamp = this[KEY_EMERGENCY_UNLOCK_UNTIL_TIMESTAMP],
            blockFloatingWindows = this[KEY_BLOCK_FLOATING_WINDOWS] ?: true,
            enableSchedulePause = this[KEY_ENABLE_SCHEDULE_PAUSE] ?: false,
            blockUninstallAndSettings = this[KEY_BLOCK_UNINSTALL_AND_SETTINGS] ?: true,
            secureAfterRestart = this[KEY_SECURE_AFTER_RESTART] ?: true,
            lastServiceHeartbeatTimestamp = this[KEY_LAST_SERVICE_HEARTBEAT],
            defaultCustomBlockMessage = this[KEY_DEFAULT_CUSTOM_BLOCK_MESSAGE]
                ?: "C'est ton temps d'étude. Une minute ici est une minute que tu ne récupéreras pas."
        )
    }
}
