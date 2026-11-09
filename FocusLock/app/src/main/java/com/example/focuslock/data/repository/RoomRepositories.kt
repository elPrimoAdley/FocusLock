package com.example.focuslock.data.repository

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

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

    override suspend fun upsertTask(task: QuestTask) =
        dao.upsertTask(task.toEntity())

    override suspend fun updateTaskCompletion(id: String, completedAt: Instant?) =
        dao.updateTaskCompletion(id, completedAt?.toEpochMilli())

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

    override suspend fun upsertSession(session: FocusSession) =
        dao.upsertSession(session.toEntity())

    override suspend fun updateSessionStatus(
        id: String,
        status: SessionStatus,
        actualEndedAt: Instant?,
        summaryNote: String?
    ) = dao.updateSessionStatus(id, status.name, actualEndedAt?.toEpochMilli(), summaryNote)
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

    override suspend fun upsertSavedPost(post: SavedPost) =
        dao.upsertSavedContent(post.toEntity())

    override suspend fun deleteSavedPostById(id: String) =
        dao.deleteSavedContentById(id)
}