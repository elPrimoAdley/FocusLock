package com.example.focuslock.engine

import com.example.focuslock.domain.BlockDecision
import com.example.focuslock.domain.BlockReason
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.RuleType
import com.example.focuslock.domain.SecurityState
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime

object RuleEngine {

    /**
     * Fonction pure d'évaluation par ordre strict de priorité (Niveaux 1 à 8).
     * Zéro dépendance Android, zéro effet de bord, horloge injectable.
     */
    fun evaluate(
        clock: Clock,
        foregroundPackage: String,
        rules: List<BlockRule> = emptyList(),
        tasks: List<QuestTask> = emptyList(),
        sessions: List<FocusSession> = emptyList(),
        todayUsageMinutes: Map<String, Int> = emptyMap(),
        securityState: SecurityState = SecurityState()
    ): BlockDecision {
        val now: Instant = clock.instant()
        val zonedNow: ZonedDateTime = now.atZone(clock.zone)

        // ==================== NIVEAU 1 : LISTE BLANCHE MATÉRIELLE & APPEL EN COURS ====================
        if (securityState.isCallInProgress || foregroundPackage in securityState.hardwareWhitelistedPackages) {
            return BlockDecision.Allow
        }

        // ==================== NIVEAU 2 : DÉVERROUILLAGE TEMPORAIRE D'URGENCE ====================
        val unlockUntil = securityState.emergencyUnlockUntil
        if (unlockUntil != null && now.isBefore(unlockUntil)) {
            return BlockDecision.Allow
        }

        // ==================== NIVEAU 3 : SESSION DE FOCUS / CONTRAT D'APPRENTISSAGE ACTIF ====================
        val activeFocusSession = sessions.firstOrNull { session ->
            session.status == SessionStatus.RUNNING &&
                session.sessionType != SessionType.FOCUS_DEBT_MALUS &&
                !now.isBefore(session.startedAt) &&
                now.isBefore(session.plannedEndAt)
        }
        if (activeFocusSession != null) {
            return if (foregroundPackage in activeFocusSession.whitelistedPackages) {
                BlockDecision.Allow
            } else {
                BlockDecision.Block(
                    reason = BlockReason.ACTIVE_FOCUS_SESSION,
                    ruleId = activeFocusSession.id,
                    message = activeFocusSession.label
                )
            }
        }

        // ==================== NIVEAU 4 : DETTE DE FOCUS (MALUS AUTOMATIQUE) ====================
        val activeDebtSession = sessions.firstOrNull { session ->
            session.status == SessionStatus.RUNNING &&
                session.sessionType == SessionType.FOCUS_DEBT_MALUS &&
                !now.isBefore(session.startedAt) &&
                now.isBefore(session.plannedEndAt)
        }
        if (activeDebtSession != null) {
            if (foregroundPackage !in activeDebtSession.whitelistedPackages) {
                return BlockDecision.Block(
                    reason = BlockReason.FOCUS_DEBT_PENALTY,
                    ruleId = activeDebtSession.id,
                    message = activeDebtSession.label
                )
            }
        }

        // Filtre préalable : règles activées qui ciblent le package au premier plan
        val applicableRules = rules.filter { rule ->
            rule.isEnabled && foregroundPackage in rule.targetPackages
        }
        if (applicableRules.isEmpty()) {
            return BlockDecision.Allow
        }

        // ==================== NIVEAU 5 : ROUTINE OBLIGATOIRE (EX. MORNING ROUTINE) ====================
        val currentMinuteOfDay = zonedNow.hour * 60 + zonedNow.minute
        for (rule in applicableRules.filter { it.ruleType == RuleType.ROUTINE }) {
            if (isWithinTimeWindow(zonedNow, currentMinuteOfDay, rule)) {
                val checklist = tasks.filter {
                    it.isRoutineChecklistItem && it.parentRoutineId == rule.id
                }
                if (! com.example.focuslock.domain.isRoutineChecklistCompleteToday(checklist, now, clock.zone)) {
                    return BlockDecision.Block(
                        reason = BlockReason.MORNING_ROUTINE,
                        ruleId = rule.id,
                        message = rule.customBlockMessage ?: rule.title
                    )
                }
            }
        }

        // ==================== NIVEAU 6 : PAUSE SABBATIQUE & DÉTOX INTERMITTENTE ====================
        val todayEpochDay = zonedNow.toLocalDate().toEpochDay()
        for (rule in applicableRules) {
            when (rule.ruleType) {
                RuleType.SABBATICAL -> {
                    val pauseUntil = rule.pauseUntil
                    if (pauseUntil != null && now.isBefore(pauseUntil)) {
                        return BlockDecision.Block(
                            reason = BlockReason.SABBATICAL_OR_DETOX,
                            ruleId = rule.id,
                            message = rule.customBlockMessage ?: rule.title
                        )
                    }
                }
                RuleType.ALTERNATING_DETOX -> {
                    val interval = rule.cycleIntervalDays
                    val startEpochDay = rule.cycleStartEpochDay
                    // Protection contre cycleIntervalDays <= 0 (division par zéro) et dates dans le futur
                    if (interval != null && interval > 0 && startEpochDay != null && todayEpochDay >= startEpochDay) {
                        val daysElapsed = todayEpochDay - startEpochDay
                        // Jour 0 = ON (Autorisé), Jours où modulo != 0 = OFF (Bloqué)
                        if (daysElapsed % interval != 0L) {
                            return BlockDecision.Block(
                                reason = BlockReason.SABBATICAL_OR_DETOX,
                                ruleId = rule.id,
                                message = rule.customBlockMessage ?: rule.title
                            )
                        }
                    }
                }
                else -> Unit
            }
        }

        // ==================== NIVEAU 7A : PLAGES HORAIRES PROGRAMMÉES (SCHEDULES) ====================
        for (rule in applicableRules.filter { it.ruleType == RuleType.SCHEDULE }) {
            if (isWithinTimeWindow(zonedNow, currentMinuteOfDay, rule)) {
                return BlockDecision.Block(
                    reason = BlockReason.SCHEDULE_OR_QUOTA,
                    ruleId = rule.id,
                    message = rule.customBlockMessage ?: rule.title
                )
            }
        }

        // ==================== NIVEAU 7B : QUOTAS JOURNALIERS (POOL PARTAGÉ + MALUS) ====================
        val clampedMalusPercent = securityState.quotaMalusReductionPercent.coerceIn(0, 100)
        for (rule in applicableRules.filter { it.ruleType == RuleType.DAILY_QUOTA }) {
            if (!DaysBitmask.isDayActive(rule.activeDaysBitmask, zonedNow.dayOfWeek)) {
                continue
            }
            val baseQuota = rule.dailyQuotaMinutes ?: continue
            if (baseQuota < 0) continue

            // Arrondi documenté : troncature entière vers zéro (plancher entier)
            val effectiveQuota = (baseQuota * (100 - clampedMalusPercent)) / 100

            // Pool partagé : somme de l'usage du jour de toutes les apps du groupe
            val sharedPoolUsage = rule.targetPackages.sumOf { pkg ->
                (todayUsageMinutes[pkg] ?: 0).coerceAtLeast(0)
            }

            if (sharedPoolUsage >= effectiveQuota) {
                return BlockDecision.Block(
                    reason = BlockReason.SCHEDULE_OR_QUOTA,
                    ruleId = rule.id,
                    message = rule.customBlockMessage ?: rule.title
                )
            }
        }

        // ==================== NIVEAU 8 : PAR DÉFAUT -> ALLOW ====================
        return BlockDecision.Allow
    }

    /**
     * Vérifie si l'instant courant tombe dans la plage [startTimeMinutes, endTimeMinutes).
     * Convention pour les plages qui traversent minuit (end < start) :
     * - Avant minuit (current >= start) : le bitmask est évalué sur le jour courant.
     * - Après minuit (current < end) : le bitmask est évalué sur le jour précédent (jour de début de la plage).
     */
    private fun isWithinTimeWindow(
        zonedNow: ZonedDateTime,
        currentMinuteOfDay: Int,
        rule: BlockRule
    ): Boolean {
        val start = rule.startTimeMinutes ?: return false
        val end = rule.endTimeMinutes ?: return false
        if (start == end) return false

        return if (start < end) {
            // Plage diurne classique (ex. 07:00 - 07:30 ou 09:00 - 17:00)
            DaysBitmask.isDayActive(rule.activeDaysBitmask, zonedNow.dayOfWeek) &&
                currentMinuteOfDay in start until end
        } else {
            // Plage nocturne traversant minuit (ex. 22:30 - 07:00)
            if (currentMinuteOfDay >= start) {
                DaysBitmask.isDayActive(rule.activeDaysBitmask, zonedNow.dayOfWeek)
            } else if (currentMinuteOfDay < end) {
                val previousDay = zonedNow.dayOfWeek.minus(1)
                DaysBitmask.isDayActive(rule.activeDaysBitmask, previousDay)
            } else {
                false
            }
        }
    }
}