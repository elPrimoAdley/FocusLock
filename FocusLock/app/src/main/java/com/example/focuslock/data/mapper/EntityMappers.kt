package com.example.focuslock.data.mapper

import com.example.focuslock.data.local.FocusTypeConverters
import com.example.focuslock.data.local.entity.AppMetadataEntity
import com.example.focuslock.data.local.entity.BlockAuditLogEntity
import com.example.focuslock.data.local.entity.FocusSessionEntity
import com.example.focuslock.data.local.entity.QuestTaskEntity
import com.example.focuslock.data.local.entity.RuleEntity
import com.example.focuslock.data.local.entity.SavedContentEntity
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.domain.AppMetadata
import com.example.focuslock.domain.BlockAuditLog
import com.example.focuslock.domain.BlockReason
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RuleType
import com.example.focuslock.domain.SavedPost
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import java.time.Instant

// ==================== 1. BLOCK RULE <-> RULE ENTITY ====================

fun BlockRule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    title = title,
    ruleType = ruleType.name,
    targetPackagesJson = FocusTypeConverters.fromPackageSet(targetPackages),
    startTimeMinutes = startTimeMinutes,
    endTimeMinutes = endTimeMinutes,
    activeDaysBitmask = activeDaysBitmask,
    dailyQuotaMinutes = dailyQuotaMinutes,
    cycleIntervalDays = cycleIntervalDays,
    cycleStartEpochDay = cycleStartEpochDay,
    pauseUntilTimestamp = pauseUntil?.toEpochMilli(),
    isStrictLocked = isStrictLocked,
    isEnabled = isEnabled,
    customBlockMessage = customBlockMessage
)

fun RuleEntity.toDomain(): BlockRule = BlockRule(
    id = id,
    title = title,
    ruleType = runCatching { RuleType.valueOf(ruleType) }.getOrDefault(RuleType.SCHEDULE),
    targetPackages = FocusTypeConverters.toPackageSet(targetPackagesJson),
    isEnabled = isEnabled,
    isStrictLocked = isStrictLocked,
    activeDaysBitmask = activeDaysBitmask,
    startTimeMinutes = startTimeMinutes,
    endTimeMinutes = endTimeMinutes,
    dailyQuotaMinutes = dailyQuotaMinutes,
    cycleIntervalDays = cycleIntervalDays,
    cycleStartEpochDay = cycleStartEpochDay,
    pauseUntil = pauseUntilTimestamp?.let(Instant::ofEpochMilli),
    customBlockMessage = customBlockMessage
)

// ==================== 2. QUEST TASK <-> QUEST TASK ENTITY ====================

fun QuestTask.toEntity(): QuestTaskEntity = QuestTaskEntity(
    id = id,
    title = title,
    description = description,
    priority = priority.dbCode,
    xpValue = xpValue,
    dueAtTimestamp = dueAt?.toEpochMilli(),
    completedAtTimestamp = completedAt?.toEpochMilli(),
    isRoutineChecklistItem = isRoutineChecklistItem,
    parentRoutineId = parentRoutineId,
    penaltyApplied = penaltyApplied
)

fun QuestTaskEntity.toDomain(): QuestTask = QuestTask(
    id = id,
    title = title,
    description = description,
    priority = QuestPriority.fromDbCode(priority),
    xpValue = xpValue,
    dueAt = dueAtTimestamp?.let(Instant::ofEpochMilli),
    completedAt = completedAtTimestamp?.let(Instant::ofEpochMilli),
    isRoutineChecklistItem = isRoutineChecklistItem,
    parentRoutineId = parentRoutineId,
    penaltyApplied = penaltyApplied
)

// ==================== 3. FOCUS SESSION <-> FOCUS SESSION ENTITY ====================

fun FocusSession.toEntity(): FocusSessionEntity = FocusSessionEntity(
    id = id,
    sessionType = sessionType.name,
    label = label,
    startedAt = startedAt.toEpochMilli(),
    plannedEndAt = plannedEndAt.toEpochMilli(),
    actualEndedAt = actualEndedAt?.toEpochMilli(),
    whitelistedPackagesJson = FocusTypeConverters.fromPackageSet(whitelistedPackages),
    summaryValidationNote = summaryValidationNote,
    status = status.name
)

fun FocusSessionEntity.toDomain(): FocusSession = FocusSession(
    id = id,
    sessionType = runCatching { SessionType.valueOf(sessionType) }.getOrDefault(SessionType.DEEP_WORK),
    label = label,
    startedAt = Instant.ofEpochMilli(startedAt),
    plannedEndAt = Instant.ofEpochMilli(plannedEndAt),
    actualEndedAt = actualEndedAt?.let(Instant::ofEpochMilli),
    whitelistedPackages = FocusTypeConverters.toPackageSet(whitelistedPackagesJson),
    summaryValidationNote = summaryValidationNote,
    status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.RUNNING)
)

// ==================== 4. SAVED POST <-> SAVED CONTENT ENTITY ====================

fun SavedPost.toEntity(): SavedContentEntity = SavedContentEntity(
    id = id,
    sourceUrl = sourceUrl,
    extractedCaption = extractedCaption,
    userTitle = userTitle,
    personalNote = personalNote,
    tag = tag,
    savedAtTimestamp = savedAt.toEpochMilli(),
    isReviewed = isReviewed,
    isFavorite = isFavorite,
    openCount = openCount,
    thumbnailLocalPath = thumbnailLocalPath,
    mediaLocalPath = mediaLocalPath
)

fun SavedContentEntity.toDomain(): SavedPost = SavedPost(
    id = id,
    sourceUrl = sourceUrl,
    extractedCaption = extractedCaption,
    userTitle = userTitle,
    personalNote = personalNote,
    tag = tag,
    savedAt = Instant.ofEpochMilli(savedAtTimestamp),
    isReviewed = isReviewed,
    isFavorite = isFavorite,
    openCount = openCount,
    thumbnailLocalPath = thumbnailLocalPath,
    mediaLocalPath = mediaLocalPath
)

// ==================== 5. BLOCK AUDIT LOG <-> BLOCK AUDIT LOG ENTITY ====================

fun BlockAuditLog.toEntity(): BlockAuditLogEntity = BlockAuditLogEntity(
    id = id,
    timestamp = timestamp.toEpochMilli(),
    blockedPackageName = blockedPackageName,
    triggerRuleId = triggerRuleId,
    reasonCode = reasonCode.name,
    explanationText = explanationText
)

fun BlockAuditLogEntity.toDomain(): BlockAuditLog = BlockAuditLog(
    id = id,
    timestamp = Instant.ofEpochMilli(timestamp),
    blockedPackageName = blockedPackageName,
    triggerRuleId = triggerRuleId,
    reasonCode = runCatching { BlockReason.valueOf(reasonCode) }
        .getOrDefault(BlockReason.SCHEDULE_OR_QUOTA),
    explanationText = explanationText
)

// ==================== 6. APP METADATA <-> APP METADATA ENTITY ====================

fun AppMetadata.toEntity(): AppMetadataEntity = AppMetadataEntity(
    packageName = packageName,
    category = category.name,
    isAlwaysWhitelisted = isAlwaysWhitelisted
)

fun AppMetadataEntity.toDomain(): AppMetadata = AppMetadata(
    packageName = packageName,
    category = runCatching { AppCategory.valueOf(category) }.getOrDefault(AppCategory.NEUTRAL),
    isAlwaysWhitelisted = isAlwaysWhitelisted
)