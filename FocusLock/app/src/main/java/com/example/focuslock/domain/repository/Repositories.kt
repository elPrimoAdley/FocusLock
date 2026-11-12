package com.example.focuslock.domain.repository

import com.example.focuslock.domain.AppMetadata
import com.example.focuslock.domain.BlockAuditLog
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.SavedPost
import com.example.focuslock.domain.SessionStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Exécuteur de transaction atomique (implémenté par RoomDatabase.withTransaction dans data).
 */
interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

interface RuleRepository {
    fun observeAllRules(): Flow<List<BlockRule>>
    fun observeEnabledRules(): Flow<List<BlockRule>>
    suspend fun getAllRules(): List<BlockRule>
    suspend fun getRuleById(id: String): BlockRule?
    suspend fun upsertRule(rule: BlockRule)
    suspend fun setRuleEnabled(id: String, enabled: Boolean)
    suspend fun deleteRuleById(id: String)
}

interface QuestTaskRepository {
    fun observeAllTasks(): Flow<List<QuestTask>>
    fun observeChecklistForRoutine(routineId: String): Flow<List<QuestTask>>
    suspend fun getAllTasks(): List<QuestTask>
    suspend fun getTaskById(id: String): QuestTask?
    suspend fun getTasksDueBetween(startOfDay: Instant, endOfDay: Instant): List<QuestTask>
    suspend fun getOverdueUnpenalizedTasks(now: Instant): List<QuestTask>
    suspend fun upsertTask(task: QuestTask)
    suspend fun updateTaskCompletion(id: String, completedAt: Instant?)
    suspend fun markTasksPenalized(ids: Set<String>)
    suspend fun deleteTaskById(id: String)
}

interface FocusSessionRepository {
    fun observeAllSessions(): Flow<List<FocusSession>>
    fun observeRunningSessions(): Flow<List<FocusSession>>
    suspend fun getRunningSessions(): List<FocusSession>
    suspend fun getActiveDebtSession(now: Instant): FocusSession?
    suspend fun getTodayCompletedFocusMinutes(startOfDay: Instant, endOfDay: Instant): Int
    suspend fun upsertSession(session: FocusSession)
    suspend fun updateSessionStatus(
        id: String,
        status: SessionStatus,
        actualEndedAt: Instant?,
        summaryNote: String? = null
    )
    suspend fun completeExpiredSessions(now: Instant): Int
}

interface BlockAuditRepository {
    suspend fun insertLog(log: BlockAuditLog): Long
    fun observeRecentLogs(limit: Int = 100): Flow<List<BlockAuditLog>>
    fun observeTodayCountForPackage(packageName: String, startOfDay: Instant): Flow<Int>
    fun observeTotalCountForPackage(packageName: String): Flow<Int>
    suspend fun getTodayCountForPackage(packageName: String, startOfDay: Instant): Int
    suspend fun getTotalCountForPackage(packageName: String): Int
    fun observeTotalDistractionsPreventedSince(startOfDay: Instant): Flow<Int>
    suspend fun deleteLogsOlderThan(cutoff: Instant): Int
}

data class DailyAppUsageRecord(
    val epochDay: Long,
    val packageName: String,
    val totalMs: Long
)

interface DailyUsageRepository {
    suspend fun saveDayUsage(epochDay: Long, usageByPackageMs: Map<String, Long>)
    suspend fun getUsageBetweenDays(startEpochDay: Long, endEpochDay: Long): List<DailyAppUsageRecord>
    fun observeUsageBetweenDays(startEpochDay: Long, endEpochDay: Long): Flow<List<DailyAppUsageRecord>>
}

interface AppMetadataRepository {
    fun observeAllMetadata(): Flow<List<AppMetadata>>
    suspend fun getMetadataForPackage(packageName: String): AppMetadata?
    suspend fun upsertMetadata(metadata: AppMetadata)
}

sealed interface VaultSaveResult {
    data class Saved(val post: SavedPost) : VaultSaveResult
    data class Duplicate(val existingPost: SavedPost) : VaultSaveResult
}

interface SavedContentRepository {
    fun observeAllSavedPosts(): Flow<List<SavedPost>>
    suspend fun getSavedPostById(id: String): SavedPost?
    suspend fun getSavedPostBySourceUrl(sourceUrl: String): SavedPost?
    suspend fun insertIfAbsent(post: SavedPost): VaultSaveResult
    suspend fun upsertSavedPost(post: SavedPost)
    suspend fun updateTitleTagAndNote(id: String, userTitle: String, tag: String, personalNote: String?)
    suspend fun updateReviewedStatus(id: String, isReviewed: Boolean)
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)
    suspend fun incrementOpenCountAndMarkReviewed(id: String): SavedPost?
    suspend fun deleteSavedPostById(id: String)
}