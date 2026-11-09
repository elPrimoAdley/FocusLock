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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngineTest {

    private val defaultZone: ZoneId = ZoneId.of("America/Toronto")
    private val instagram = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"
    private val spotify = "com.spotify.music"

    /**
     * Helper de test 2.4 : construit une horloge figée à la seconde près dans le fuseau donné.
     */
    private fun fixedClock(
        isoLocalDateTime: String,
        zoneId: ZoneId = defaultZone
    ): Clock {
        val ldt = LocalDateTime.parse(isoLocalDateTime)
        val instant = ldt.atZone(zoneId).toInstant()
        return Clock.fixed(instant, zoneId)
    }

    // =========================================================================
    // 2.4 CONTRAT DU MOTEUR
    // =========================================================================

    @Test
    fun `2_4 no rules and no sessions returns Allow`() {
        val clock = fixedClock("2026-11-09T10:00:00") // Lundi
        val decision = RuleEngine.evaluate(
            clock = clock,
            foregroundPackage = instagram
        )
        assertEquals(BlockDecision.Allow, decision)
    }

    // =========================================================================
    // 2.5 NIVEAUX 1 ET 2 : LISTE BLANCHE MATÉRIELLE + URGENCE
    // =========================================================================

    @Test
    fun `2_5 hardware whitelisted packages (Phone, SystemUI, Keyboard) always return Allow even under active rule`() {
        val clock = fixedClock("2026-11-09T10:00:00")
        val strictSchedule = BlockRule(
            id = "r-all",
            title = "Blocage Total",
            ruleType = RuleType.SCHEDULE,
            targetPackages = setOf(
                "com.samsung.android.dialer",
                "com.android.systemui",
                "com.samsung.android.honeyboard",
                instagram
            ),
            startTimeMinutes = 0,
            endTimeMinutes = 24 * 60
        )

        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(clock, "com.samsung.android.dialer", rules = listOf(strictSchedule))
        )
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(clock, "com.android.systemui", rules = listOf(strictSchedule))
        )
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(clock, "com.samsung.android.honeyboard", rules = listOf(strictSchedule))
        )
    }

    @Test
    fun `2_5 call in progress always returns Allow even during active focus session`() {
        val clock = fixedClock("2026-11-09T10:00:00")
        val session = FocusSession(
            id = "s1",
            sessionType = SessionType.ACTIVE_LEARNING,
            label = "Espagnol",
            startedAt = clock.instant().minusSeconds(600),
            plannedEndAt = clock.instant().plusSeconds(600)
        )
        val decision = RuleEngine.evaluate(
            clock = clock,
            foregroundPackage = instagram,
            sessions = listOf(session),
            securityState = SecurityState(isCallInProgress = true)
        )
        assertEquals(BlockDecision.Allow, decision)
    }

    @Test
    fun `2_5 emergency unlock valid returns Allow and expires at the exact second`() {
        val unlockExpiration = fixedClock("2026-11-09T10:15:00").instant()
        val session = FocusSession(
            id = "s1",
            sessionType = SessionType.ACTIVE_LEARNING,
            label = "Étude",
            startedAt = fixedClock("2026-11-09T10:00:00").instant(),
            plannedEndAt = fixedClock("2026-11-09T11:00:00").instant()
        )
        val securityState = SecurityState(emergencyUnlockUntil = unlockExpiration)

        // 1 seconde avant l'expiration -> Allow
        val clockOneSecBefore = fixedClock("2026-11-09T10:14:59")
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(clockOneSecBefore, instagram, sessions = listOf(session), securityState = securityState)
        )

        // À la seconde pile d'expiration (10:15:00) -> ne passe plus (Block)
        val clockExactExpiration = fixedClock("2026-11-09T10:15:00")
        val expiredDecision = RuleEngine.evaluate(
            clockExactExpiration,
            instagram,
            sessions = listOf(session),
            securityState = securityState
        )
        assertEquals(
            BlockDecision.Block(BlockReason.ACTIVE_FOCUS_SESSION, "s1", "Étude"),
            expiredDecision
        )
    }

    // =========================================================================
    // 2.6 NIVEAUX 3 ET 4 : SESSION DE FOCUS + DETTE DE FOCUS
    // =========================================================================

    @Test
    fun `2_6 running focus session blocks non-whitelisted app and allows session-whitelisted app`() {
        val clock = fixedClock("2026-11-09T14:00:00")
        val session = FocusSession(
            id = "focus-1",
            sessionType = SessionType.ACTIVE_LEARNING,
            label = "Espagnol — Tablette",
            startedAt = fixedClock("2026-11-09T13:30:00").instant(),
            plannedEndAt = fixedClock("2026-11-09T14:30:00").instant(),
            whitelistedPackages = setOf(spotify),
            status = SessionStatus.RUNNING
        )

        // Package hors liste blanche -> Block(ACTIVE_FOCUS_SESSION)
        assertEquals(
            BlockDecision.Block(BlockReason.ACTIVE_FOCUS_SESSION, "focus-1", "Espagnol — Tablette"),
            RuleEngine.evaluate(clock, instagram, sessions = listOf(session))
        )

        // Package en liste blanche de session -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(clock, spotify, sessions = listOf(session))
        )
    }

    @Test
    fun `2_6 completed or broken sessions are ignored and session ends at plannedEndAt across midnight`() {
        // Session qui commence à 23:30 et finit à 00:30 le lendemain (ne s'arrête pas à minuit)
        val start = fixedClock("2026-11-09T23:30:00").instant()
        val plannedEnd = fixedClock("2026-11-10T00:30:00").instant()

        val runningSession = FocusSession(
            id = "night-session",
            sessionType = SessionType.DEEP_WORK,
            label = "Travail Profond",
            startedAt = start,
            plannedEndAt = plannedEnd,
            status = SessionStatus.RUNNING
        )
        val completedSession = runningSession.copy(id = "s-done", status = SessionStatus.COMPLETED)
        val brokenSession = runningSession.copy(id = "s-broken", status = SessionStatus.BROKEN_BY_EMERGENCY)

        // Sessions COMPLETED et BROKEN_BY_EMERGENCY ignorées
        val clockDuring = fixedClock("2026-11-10T00:10:00")
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(clockDuring, instagram, sessions = listOf(completedSession, brokenSession))
        )

        // Session RUNNING toujours active après minuit (00:10)
        assertEquals(
            BlockDecision.Block(BlockReason.ACTIVE_FOCUS_SESSION, "night-session", "Travail Profond"),
            RuleEngine.evaluate(clockDuring, instagram, sessions = listOf(runningSession))
        )

        // À plannedEndAt pile (00:30:00) -> Allow
        val clockAtEnd = fixedClock("2026-11-10T00:30:00")
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(clockAtEnd, instagram, sessions = listOf(runningSession))
        )
    }

    @Test
    fun `2_6 running FOCUS_DEBT_MALUS session blocks with FOCUS_DEBT_PENALTY`() {
        val clock = fixedClock("2026-11-09T18:00:00")
        val debtSession = FocusSession(
            id = "debt-1",
            sessionType = SessionType.FOCUS_DEBT_MALUS,
            label = "Dette P1 : 30 min de blocage strict",
            startedAt = fixedClock("2026-11-09T17:45:00").instant(),
            plannedEndAt = fixedClock("2026-11-09T18:15:00").instant(),
            status = SessionStatus.RUNNING
        )

        val decision = RuleEngine.evaluate(clock, instagram, sessions = listOf(debtSession))
        assertEquals(
            BlockDecision.Block(BlockReason.FOCUS_DEBT_PENALTY, "debt-1", "Dette P1 : 30 min de blocage strict"),
            decision
        )
    }

    // =========================================================================
    // 2.7 NIVEAU 5 : ROUTINE OBLIGATOIRE (MORNING ROUTINE)
    // =========================================================================

    @Test
    fun `2_7 morning routine 07h00-07h30 blocks when checklist incomplete and allows when complete or at 07h30 sharp`() {
        val routineRule = BlockRule(
            id = "routine-morning",
            title = "Morning Routine",
            ruleType = RuleType.ROUTINE,
            targetPackages = setOf(instagram),
            activeDaysBitmask = DaysBitmask.WEEKDAYS, // Lundi à Vendredi
            startTimeMinutes = 7 * 60,      // 07:00 (420)
            endTimeMinutes = 7 * 60 + 30    // 07:30 (450)
        )

        val task1 = QuestTask(
            id = "t1",
            title = "Boire 500ml d'eau",
            isRoutineChecklistItem = true,
            parentRoutineId = "routine-morning",
            completedAt = fixedClock("2026-11-09T07:05:00").instant()
        )
        val task2Incomplete = QuestTask(
            id = "t2",
            title = "20 pompes",
            isRoutineChecklistItem = true,
            parentRoutineId = "routine-morning",
            completedAt = null
        )

        val clockMonday715 = fixedClock("2026-11-09T07:15:00") // Lundi 07:15

        // 1. Checklist incomplète à 07:15 -> Block(MORNING_ROUTINE)
        assertEquals(
            BlockDecision.Block(BlockReason.MORNING_ROUTINE, "routine-morning", "Morning Routine"),
            RuleEngine.evaluate(
                clock = clockMonday715,
                foregroundPackage = instagram,
                rules = listOf(routineRule),
                tasks = listOf(task1, task2Incomplete)
            )
        )

        // 2. Checklist complète à 07:15 -> Allow
        val task2Complete = task2Incomplete.copy(completedAt = fixedClock("2026-11-09T07:10:00").instant())
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                clock = clockMonday715,
                foregroundPackage = instagram,
                rules = listOf(routineRule),
                tasks = listOf(task1, task2Complete)
            )
        )

        // 3. À 07:30 pile (borne de fin) même avec checklist incomplète -> Allow
        val clockMonday730 = fixedClock("2026-11-09T07:30:00")
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                clock = clockMonday730,
                foregroundPackage = instagram,
                rules = listOf(routineRule),
                tasks = listOf(task1, task2Incomplete)
            )
        )

        // 4. Jour non actif (Dimanche 2026-11-08 à 07:15) -> Allow
        val clockSunday715 = fixedClock("2026-11-08T07:15:00")
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                clock = clockSunday715,
                foregroundPackage = instagram,
                rules = listOf(routineRule),
                tasks = listOf(task1, task2Incomplete)
            )
        )
    }

    @Test
    fun `3_14 morning routine checklist completed yesterday is automatically incomplete today without reset job`() {
        val routineRule = BlockRule(
            id = "routine-morning",
            title = "Morning Routine",
            ruleType = RuleType.ROUTINE,
            targetPackages = setOf(instagram),
            activeDaysBitmask = DaysBitmask.ALL_DAYS,
            startTimeMinutes = 7 * 60,
            endTimeMinutes = 7 * 60 + 30
        )

        // Sous-tâche cochée HIER (8 novembre à 07:10)
        val completedYesterdayItem = QuestTask(
            id = "t-yesterday",
            title = "Boire 500ml d'eau",
            isRoutineChecklistItem = true,
            parentRoutineId = "routine-morning",
            completedAt = fixedClock("2026-11-08T07:10:00").instant()
        )

        // Aujourd'hui (9 novembre à 07:15) -> completedAt < début du jour local -> Block(MORNING_ROUTINE) !
        val clockToday715 = fixedClock("2026-11-09T07:15:00")
        assertEquals(
            BlockDecision.Block(BlockReason.MORNING_ROUTINE, "routine-morning", "Morning Routine"),
            RuleEngine.evaluate(
                clock = clockToday715,
                foregroundPackage = instagram,
                rules = listOf(routineRule),
                tasks = listOf(completedYesterdayItem)
            )
        )
    }

    @Test
    fun `2_7 morning routine respects injected ZoneId on DST transition day`() {
        val parisZone = ZoneId.of("Europe/Paris")
        // Dimanche 25 octobre 2026 : passage à l'heure d'hiver en Europe
        val dstInstant = ZonedDateTime.of(2026, 10, 25, 7, 15, 0, 0, parisZone).toInstant()
        val parisClock = Clock.fixed(dstInstant, parisZone)

        val routineRule = BlockRule(
            id = "routine-dst",
            title = "Routine DST",
            ruleType = RuleType.ROUTINE,
            targetPackages = setOf(instagram),
            activeDaysBitmask = DaysBitmask.SUNDAY,
            startTimeMinutes = 7 * 60,
            endTimeMinutes = 7 * 60 + 30
        )
        val incompleteItem = QuestTask(
            id = "t-dst",
            title = "Méditation",
            isRoutineChecklistItem = true,
            parentRoutineId = "routine-dst",
            completedAt = null
        )

        assertEquals(
            BlockDecision.Block(BlockReason.MORNING_ROUTINE, "routine-dst", "Routine DST"),
            RuleEngine.evaluate(parisClock, instagram, rules = listOf(routineRule), tasks = listOf(incompleteItem))
        )
    }

    // =========================================================================
    // 2.8 NIVEAU 6 : PAUSE SABBATIQUE + DÉTOX INTERMITTENTE (1J/2)
    // =========================================================================

    @Test
    fun `2_8 sabbatical blocks when now is strictly before pauseUntil to the exact second`() {
        val pauseUntil = fixedClock("2026-11-15T12:00:00").instant()
        val sabbaticalRule = BlockRule(
            id = "sab-1",
            title = "Pause Sabbatique 7j",
            ruleType = RuleType.SABBATICAL,
            targetPackages = setOf(tiktok),
            pauseUntil = pauseUntil
        )

        // 1 seconde avant (11:59:59) -> Block
        assertEquals(
            BlockDecision.Block(BlockReason.SABBATICAL_OR_DETOX, "sab-1", "Pause Sabbatique 7j"),
            RuleEngine.evaluate(fixedClock("2026-11-15T11:59:59"), tiktok, rules = listOf(sabbaticalRule))
        )

        // À la seconde pile (12:00:00) -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(fixedClock("2026-11-15T12:00:00"), tiktok, rules = listOf(sabbaticalRule))
        )
    }

    @Test
    fun `2_8 alternating detox 1 day out of 2 handles ON-OFF days, midnight crossing, future start, and zero interval`() {
        val startEpochDay = LocalDate.of(2026, 11, 10).toEpochDay()
        val detoxRule = BlockRule(
            id = "detox-1-2",
            title = "Détox 1j/2",
            ruleType = RuleType.ALTERNATING_DETOX,
            targetPackages = setOf(instagram),
            cycleIntervalDays = 2,
            cycleStartEpochDay = startEpochDay
        )

        // Date de départ dans le futur (9 nov < 10 nov) -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(fixedClock("2026-11-09T23:59:59"), instagram, rules = listOf(detoxRule))
        )

        // Jour 0 (10 nov) = Jour ON -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(fixedClock("2026-11-10T23:59:59"), instagram, rules = listOf(detoxRule))
        )

        // Passage de minuit -> Jour 1 (11 nov à 00:00:00) = Jour OFF -> Block
        assertEquals(
            BlockDecision.Block(BlockReason.SABBATICAL_OR_DETOX, "detox-1-2", "Détox 1j/2"),
            RuleEngine.evaluate(fixedClock("2026-11-11T00:00:00"), instagram, rules = listOf(detoxRule))
        )

        // Jour 2 (12 nov) = Jour ON -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(fixedClock("2026-11-12T08:00:00"), instagram, rules = listOf(detoxRule))
        )

        // cycleIntervalDays <= 0 -> ignoré sans crash (évite ArithmeticException: / by zero)
        val invalidZeroRule = detoxRule.copy(id = "detox-zero", cycleIntervalDays = 0)
        val invalidNegativeRule = detoxRule.copy(id = "detox-neg", cycleIntervalDays = -3)
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                fixedClock("2026-11-11T12:00:00"),
                instagram,
                rules = listOf(invalidZeroRule, invalidNegativeRule)
            )
        )
    }

    // =========================================================================
    // 2.9 NIVEAU 7A : PLAGES HORAIRES (DIURNES & COUVRE-FEU TRAVERSANT MINUIT)
    // =========================================================================

    @Test
    fun `2_9 daytime schedule 09h00-17h00 Mon-Fri checks bounds, disabled flag, and target package`() {
        val workRule = BlockRule(
            id = "sched-work",
            title = "Focus Travail",
            ruleType = RuleType.SCHEDULE,
            targetPackages = setOf(instagram),
            activeDaysBitmask = DaysBitmask.WEEKDAYS,
            startTimeMinutes = 9 * 60,   // 09:00
            endTimeMinutes = 17 * 60     // 17:00
        )

        // Borne de début (09:00:00 Lundi) -> Block
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "sched-work", "Focus Travail"),
            RuleEngine.evaluate(fixedClock("2026-11-09T09:00:00"), instagram, rules = listOf(workRule))
        )

        // Borne de fin (17:00:00 Lundi) -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(fixedClock("2026-11-09T17:00:00"), instagram, rules = listOf(workRule))
        )

        // Package non ciblé (Spotify) -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(fixedClock("2026-11-09T11:00:00"), spotify, rules = listOf(workRule))
        )

        // Règle désactivée -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                fixedClock("2026-11-09T11:00:00"),
                instagram,
                rules = listOf(workRule.copy(isEnabled = false))
            )
        )
    }

    @Test
    fun `2_9 overnight curfew 22h30-07h00 uses start day bitmask across midnight`() {
        // Couvre-feu activé uniquement le Vendredi soir (commence vendredi à 22h30, finit samedi à 07h00)
        val fridayCurfew = BlockRule(
            id = "curfew-fri",
            title = "Couvre-feu Vendredi",
            ruleType = RuleType.SCHEDULE,
            targetPackages = setOf(instagram),
            activeDaysBitmask = DaysBitmask.FRIDAY,
            startTimeMinutes = 22 * 60 + 30, // 22:30
            endTimeMinutes = 7 * 60          // 07:00
        )

        // Vendredi 13 nov 2026 à 23:00 -> Block (Vendredi est actif)
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "curfew-fri", "Couvre-feu Vendredi"),
            RuleEngine.evaluate(fixedClock("2026-11-13T23:00:00"), instagram, rules = listOf(fridayCurfew))
        )

        // Samedi 14 nov 2026 à 02:00 -> Block (car la plage a commencé Vendredi !)
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "curfew-fri", "Couvre-feu Vendredi"),
            RuleEngine.evaluate(fixedClock("2026-11-14T02:00:00"), instagram, rules = listOf(fridayCurfew))
        )

        // Samedi 14 nov 2026 à 07:00 pile -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(fixedClock("2026-11-14T07:00:00"), instagram, rules = listOf(fridayCurfew))
        )

        // Samedi 14 nov 2026 à 23:00 -> Allow (Samedi soir n'est pas dans le bitmask)
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(fixedClock("2026-11-14T23:00:00"), instagram, rules = listOf(fridayCurfew))
        )
    }

    // =========================================================================
    // 2.10 NIVEAU 7B : QUOTAS JOURNALIERS (POOL PARTAGÉ + MALUS)
    // =========================================================================

    @Test
    fun `2_10 daily quota uses shared pool across target apps, applies malus reduction, and blocks at zero`() {
        val clock = fixedClock("2026-11-09T15:00:00")
        val sharedQuotaRule = BlockRule(
            id = "quota-social",
            title = "Quota Réseaux 30m",
            ruleType = RuleType.DAILY_QUOTA,
            targetPackages = setOf(instagram, tiktok),
            dailyQuotaMinutes = 30
        )

        // 1. Pool partagé : 14m Instagram + 15m TikTok = 29m (< 30m) -> Allow
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                clock = clock,
                foregroundPackage = instagram,
                rules = listOf(sharedQuotaRule),
                todayUsageMinutes = mapOf(instagram to 14, tiktok to 15)
            )
        )

        // 2. Pool partagé : 15m Instagram + 15m TikTok = 30m (>= 30m) -> Block
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "quota-social", "Quota Réseaux 30m"),
            RuleEngine.evaluate(
                clock = clock,
                foregroundPackage = instagram,
                rules = listOf(sharedQuotaRule),
                todayUsageMinutes = mapOf(instagram to 15, tiktok to 15)
            )
        )

        // 3. Réduction de malus -50% : 30m devient 15m effectifs -> 15m d'usage bloque !
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "quota-social", "Quota Réseaux 30m"),
            RuleEngine.evaluate(
                clock = clock,
                foregroundPackage = instagram,
                rules = listOf(sharedQuotaRule),
                todayUsageMinutes = mapOf(instagram to 10, tiktok to 5),
                securityState = SecurityState(quotaMalusReductionPercent = 50)
            )
        )

        // 4. Quota à 0 -> Block immédiat même avec 0 min d'usage
        val zeroQuotaRule = sharedQuotaRule.copy(id = "quota-zero", dailyQuotaMinutes = 0)
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "quota-zero", "Quota Réseaux 30m"),
            RuleEngine.evaluate(clock, instagram, rules = listOf(zeroQuotaRule), todayUsageMinutes = emptyMap())
        )
    }

    // =========================================================================
    // 2.11 ORDRE DE PRIORITÉ ET RÉSOLUTION DES CONFLITS
    // =========================================================================

    @Test
    fun `2_11 priority hierarchy resolves conflicts in strict order from Level 1 to Level 8`() {
        val clock = fixedClock("2026-11-09T07:15:00") // Lundi 07:15
        val now = clock.instant()

        val focusSession = FocusSession(
            id = "lvl-3-focus",
            sessionType = SessionType.ACTIVE_LEARNING,
            label = "Lvl 3 Focus",
            startedAt = now.minusSeconds(300),
            plannedEndAt = now.plusSeconds(1800)
        )
        val debtSession = FocusSession(
            id = "lvl-4-debt",
            sessionType = SessionType.FOCUS_DEBT_MALUS,
            label = "Lvl 4 Debt",
            startedAt = now.minusSeconds(300),
            plannedEndAt = now.plusSeconds(1800)
        )
        val routineRule = BlockRule(
            id = "lvl-5-routine",
            title = "Lvl 5 Routine",
            ruleType = RuleType.ROUTINE,
            targetPackages = setOf(instagram),
            startTimeMinutes = 7 * 60,
            endTimeMinutes = 7 * 60 + 30
        )
        val sabbaticalRule = BlockRule(
            id = "lvl-6-sabbatical",
            title = "Lvl 6 Sabbatical",
            ruleType = RuleType.SABBATICAL,
            targetPackages = setOf(instagram),
            pauseUntil = now.plusSeconds(86400)
        )
        val scheduleRule = BlockRule(
            id = "lvl-7a-schedule",
            title = "Lvl 7a Schedule",
            ruleType = RuleType.SCHEDULE,
            targetPackages = setOf(instagram),
            startTimeMinutes = 7 * 60,
            endTimeMinutes = 8 * 60
        )
        val quotaRule = BlockRule(
            id = "lvl-7b-quota",
            title = "Lvl 7b Quota",
            ruleType = RuleType.DAILY_QUOTA,
            targetPackages = setOf(instagram),
            dailyQuotaMinutes = 10
        )
        val allRules = listOf(quotaRule, scheduleRule, sabbaticalRule, routineRule)
        val usageOverQuota = mapOf(instagram to 25)

        // Tableau de vérification niveau par niveau quand plusieurs règles sont actives simultanément
        // 1. Niveau 1 gagne sur tout (Appel en cours)
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                clock, instagram, allRules, emptyList(), listOf(focusSession, debtSession),
                usageOverQuota, SecurityState(isCallInProgress = true)
            )
        )

        // 2. Niveau 2 gagne sur les niveaux 3..7 (Urgence valide)
        assertEquals(
            BlockDecision.Allow,
            RuleEngine.evaluate(
                clock, instagram, allRules, emptyList(), listOf(focusSession, debtSession),
                usageOverQuota, SecurityState(emergencyUnlockUntil = now.plusSeconds(60))
            )
        )

        // 3. Niveau 3 (Focus Session) gagne sur les niveaux 4..7
        assertEquals(
            BlockDecision.Block(BlockReason.ACTIVE_FOCUS_SESSION, "lvl-3-focus", "Lvl 3 Focus"),
            RuleEngine.evaluate(
                clock, instagram, allRules, emptyList(), listOf(debtSession, focusSession), usageOverQuota
            )
        )

        // 4. Niveau 4 (Dette de Focus) gagne sur les niveaux 5..7
        assertEquals(
            BlockDecision.Block(BlockReason.FOCUS_DEBT_PENALTY, "lvl-4-debt", "Lvl 4 Debt"),
            RuleEngine.evaluate(
                clock, instagram, allRules, emptyList(), listOf(debtSession), usageOverQuota
            )
        )

        // 5. Niveau 5 (Morning Routine) gagne sur les niveaux 6..7
        assertEquals(
            BlockDecision.Block(BlockReason.MORNING_ROUTINE, "lvl-5-routine", "Lvl 5 Routine"),
            RuleEngine.evaluate(clock, instagram, allRules, emptyList(), emptyList(), usageOverQuota)
        )

        // 6. Niveau 6 (Sabbatique / Détox) gagne sur le niveau 7
        val rulesFromLvl6 = listOf(quotaRule, scheduleRule, sabbaticalRule)
        assertEquals(
            BlockDecision.Block(BlockReason.SABBATICAL_OR_DETOX, "lvl-6-sabbatical", "Lvl 6 Sabbatical"),
            RuleEngine.evaluate(clock, instagram, rulesFromLvl6, emptyList(), emptyList(), usageOverQuota)
        )

        // 7a. Niveau 7a (Plage horaire) gagne sur 7b (Quota)
        val rulesFromLvl7a = listOf(quotaRule, scheduleRule)
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "lvl-7a-schedule", "Lvl 7a Schedule"),
            RuleEngine.evaluate(clock, instagram, rulesFromLvl7a, emptyList(), emptyList(), usageOverQuota)
        )

        // 7b. Niveau 7b (Quota journalier) bloque quand seul actif
        assertEquals(
            BlockDecision.Block(BlockReason.SCHEDULE_OR_QUOTA, "lvl-7b-quota", "Lvl 7b Quota"),
            RuleEngine.evaluate(clock, instagram, listOf(quotaRule), emptyList(), emptyList(), usageOverQuota)
        )

        // 8. Toutes les règles désactivées -> Allow
        val disabledRules = allRules.map { it.copy(isEnabled = false) }
        assertTrue(
            RuleEngine.evaluate(clock, instagram, disabledRules, emptyList(), emptyList(), usageOverQuota) is BlockDecision.Allow
        )
    }
}