package com.example.focuslock.engine

import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.RuleType
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class QuotaRingStatus(
    val baseQuotaMinutes: Int,
    val effectiveQuotaMinutes: Int,
    val usedMinutes: Int,
    val remainingMinutes: Int,
    val malusReductionPercent: Int,
    val usedFraction: Float
)

object RuleFormatterAndDetoxCalculator {

    private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * 3.16 : Formateur pur du résumé horaire en police monospace pour les cartes de règles.
     */
    fun formatRuleSummary(
        rule: BlockRule,
        now: Instant,
        zoneId: ZoneId
    ): String {
        val today = now.atZone(zoneId).toLocalDate()
        return when (rule.ruleType) {
            RuleType.SCHEDULE, RuleType.ROUTINE -> {
                val start = formatMinutesHHMM(rule.startTimeMinutes ?: 0)
                val end = formatMinutesHHMM(rule.endTimeMinutes ?: 0)
                val overnightSuffix = if ((rule.endTimeMinutes ?: 0) < (rule.startTimeMinutes ?: 0)) " (+1j)" else ""
                val days = formatDaysBitmask(rule.activeDaysBitmask)
                "$start–$end$overnightSuffix • $days"
            }

            RuleType.DAILY_QUOTA -> {
                val q = rule.dailyQuotaMinutes ?: 0
                val days = formatDaysBitmask(rule.activeDaysBitmask)
                "Quota ${q}m/j • $days"
            }

            RuleType.ALTERNATING_DETOX -> {
                val interval = rule.cycleIntervalDays ?: 2
                val reopenDate = nextReopenDate(rule, today)
                val cycleLabel = when {
                    interval == 2 -> "Cycle 1j/2"
                    interval == 7 && isAnchoredOnSaturday(rule.cycleStartEpochDay) -> "Samedi uniq."
                    else -> "Cycle 1j/$interval"
                }
                if (reopenDate != null && reopenDate.isAfter(today)) {
                    "$cycleLabel • Verrouillé jusqu'au ${reopenDate.format(shortDateFormatter)}"
                } else {
                    "$cycleLabel • Jour ON (Accès autorisé)"
                }
            }

            RuleType.SABBATICAL -> {
                val until = rule.pauseUntil
                if (until != null && now.isBefore(until)) {
                    val dateStr = until.atZone(zoneId).toLocalDate().format(shortDateFormatter)
                    val countdown = formatSabbaticalCountdown(until, now)
                    "Jusqu'au $dateStr ($countdown)"
                } else {
                    "Pause sabbatique terminée"
                }
            }
        }
    }

    /**
     * 3.19 : Fonction pure nextReopenDate.
     * - Si [today] est un jour ON (ou avant le début du cycle), retourne [today].
     * - Si [today] est un jour OFF, retourne le prochain LocalDate où (epochDay - startEpochDay) % interval == 0.
     * - Si cycleIntervalDays <= 0 ou cycleStartEpochDay == null, retourne null.
     */
    fun nextReopenDate(
        rule: BlockRule,
        today: LocalDate
    ): LocalDate? {
        if (rule.ruleType != RuleType.ALTERNATING_DETOX) return null
        val interval = rule.cycleIntervalDays ?: return null
        val startEpochDay = rule.cycleStartEpochDay ?: return null
        if (interval <= 0) return null

        val todayEpoch = today.toEpochDay()
        if (todayEpoch < startEpochDay) return today

        val elapsed = todayEpoch - startEpochDay
        val remainder = elapsed % interval
        return if (remainder == 0L) {
            today
        } else {
            val daysUntilNextOn = interval - remainder
            today.plusDays(daysUntilNextOn)
        }
    }

    /**
     * 3.19 : Calcule le cycleStartEpochDay aligné sur le samedi précédent ou courant
     * pour le préréglage « Uniquement le samedi » (cycle de 7 jours démarrant un samedi).
     */
    fun saturdayOnlyCycleStartEpochDay(referenceDate: LocalDate): Long {
        val saturday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        return saturday.toEpochDay()
    }

    /**
     * 3.18 : Calcule l'état temps réel du ring-mini de quota journalier (avec pool partagé et malus).
     */
    fun computeQuotaRingStatus(
        baseQuotaMinutes: Int,
        quotaMalusReductionPercent: Int,
        usedMinutes: Int
    ): QuotaRingStatus {
        val safeBase = baseQuotaMinutes.coerceAtLeast(0)
        val clampedMalus = quotaMalusReductionPercent.coerceIn(0, 100)
        val effectiveQuota = (safeBase * (100 - clampedMalus)) / 100
        val safeUsed = usedMinutes.coerceAtLeast(0)
        val remaining = (effectiveQuota - safeUsed).coerceAtLeast(0)

        val usedFraction = when {
            effectiveQuota <= 0 -> 1f
            else -> (safeUsed.toFloat() / effectiveQuota.toFloat()).coerceIn(0f, 1f)
        }

        return QuotaRingStatus(
            baseQuotaMinutes = safeBase,
            effectiveQuotaMinutes = effectiveQuota,
            usedMinutes = safeUsed,
            remainingMinutes = remaining,
            malusReductionPercent = clampedMalus,
            usedFraction = usedFraction
        )
    }

    /**
     * 3.20 : Compte à rebours lisible pour la carte de Pause Sabbatique.
     */
    fun formatSabbaticalCountdown(pauseUntil: Instant, now: Instant): String {
        if (!now.isBefore(pauseUntil)) return "Expiré"
        val duration = Duration.between(now, pauseUntil)
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val mins = duration.toMinutes() % 60
        return when {
            days > 0 -> "reste ${days}j ${hours}h"
            hours > 0 -> "reste ${hours}h ${mins}m"
            else -> "reste ${mins.coerceAtLeast(1)}m"
        }
    }

    /**
     * 3.20 : Texte de la pilule supérieure de l'Overlay pour une Pause Sabbatique.
     */
    fun formatSabbaticalOverlayPill(pauseUntil: Instant, zoneId: ZoneId): String {
        val dateStr = pauseUntil.atZone(zoneId).toLocalDate().format(shortDateFormatter)
        return "PAUSE SABBATIQUE • JUSQU'AU $dateStr"
    }

    fun formatDaysBitmask(bitmask: Int): String {
        return when (bitmask and DaysBitmask.ALL_DAYS) {
            DaysBitmask.ALL_DAYS -> "Tous les jours"
            DaysBitmask.WEEKDAYS -> "Lun–Ven"
            DaysBitmask.WEEKEND -> "Sam–Dim"
            else -> {
                val labels = listOf(
                    DayOfWeek.MONDAY to "L",
                    DayOfWeek.TUESDAY to "Ma",
                    DayOfWeek.WEDNESDAY to "Me",
                    DayOfWeek.THURSDAY to "J",
                    DayOfWeek.FRIDAY to "V",
                    DayOfWeek.SATURDAY to "S",
                    DayOfWeek.SUNDAY to "D"
                )
                labels.filter { (day, _) -> DaysBitmask.isDayActive(bitmask, day) }
                    .joinToString(" ") { it.second }
            }
            }
    }

    fun formatMinutesHHMM(minutesOfDay: Int): String {
        val h = (minutesOfDay / 60).coerceIn(0, 23)
        val m = (minutesOfDay % 60).coerceIn(0, 59)
        return String.format("%02d:%02d", h, m)
    }

    private fun isAnchoredOnSaturday(epochDay: Long?): Boolean {
        if (epochDay == null) return false
        return LocalDate.ofEpochDay(epochDay).dayOfWeek == DayOfWeek.SATURDAY
    }
}