package com.example.focuslock.data.repository

import androidx.room.withTransaction
import com.example.focuslock.data.local.FocusDatabase
import com.example.focuslock.data.local.dao.AppMetadataDao
import com.example.focuslock.data.local.dao.BlockAuditLogDao
import com.example.focuslock.data.local.dao.FocusSessionDao
import com.example.focuslock.data.local.dao.QuestTaskDao
import com.example.focuslock.data.local.dao.RuleDao
import com.example.focuslock.data.local.dao.SavedContentDao
import com.example.focuslock.data.mapper.toDomain
import com.example.focuslock.data.mapper.toEntity
import com.example.focuslock.domain.AppMetadata
import com.example.focuslock.domain.BlockAuditLog
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.SavedPost
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.repository.AppMetadataRepository
import com.example.focuslock.domain.repository.BlockAuditRepository
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.repository.RuleRepository
import com.example.focuslock.domain.repository.SavedContentRepository
import com.example.focuslock.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomTransactionRunner(
    private val database: FocusDatabase
) : TransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return database.withTransaction { block() }
    }
}

class RoomRuleRepository(private val dao: RuleDao) : RuleRepository {
    override fun observeAllRules(): Flow<List<BlockRule>> =
        dao.observeAllRules().map { list -> list.map { it.toDomain() } }

    override fun observeEnabledRules(): Flow<List<BlockRule>> =
        dao.observeEnabledRules().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllRules(): List<BlockRule> =
        dao.getAllRules().map { it.toDomain() }

    override suspend fun getRuleById(id: String): BlockRule? =
        dao.getRuleById(id)?.toDomain()

    override suspend fun upsertRule(rule: BlockRule) =
        dao.upsertRule(rule.toEntity())

    override suspend fun setRuleEnabled(id: String, enabled: Boolean) =
        dao.setRuleEnabled(id, enabled)

    override suspend fun deleteRuleById(id: String) =
        dao.deleteRuleById(id)
}

class RoomQuestTaskRepository(private val dao: QuestTaskDao) : QuestTaskRepository {
    override fun observeAllTasks(): Flow<List<QuestTask>> =
        dao.observeAllTasks().map { list -> list.map { it.toDomain() } }

    override fun observeChecklistForRoutine(routineId: String): Flow<List<QuestTask>> =
        dao.observeChecklistForRoutine(routineId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAllTasks(): List<QuestTask> =
        dao.getAllTasks().map { it.toDomain() }

    override suspend fun getTaskById(id: String): QuestTask? =
        dao.getTaskById(id)?.toDomain()

    override suspend fun getTasksDueBetween(startOfDay: Instant, endOfDay: Instant): List<QuestTask> =
        dao.getTasksDueBetween(startOfDay.toEpochMilli(), endOfDay.toEpochMilli())
            .map { it.toDomain() }

    override suspend fun getOverdueUnpenalizedTasks(now: Instant): List<QuestTask> =
        dao.getOverdueUnpenalizedTasks(now.toEpochMilli()).map { it.toDomain() }

    override suspend fun upsertTask(task: QuestTask) =
        dao.upsertTask(task.toEntity())

    override suspend fun updateTaskCompletion(id: String, completedAt: Instant?) =
        dao.updateTaskCompletion(id, completedAt?.toEpochMilli())

    override suspend fun markTasksPenalized(ids: Set<String>) {
        if (ids.isNotEmpty()) {
            dao.markTasksPenalized(ids.toList())
        }
    }

    override suspend fun deleteTaskById(id: String) =
        dao.deleteTaskById(id)
}

class RoomFocusSessionRepository(private val dao: FocusSessionDao) : FocusSessionRepository {
    override fun observeAllSessions(): Flow<List<FocusSession>> =
        dao.observeAllSessions().map { list -> list.map { it.toDomain() } }

    override fun observeRunningSessions(): Flow<List<FocusSession>> =
        dao.observeRunningSessions().map { list -> list.map { it.toDomain() } }

    override suspend fun getRunningSessions(): List<FocusSession> =
        dao.getRunningSessions().map { it.toDomain() }

    override suspend fun getActiveDebtSession(now: Instant): FocusSession? =
        dao.getActiveDebtSession(now.toEpochMilli())?.toDomain()

    override suspend fun getTodayCompletedFocusMinutes(
        startOfDay: Instant,
        endOfDay: Instant
    ): Int {
        val sessions = dao.getCompletedFocusSessionsBetween(
            startOfDay.toEpochMilli(),
            endOfDay.toEpochMilli()
        )
        return sessions.sumOf { entity ->
            val endMillis = entity.actualEndedAt ?: entity.plannedEndAt
            val durationMillis = (endMillis - entity.startedAt).coerceAtLeast(0L)
            (durationMillis / 60_000L).toInt()
        }
    }

    override suspend fun upsertSession(session: FocusSession) =
        dao.upsertSession(session.toEntity())

    override suspend fun updateSessionStatus(
        id: String,
        status: SessionStatus,
        actualEndedAt: Instant?,
        summaryNote: String?
    ) = dao.updateSessionStatus(id, status.name, actualEndedAt?.toEpochMilli(), summaryNote)

    override suspend fun completeExpiredSessions(now: Instant): Int =
        dao.completeExpiredSessions(now.toEpochMilli())
}

class RoomBlockAuditRepository(private val dao: BlockAuditLogDao) : BlockAuditRepository {
    override suspend fun insertLog(log: BlockAuditLog): Long =
        dao.insertLog(log.toEntity())

    override fun observeRecentLogs(limit: Int): Flow<List<BlockAuditLog>> =
        dao.observeRecentLogs(limit).map { list -> list.map { it.toDomain() } }

    override fun observeTodayCountForPackage(packageName: String, startOfDay: Instant): Flow<Int> =
        dao.observeTodayCountForPackage(packageName, startOfDay.toEpochMilli())

    override fun observeTotalCountForPackage(packageName: String): Flow<Int> =
        dao.observeTotalCountForPackage(packageName)

    override suspend fun getTodayCountForPackage(packageName: String, startOfDay: Instant): Int =
        dao.getTodayCountForPackage(packageName, startOfDay.toEpochMilli())

    override suspend fun getTotalCountForPackage(packageName: String): Int =
        dao.getTotalCountForPackage(packageName)

    override fun observeTotalDistractionsPreventedSince(startOfDay: Instant): Flow<Int> =
        dao.observeTotalDistractionsPreventedSince(startOfDay.toEpochMilli())

    override suspend fun deleteLogsOlderThan(cutoff: Instant): Int =
        dao.deleteLogsOlderThan(cutoff.toEpochMilli())
}

class RoomAppMetadataRepository(private val dao: AppMetadataDao) : AppMetadataRepository {
    override fun observeAllMetadata(): Flow<List<AppMetadata>> =
        dao.observeAllMetadata().map { list -> list.map { it.toDomain() } }

    override suspend fun getMetadataForPackage(packageName: String): AppMetadata? =
        dao.getMetadataForPackage(packageName)?.toDomain()

    override suspend fun upsertMetadata(metadata: AppMetadata) =
        dao.upsertMetadata(metadata.toEntity())
}

class RoomSavedContentRepository(private val dao: SavedContentDao) : SavedContentRepository {
    override fun observeAllSavedPosts(): Flow<List<SavedPost>> =
        dao.observeAllSavedContents().map { list -> list.map { it.toDomain() } }

    override suspend fun getSavedPostById(id: String): SavedPost? =
        dao.getSavedContentById(id)?.toDomain()

    override suspend fun getSavedPostBySourceUrl(sourceUrl: String): SavedPost? =
        dao.getSavedContentBySourceUrl(sourceUrl)?.toDomain()

    override suspend fun insertIfAbsent(post: SavedPost): com.example.focuslock.domain.repository.VaultSaveResult {
        val existing = dao.getSavedContentBySourceUrl(post.sourceUrl)
        if (existing != null) {
            return com.example.focuslock.domain.repository.VaultSaveResult.Duplicate(existing.toDomain())
        }
        return try {
            dao.insertSavedContent(post.toEntity())
            com.example.focuslock.domain.repository.VaultSaveResult.Saved(post)
        } catch (_: Throwable) {
            val fallbackExisting = dao.getSavedContentBySourceUrl(post.sourceUrl)?.toDomain() ?: post
            com.example.focuslock.domain.repository.VaultSaveResult.Duplicate(fallbackExisting)
        }
    }

    override suspend fun upsertSavedPost(post: SavedPost) =
        dao.upsertSavedContent(post.toEntity())

    override suspend fun updateTitleTagAndNote(
        id: String,
        userTitle: String,
        tag: String,
        personalNote: String?
    ) = dao.updateTitleTagAndNote(id, userTitle, tag, personalNote)

    override suspend fun updateReviewedStatus(id: String, isReviewed: Boolean) =
        dao.updateReviewedStatus(id, isReviewed)

    override suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean) =
        dao.updateFavoriteStatus(id, isFavorite)

    override suspend fun incrementOpenCountAndMarkReviewed(id: String): SavedPost? {
        dao.incrementOpenCountAndMarkReviewed(id)
        return dao.getSavedContentById(id)?.toDomain()
    }

    override suspend fun deleteSavedPostById(id: String) =
        dao.deleteSavedContentById(id)
}

class RoomDailyUsageRepository(
    private val dao: com.example.focuslock.data.local.dao.DailyUsageDao
) : com.example.focuslock.domain.repository.DailyUsageRepository {

    override suspend fun saveDayUsage(epochDay: Long, usageByPackageMs: Map<String, Long>) {
        val entities = if (usageByPackageMs.isEmpty()) {
            // Marqueur de journée évaluée avec 0 ms d'usage (distingue un vrai 0 d'un jour "indisponible")
            listOf(
                com.example.focuslock.data.local.entity.DailyUsageEntity(
                    epochDay = epochDay,
                    packageName = SENTINEL_ZERO_USAGE_PACKAGE,
                    totalMs = 0L
                )
            )
        } else {
            usageByPackageMs.map { (pkg, ms) ->
                com.example.focuslock.data.local.entity.DailyUsageEntity(
                    epochDay = epochDay,
                    packageName = pkg,
                    totalMs = ms
                )
            }
        }
        dao.upsertDailyUsages(entities)
    }

    override suspend fun getUsageBetweenDays(
        startEpochDay: Long,
        endEpochDay: Long
    ): List<com.example.focuslock.domain.repository.DailyAppUsageRecord> =
        dao.getUsageBetweenDays(startEpochDay, endEpochDay).map {
            com.example.focuslock.domain.repository.DailyAppUsageRecord(
                epochDay = it.epochDay,
                packageName = it.packageName,
                totalMs = it.totalMs
            )
        }

    override fun observeUsageBetweenDays(
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<List<com.example.focuslock.domain.repository.DailyAppUsageRecord>> =
        dao.observeUsageBetweenDays(startEpochDay, endEpochDay).map { list ->
            list.map {
                com.example.focuslock.domain.repository.DailyAppUsageRecord(
                    epochDay = it.epochDay,
                    packageName = it.packageName,
                    totalMs = it.totalMs
                )
            }
        }

    companion object {
        const val SENTINEL_ZERO_USAGE_PACKAGE = "__focuslock_day_recorded__"
    }
}