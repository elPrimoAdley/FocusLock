package com.example.focuslock.engine

import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.EditPermission
import com.example.focuslock.domain.FrictionReason
import com.example.focuslock.domain.GamificationConstants
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RuleType
import java.time.Duration
import java.time.Instant

object EditGuard {

    /**
     * Évalue la modification d'une règle de blocage.
     */
    fun checkRuleEdit(
        oldRule: BlockRule,
        newRule: BlockRule,
        now: Instant
    ): EditPermission {
        // Si l'ancienne règle était déjà désactivée, toute configuration ou réactivation est permise
        if (!oldRule.isEnabled) {
            return EditPermission.Allowed
        }

        // 1. Désactiver une règle active -> Friction
        if (!newRule.isEnabled) {
            return EditPermission.RequiresFriction(FrictionReason.DISABLING_ACTIVE_RULE)
        }

        // 2. Retirer au moins une application cible -> Friction (en ajouter = durcir -> Allowed)
        if (!newRule.targetPackages.containsAll(oldRule.targetPackages)) {
            return EditPermission.RequiresFriction(FrictionReason.REMOVING_TARGET_PACKAGES)
        }

        // 3. Retirer au moins un jour actif du bitmask -> Friction (en ajouter = durcir -> Allowed)
        val removedDaysMask = oldRule.activeDaysBitmask and newRule.activeDaysBitmask.inv()
        if (removedDaysMask != 0) {
            return EditPermission.RequiresFriction(FrictionReason.REDUCING_ACTIVE_DAYS)
        }

        // 4. Vérifications spécifiques selon le type de règle
        return when (oldRule.ruleType) {
            RuleType.DAILY_QUOTA -> {
                val oldQuota = oldRule.dailyQuotaMinutes ?: return EditPermission.Allowed
                val newQuota = newRule.dailyQuotaMinutes
                    ?: return EditPermission.RequiresFriction(FrictionReason.LOOSENING_DAILY_QUOTA)
                // Réduire le quota (ex. 30 min -> 15 min) = Durcir -> Allowed
                // Augmenter le quota (ex. 30 min -> 60 min) = Assouplir -> RequiresFriction
                if (newQuota > oldQuota) {
                    EditPermission.RequiresFriction(FrictionReason.LOOSENING_DAILY_QUOTA)
                } else {
                    EditPermission.Allowed
                }
            }

            RuleType.SCHEDULE, RuleType.ROUTINE -> {
                val oldStart = oldRule.startTimeMinutes ?: return EditPermission.Allowed
                val oldEnd = oldRule.endTimeMinutes ?: return EditPermission.Allowed
                val newStart = newRule.startTimeMinutes
                    ?: return EditPermission.RequiresFriction(FrictionReason.SHORTENING_SCHEDULE_WINDOW)
                val newEnd = newRule.endTimeMinutes
                    ?: return EditPermission.RequiresFriction(FrictionReason.SHORTENING_SCHEDULE_WINDOW)

                val oldDuration = computeWindowDurationMinutes(oldStart, oldEnd)
                val newDuration = computeWindowDurationMinutes(newStart, newEnd)

                // Raccourcir la durée ou retarder le début / avancer la fin = Assouplir -> Friction
                val startsLater = isStartPushedLater(oldStart, newStart, oldEnd)
                val endsEarlier = isEndPulledEarlier(oldEnd, newEnd, oldStart)
                if (newDuration < oldDuration || startsLater || endsEarlier) {
                    EditPermission.RequiresFriction(FrictionReason.SHORTENING_SCHEDULE_WINDOW)
                } else {
                    EditPermission.Allowed
                }
            }

            RuleType.SABBATICAL -> {
                val oldPauseUntil = oldRule.pauseUntil ?: return EditPermission.Allowed
                // Si la pause sabbatique est déjà expirée, libre de la reconfigurer
                if (!now.isBefore(oldPauseUntil)) return EditPermission.Allowed

                val newPauseUntil = newRule.pauseUntil
                    ?: return EditPermission.RequiresFriction(FrictionReason.SHORTENING_SABBATICAL)

                if (newPauseUntil.isBefore(oldPauseUntil)) {
                    EditPermission.RequiresFriction(FrictionReason.SHORTENING_SABBATICAL)
                } else {
                    EditPermission.Allowed
                }
            }

            RuleType.ALTERNATING_DETOX -> {
                val oldInterval = oldRule.cycleIntervalDays ?: return EditPermission.Allowed
                val newInterval = newRule.cycleIntervalDays
                    ?: return EditPermission.RequiresFriction(FrictionReason.LOOSENING_DETOX_CYCLE)
                val startChanged = newRule.cycleStartEpochDay != oldRule.cycleStartEpochDay
                if (newInterval < oldInterval || startChanged) {
                    EditPermission.RequiresFriction(FrictionReason.LOOSENING_DETOX_CYCLE)
                } else {
                    EditPermission.Allowed
                }
            }
        }
    }

    /**
     * Évalue la suppression d'une règle.
     */
    fun checkRuleDeletion(rule: BlockRule): EditPermission {
        return if (rule.isEnabled) {
            EditPermission.RequiresFriction(FrictionReason.DELETING_ACTIVE_RULE)
        } else {
            EditPermission.Allowed
        }
    }

    /**
     * Évalue la suppression d'une tâche (Section 5 : P1 ou P2 engagée ne peut pas être supprimée sans PIN).
     */
    fun checkTaskDeletion(task: QuestTask): EditPermission {
        if (task.completedAt != null || task.isRoutineChecklistItem) {
            return EditPermission.Allowed
        }
        return when (task.priority) {
            QuestPriority.P1, QuestPriority.P2 ->
                EditPermission.RequiresFriction(FrictionReason.DELETING_ENGAGED_PRIORITY_TASK)
            QuestPriority.P3 -> EditPermission.Allowed
        }
    }

    /**
     * Évalue la modification d'une tâche (abaissement de priorité ou report d'échéance à < 2h).
     */
    fun checkTaskEdit(
        oldTask: QuestTask,
        newTask: QuestTask,
        now: Instant
    ): EditPermission {
        if (oldTask.completedAt != null || oldTask.isRoutineChecklistItem) {
            return EditPermission.Allowed
        }

        // 1. Abaisser la priorité d'une tâche P1/P2 (ex. P1 -> P2 ou P2 -> P3) -> Friction
        if (newTask.priority.xpReward < oldTask.priority.xpReward) {
            return EditPermission.RequiresFriction(FrictionReason.DOWNGRADING_TASK_PRIORITY)
        }

        // 2. Repousser ou supprimer l'échéance d'une tâche P1 ou P2 à moins de 2 heures du terme -> Friction
        if (oldTask.priority == QuestPriority.P1 || oldTask.priority == QuestPriority.P2) {
            val oldDue = oldTask.dueAt
            if (oldDue != null) {
                val timeUntilDue = Duration.between(now, oldDue)
                val isWithinFreezeWindow = timeUntilDue < GamificationConstants.QUEST_FREEZE_WINDOW
                val isDeadlinePostponedOrRemoved =
                    newTask.dueAt == null || newTask.dueAt.isAfter(oldDue)

                if (isWithinFreezeWindow && isDeadlinePostponedOrRemoved) {
                    return EditPermission.RequiresFriction(FrictionReason.POSTPONING_IMMINENT_DEADLINE)
                }
            }
        }

        return EditPermission.Allowed
    }

    private fun computeWindowDurationMinutes(start: Int, end: Int): Int {
        return if (start <= end) {
            end - start
        } else {
            (24 * 60 - start) + end
        }
    }

    private fun isStartPushedLater(oldStart: Int, newStart: Int, oldEnd: Int): Boolean {
        return if (oldStart <= oldEnd) {
            newStart > oldStart
        } else {
            newStart > oldStart
        }
    }

    private fun isEndPulledEarlier(oldEnd: Int, newEnd: Int, oldStart: Int): Boolean {
        return if (oldStart <= oldEnd) {
            newEnd < oldEnd
        } else {
            newEnd < oldEnd
        }
    }
}