package com.example.focuslock.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focuslock.data.AppContainer
import com.example.focuslock.data.apps.InstalledAppsLoader
import com.example.focuslock.data.apps.SelectableAppInfo
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.domain.AppMetadata
import com.example.focuslock.domain.FocusMode
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.LevelProgress
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import com.example.focuslock.domain.repository.AppMetadataRepository
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.domain.toSessionType
import com.example.focuslock.engine.GamificationCoordinator
import com.example.focuslock.engine.LevelCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class HubUiState(
    val greeting: String = "Prêt pour ta séance ?",
    val dateLabel: String = "",
    val streakDays: Int = 0,
    val levelProgress: LevelProgress = LevelCalculator.fromXp(0),
    val completedFocusMinutes: Int = 0,
    val dailyGoalMinutes: Int = 120,
    val selectedDurationMinutes: Int = 60, // 5 min à 240 min (4 h)
    val selectedMode: FocusMode = FocusMode.LANGUAGE,
    val activeFocusSession: FocusSession? = null,
    val activeSessionRemainingSeconds: Long = 0L,
    val sessionAwaitingValidationNote: FocusSession? = null,
    val activeMalusBanner: String? = null,
    val topP1Quests: List<QuestTask> = emptyList(),
    val selectableApps: List<SelectableAppInfo> = emptyList()
)

class HubViewModel(
    private val clock: Clock,
    private val questTaskRepository: QuestTaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val appMetadataRepository: AppMetadataRepository,
    private val gamificationStore: GamificationStore,
    private val gamificationCoordinator: GamificationCoordinator,
    val appsLoader: InstalledAppsLoader
) : ViewModel() {

    private val selectedDurationFlow = MutableStateFlow(60)
    private val selectedModeFlow = MutableStateFlow(FocusMode.LANGUAGE)
    private val tickFlow = MutableStateFlow(clock.instant())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val frenchDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

    init {
        // Horloge seconde par seconde pour le décompte temps réel et l'expiration automatique (3.22)
        viewModelScope.launch {
            var tickCounter = 0
            while (true) {
                val now = clock.instant()
                tickFlow.value = now
                if (tickCounter % 5 == 0) {
                    with(Dispatchers.IO) {
                        focusSessionRepository.completeExpiredSessions(now)
                    }
                }
                tickCounter++
                delay(1_000L)
            }
        }
    }

    val uiState: StateFlow<HubUiState> = combine(
        questTaskRepository.observeAllTasks(),
        focusSessionRepository.observeAllSessions(),
        appMetadataRepository.observeAllMetadata(),
        gamificationStore.state,
        combine(selectedDurationFlow, selectedModeFlow, tickFlow) { dur, mode, now ->
            Triple(dur, mode, now)
        }
    ) { allTasks, allSessions, metadataList, gamification, (selectedDuration, selectedMode, now) ->
        val zone = clock.zone
        val zonedNow = now.atZone(zone)
        val today = zonedNow.toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant()

        val greeting = when (zonedNow.hour) {
            in 5..11 -> "Bonjour, prêt pour ta séance ?"
            in 12..17 -> "Bon après-midi, reste concentré."
            else -> "Bonsoir, protège ton calme."
        }
        val dateStr = today.format(frenchDateFormatter).replaceFirstChar { it.uppercase() }

        // Session de Focus active (hors dette)
        val activeFocus = allSessions.firstOrNull { session ->
            session.status == SessionStatus.RUNNING &&
                session.sessionType != SessionType.FOCUS_DEBT_MALUS &&
                now.isBefore(session.plannedEndAt)
        }
        val remainingSec = if (activeFocus != null) {
            Duration.between(now, activeFocus.plannedEndAt).seconds.coerceAtLeast(0L)
        } else {
            0L
        }

        // Session d'apprentissage tout juste terminée aujourd'hui sans note de synthèse (3.22 optionnel)
        val awaitingNote = allSessions.firstOrNull { session ->
            session.status == SessionStatus.COMPLETED &&
                session.sessionType == SessionType.ACTIVE_LEARNING &&
                !session.startedAt.isBefore(startOfToday) &&
                session.summaryValidationNote == null
        }

        // Minutes de focus du jour (sessions terminées aujourd'hui + minutes écoulées de la session en cours)
        val completedMinsToday = allSessions
            .filter { session ->
                session.status == SessionStatus.COMPLETED &&
                    session.sessionType != SessionType.FOCUS_DEBT_MALUS &&
                    !session.startedAt.isBefore(startOfToday) &&
                    session.startedAt.isBefore(endOfToday)
            }
            .sumOf { session ->
                val end = session.actualEndedAt ?: session.plannedEndAt
                Duration.between(session.startedAt, end).toMinutes().coerceAtLeast(0L).toInt()
            }

        val activeElapsedMins = if (activeFocus != null && !activeFocus.startedAt.isBefore(startOfToday)) {
            Duration.between(activeFocus.startedAt, now).toMinutes().coerceAtLeast(0L).toInt()
        } else {
            0
        }

        // Bandeau d'alerte Malus (3.23 : Dette de Focus active OU réduction de quota du jour)
        val activeDebt = allSessions.firstOrNull { session ->
            session.status == SessionStatus.RUNNING &&
                session.sessionType == SessionType.FOCUS_DEBT_MALUS &&
                now.isBefore(session.plannedEndAt)
        }
        val activeQuotaCut = if (gamification.quotaMalusEpochDay == today.toEpochDay()) {
            gamification.quotaMalusReductionPercent
        } else {
            0
        }

        val malusMessage = buildMalusBannerMessage(activeDebt, activeQuotaCut, now)

        // Aperçu des 3 Quêtes prioritaires P1 du jour (3.23)
        val topP1 = allTasks
            .filter { task ->
                !task.isRoutineChecklistItem &&
                    task.priority == QuestPriority.P1 &&
                    (task.dueAt == null || task.dueAt.isBefore(endOfToday))
            }
            .sortedWith(
                compareBy<QuestTask> { it.completedAt != null }
                    .thenBy { it.dueAt ?: Instant.MAX }
            )
            .take(3)

        val selectableApps = appsLoader.loadSelectableApps(metadataList.associateBy { it.packageName })

        HubUiState(
            greeting = greeting,
            dateLabel = dateStr,
            streakDays = gamification.currentStreakDays,
            levelProgress = LevelCalculator.fromXp(gamification.totalXp),
            completedFocusMinutes = completedMinsToday + activeElapsedMins,
            dailyGoalMinutes = gamification.dailyFocusGoalMinutes,
            selectedDurationMinutes = selectedDuration,
            selectedMode = selectedMode,
            activeFocusSession = activeFocus,
            activeSessionRemainingSeconds = remainingSec,
            sessionAwaitingValidationNote = awaitingNote,
            activeMalusBanner = malusMessage,
            topP1Quests = topP1,
            selectableApps = selectableApps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HubUiState()
    )

    fun adjustDuration(deltaMinutes: Int) {
        selectedDurationFlow.value = (selectedDurationFlow.value + deltaMinutes).coerceIn(5, 240)
    }

    fun selectMode(mode: FocusMode) {
        selectedModeFlow.value = mode
    }

    fun updateAppCategory(packageName: String, category: AppCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = appMetadataRepository.getMetadataForPackage(packageName)
            appMetadataRepository.upsertMetadata(
                AppMetadata(
                    packageName = packageName,
                    category = category,
                    isAlwaysWhitelisted = current?.isAlwaysWhitelisted ?: false
                )
            )
        }
    }

    /**
     * 3.21 : Démarre un Contrat de Temps FocusSession RUNNING avec plannedEndAt = now + durée.
     * Refuse s'il y a déjà une session en cours.
     */
    fun startFocusSession(
        mode: FocusMode,
        customLabel: String,
        durationMinutes: Int,
        whitelistedPackages: Set<String>,
        onStarted: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = clock.instant()
            focusSessionRepository.completeExpiredSessions(now)

            val running = focusSessionRepository.getRunningSessions()
                .firstOrNull { now.isBefore(it.plannedEndAt) }

            if (running != null) {
                _events.tryEmit("Refusé : une session (« ${running.label} ») est déjà en cours.")
                return@launch
            }

            val clampedDuration = durationMinutes.coerceIn(5, 240)
            val cleanLabel = customLabel.trim().ifEmpty { mode.label }
            val session = FocusSession(
                id = "focus-${UUID.randomUUID()}",
                sessionType = mode.toSessionType(),
                label = cleanLabel,
                startedAt = now,
                plannedEndAt = now.plusSeconds(clampedDuration * 60L),
                actualEndedAt = null,
                whitelistedPackages = whitelistedPackages,
                summaryValidationNote = null,
                status = SessionStatus.RUNNING
            )

            focusSessionRepository.upsertSession(session)
            _events.tryEmit("Contrat « $cleanLabel » verrouillé pour $clampedDuration min.")
            onStarted()
        }
    }

    /**
     * 3.22 (Optionnel) : Enregistre la note de fin de session résumant ce qui a été appris.
     */
    fun submitSessionValidationNote(session: FocusSession, note: String) {
        val finalNote = note.trim().ifEmpty { "Session validée" }
        viewModelScope.launch(Dispatchers.IO) {
            focusSessionRepository.updateSessionStatus(
                id = session.id,
                status = SessionStatus.COMPLETED,
                actualEndedAt = session.actualEndedAt ?: session.plannedEndAt,
                summaryNote = finalNote
            )
            _events.tryEmit("Note d'apprentissage archivée.")
        }
    }

    /**
     * 3.23 : Validation directe d'une quête P1 depuis l'aperçu du Hub.
     */
    fun completeP1QuestFromHub(task: QuestTask) {
        if (task.completedAt != null) {
            _events.tryEmit("Quête déjà terminée aujourd'hui.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val award = gamificationCoordinator.completeTask(task.id, clock.instant()) ?: return@launch
            if (award.totalEarnedXp > 0) {
                _events.tryEmit("Quête P1 validée ! +${award.totalEarnedXp} XP")
            } else {
                _events.tryEmit("Quête terminée hors délai (+0 XP)")
            }
        }
    }

    private fun buildMalusBannerMessage(
        activeDebt: FocusSession?,
        quotaCutPercent: Int,
        now: Instant
    ): String? {
        if (activeDebt == null && quotaCutPercent <= 0) return null
        val parts = mutableListOf<String>()
        if (activeDebt != null) {
            val minsLeft = ((Duration.between(now, activeDebt.plannedEndAt).seconds + 59L) / 60L)
                .coerceAtLeast(1L)
            parts.add("Dette de Focus stricte en cours : $minsLeft min restantes")
        }
        if (quotaCutPercent > 0) {
            parts.add("Quotas journaliers de loisirs amputés de −$quotaCutPercent %")
        }
        return parts.joinToString(" • ")
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HubViewModel(
                        clock = container.clock,
                        questTaskRepository = container.questTaskRepository,
                        focusSessionRepository = container.focusSessionRepository,
                        appMetadataRepository = container.appMetadataRepository,
                        gamificationStore = container.gamificationStore,
                        gamificationCoordinator = container.gamificationCoordinator,
                        appsLoader = container.installedAppsLoader
                    ) as T
                }
            }
    }
}