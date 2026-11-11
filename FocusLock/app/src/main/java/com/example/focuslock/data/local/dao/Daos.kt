package com.example.focuslock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.focuslock.data.local.entity.AppMetadataEntity
import com.example.focuslock.data.local.entity.BlockAuditLogEntity
import com.example.focuslock.data.local.entity.FocusSessionEntity
import com.example.focuslock.data.local.entity.QuestTaskEntity
import com.example.focuslock.data.local.entity.RuleEntity
import com.example.focuslock.data.local.entity.SavedContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY title ASC")
    fun observeAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE isEnabled = 1")
    fun observeEnabledRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun getAllRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: String): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: RuleEntity)

    @Query("UPDATE rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)
}

@Dao
interface QuestTaskDao {
    @Query("SELECT * FROM quest_tasks ORDER BY dueAtTimestamp ASC")
    fun observeAllTasks(): Flow<List<QuestTaskEntity>>

    @Query("SELECT * FROM quest_tasks WHERE isRoutineChecklistItem = 1 AND parentRoutineId = :routineId")
    fun observeChecklistForRoutine(routineId: String): Flow<List<QuestTaskEntity>>

    @Query("SELECT * FROM quest_tasks")
    suspend fun getAllTasks(): List<QuestTaskEntity>

    @Query("SELECT * FROM quest_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): QuestTaskEntity?

    @Query(
        """
        SELECT * FROM quest_tasks 
        WHERE isRoutineChecklistItem = 0 
          AND dueAtTimestamp IS NOT NULL 
          AND dueAtTimestamp >= :startOfDayMillis 
          AND dueAtTimestamp < :endOfDayMillis
        ORDER BY dueAtTimestamp ASC
        """
    )
    suspend fun getTasksDueBetween(startOfDayMillis: Long, endOfDayMillis: Long): List<QuestTaskEntity>

    @Query(
        """
        SELECT * FROM quest_tasks 
        WHERE isRoutineChecklistItem = 0 
          AND penaltyApplied = 0 
          AND dueAtTimestamp IS NOT NULL 
          AND (
              (completedAtTimestamp IS NULL AND dueAtTimestamp < :nowMillis)
              OR (completedAtTimestamp IS NOT NULL AND completedAtTimestamp > dueAtTimestamp)
          )
        ORDER BY dueAtTimestamp ASC
        """
    )
    suspend fun getOverdueUnpenalizedTasks(nowMillis: Long): List<QuestTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: QuestTaskEntity)

    @Query("UPDATE quest_tasks SET completedAtTimestamp = :completedAtTimestamp WHERE id = :id")
    suspend fun updateTaskCompletion(id: String, completedAtTimestamp: Long?)

    @Query("UPDATE quest_tasks SET penaltyApplied = 1 WHERE id IN (:ids)")
    suspend fun markTasksPenalized(ids: List<String>)

    @Query("DELETE FROM quest_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE status = 'RUNNING' ORDER BY startedAt DESC")
    fun observeRunningSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE status = 'RUNNING'")
    suspend fun getRunningSessions(): List<FocusSessionEntity>

    @Query(
        """
        SELECT * FROM focus_sessions 
        WHERE status = 'RUNNING' 
          AND sessionType = 'FOCUS_DEBT_MALUS' 
          AND plannedEndAt > :nowMillis 
        ORDER BY plannedEndAt DESC 
        LIMIT 1
        """
    )
    suspend fun getActiveDebtSession(nowMillis: Long): FocusSessionEntity?

    @Query(
        """
        SELECT * FROM focus_sessions 
        WHERE status = 'COMPLETED' 
          AND sessionType != 'FOCUS_DEBT_MALUS'
          AND startedAt >= :startOfDayMillis 
          AND startedAt < :endOfDayMillis
        """
    )
    suspend fun getCompletedFocusSessionsBetween(
        startOfDayMillis: Long,
        endOfDayMillis: Long
    ): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: FocusSessionEntity)

    @Query(
        """
        UPDATE focus_sessions 
        SET status = :status, actualEndedAt = :actualEndedAt, summaryValidationNote = :summaryNote 
        WHERE id = :id
        """
    )
    suspend fun updateSessionStatus(
        id: String,
        status: String,
        actualEndedAt: Long?,
        summaryNote: String? = null
    )

    @Query(
        """
        UPDATE focus_sessions 
        SET status = 'COMPLETED', actualEndedAt = plannedEndAt 
        WHERE status = 'RUNNING' AND plannedEndAt <= :nowMillis
        """
    )
    suspend fun completeExpiredSessions(nowMillis: Long): Int
}

@Dao
interface BlockAuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BlockAuditLogEntity): Long

    @Query("SELECT * FROM block_audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 100): Flow<List<BlockAuditLogEntity>>

    @Query("SELECT COUNT(*) FROM block_audit_logs WHERE blockedPackageName = :packageName AND timestamp >= :startOfDayTimestamp")
    fun observeTodayCountForPackage(packageName: String, startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_audit_logs WHERE blockedPackageName = :packageName")
    fun observeTotalCountForPackage(packageName: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_audit_logs WHERE blockedPackageName = :packageName AND timestamp >= :startOfDayTimestamp")
    suspend fun getTodayCountForPackage(packageName: String, startOfDayTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM block_audit_logs WHERE blockedPackageName = :packageName")
    suspend fun getTotalCountForPackage(packageName: String): Int

    @Query("SELECT COUNT(*) FROM block_audit_logs WHERE timestamp >= :startOfDayTimestamp")
    fun observeTotalDistractionsPreventedSince(startOfDayTimestamp: Long): Flow<Int>
}

@Dao
interface AppMetadataDao {
    @Query("SELECT * FROM app_metadata ORDER BY packageName ASC")
    fun observeAllMetadata(): Flow<List<AppMetadataEntity>>

    @Query("SELECT * FROM app_metadata WHERE packageName = :packageName LIMIT 1")
    suspend fun getMetadataForPackage(packageName: String): AppMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: AppMetadataEntity)
}

@Dao
interface SavedContentDao {
    @Query("SELECT * FROM saved_contents ORDER BY savedAtTimestamp DESC")
    fun observeAllSavedContents(): Flow<List<SavedContentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedContent(content: SavedContentEntity)

    @Query("DELETE FROM saved_contents WHERE id = :id")
    suspend fun deleteSavedContentById(id: String)
}