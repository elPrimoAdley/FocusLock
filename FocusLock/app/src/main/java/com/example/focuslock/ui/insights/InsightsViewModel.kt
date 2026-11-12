package com.example.focuslock.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focuslock.BuildConfig
import com.example.focuslock.data.AppContainer
import com.example.focuslock.data.apps.InstalledAppsLoader
import com.example.focuslock.data.usage.UsageStatsTracker
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.domain.AppMetadata
import com.example.focuslock.domain.BlockAuditLog
import com.example.focuslock.domain.BlockReason
import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import com.example.focuslock.domain.repository.AppMetadataRepository
import com.example.focuslock.domain.repository.BlockAuditRepository
import com.example.focuslock.domain.repository.DailyUsageRepository
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.engine.insights.HeatmapCalculator
import com.example.focuslock.engine.insights.InsightsCalculator
import com.example.focuslock.engine.insights.InsightsPeriod
import com.example.focuslock.engine.insights.MonthlyHeatmapGrid
import com.example.focuslock.engine.insights.PeriodInsightsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class FormattedAuditLogItem(
    val id: Long,
    val date: LocalDate,
    val formattedLine: String // ex: "14:32:10 — Blocage de com.instagram.android : Quota journalier (30m/30m) atteint"
)

data class InsightsUiState(
    val hasUsagePermission: Boolean = true,
    val selectedPeriod: InsightsPeriod = InsightsPeriod.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
    val summary: PeriodInsightsSummary? = null,
    val appLabelsByPackage: Map<String, String> = emptyMap(),
    val distractionsPreventedToday: Int = 0,
    val awakeStartMinutes: Int = 7 * 60,
    val awakeEndMinutes: Int = 23 * 60,
    val heatmapGrid: MonthlyHeatmapGrid? = null,
    val auditLogs: List<FormattedAuditLogItem> = emptyList(),
    val auditFilterDate: LocalDate? = null,
    val canLoadMoreAuditLogs: Boolean = false
)

class InsightsViewModel(
    private val clock: Clock,
    private val usageStatsTracker: UsageStatsTracker,
    private val dailyUsageRepository: DailyUsageRepository,
    private val appMetadataRepository: AppMetadataRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val questTaskRepository: QuestTaskRepository,
    private val blockAuditRepository: BlockAuditRepository,
    private val gamificationStore: GamificationStore,
    val appsLoader: InstalledAppsLoader
) : ViewModel() {

    private val selectedPeriodFlow = MutableStateFlow(InsightsPeriod.DAY)
    private val selectedDateFlow = MutableStateFlow(LocalDate.now(clock))
    private val selectedHeatmapMonthFlow = MutableStateFlow(YearMonth.now(clock))
    private val auditFilterDateFlow = MutableStateFlow<LocalDate?>(null)
    private val auditLimitFlow = MutableStateFlow(50) // 4.20 : Par lots de 50
    private val permissionRefreshTrigger = MutableStateFlow(0)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val todayStartInstant = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()
    private val eightWeeksAgoEpochDay = LocalDate.now(clock).minusWeeks(9).toEpochDay()

    val uiState: StateFlow<InsightsUiState> = combine(
        combine(
            selectedPeriodFlow,
            selectedDateFlow,
            selectedHeatmapMonthFlow,
            auditFilterDateFlow,
            auditLimitFlow
        ) { period, date, month, auditDate, auditLimit ->
            FilterParams(period, date, month, auditDate, auditLimit)
        },
        dailyUsageRepository.observeUsageBetweenDays(eightWeeksAgoEpochDay, LocalDate.now(clock).toEpochDay()),
        appMetadataRepository.observeAllMetadata(),
        combine(
            focusSessionRepository.observeAllSessions(),
            questTaskRepository.observeAllTasks(),
            blockAuditRepository.observeRecentLogs(limit = 500),
            blockAuditRepository.observeTotalDistractionsPreventedSince(todayStartInstant),
            permissionRefreshTrigger
        ) { sessions, tasks, logs, preventedCount, _ ->
            DomainSnapshot(sessions, tasks, logs, preventedCount)
        },
        gamificationStore.state
    ) { params, historyRecords, metadataList, domainSnap, gamification ->
        val hasPerm = usageStatsTracker.hasUsagePermission()
        val zone = clock.zone
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()

        val explicitCategories = metadataList.associate { it.packageName to it.category }
        val selectableApps = appsLoader.loadSelectableApps(metadataList.associateBy { it.packageName })
        val labelsMap = selectableApps.associate { it.packageName to it.label }

        val summary = if (!hasPerm) {
            null
        } else {
            when (params.period) {
                InsightsPeriod.DAY -> {
                    val dayAgg = usageStatsTracker.getDayAggregation(
                        date = params.date,
                        categoryByPackage = explicitCategories,
                        forceRefresh = false
                    )
                    InsightsCalculator.buildDaySummary(
                        selectedDate = params.date,
                        todayDate = today,
                        dayAggregation = dayAgg,
                        explicitCategoryByPackage = explicitCategories,
                        now = now,
                        zoneId = zone,
                        awakeStartMinutes = gamification.awakeStartMinutes,
                        awakeEndMinutes = gamification.awakeEndMinutes
                    )
                }
                InsightsPeriod.WEEK -> {
                    val todayMins = usageStatsTracker.getTodayUsageMinutes(forceRefresh = false)
                    InsightsCalculator.buildWeekSummary(
                        anchorDate = params.date,
                        todayDate = today,
                        historyRecords = historyRecords,
                        todayMinutesByPackage = todayMins,
                        explicitCategoryByPackage = explicitCategories,
                        now = now,
                        zoneId = zone,
                        awakeStartMinutes = gamification.awakeStartMinutes,
                        awakeEndMinutes = gamification.awakeEndMinutes
                    )
                }
                InsightsPeriod.TREND -> {
                    val todayMins = usageStatsTracker.getTodayUsageMinutes(forceRefresh = false)
                    InsightsCalculator.buildTrendSummary(
                        anchorDate = params.date,
                        todayDate = today,
                        historyRecords = historyRecords,
                        todayMinutesByPackage = todayMins,
                        explicitCategoryByPackage = explicitCategories,
                        now = now,
                        zoneId = zone,
                        awakeStartMinutes = gamification.awakeStartMinutes,
                        awakeEndMinutes = gamification.awakeEndMinutes
                    )
                }
            }
        }

        val heatmap = HeatmapCalculator.buildMonthlyGrid(
            yearMonth = params.heatmapMonth,
            zoneId = zone,
            sessions = domainSnap.sessions,
            tasks = domainSnap.tasks
        )

        // 4.20 : Formatage exact du journal d'audit et pagination par lots de 50
        val filteredAuditSource = domainSnap.logs
            .filter { it.reasonCode != BlockReason.VIEWING_WINDOW_ENDED }
            .filter { log ->
                params.auditDate == null || log.timestamp.atZone(zone).toLocalDate() == params.auditDate
            }

        val visibleLogs = filteredAuditSource.take(params.auditLimit).map { log ->
            val zoned = log.timestamp.atZone(zone)
            val timeStr = zoned.format(timeFormatter)
            FormattedAuditLogItem(
                id = log.id,
                date = zoned.toLocalDate(),
                formattedLine = "$timeStr — Blocage de ${log.blockedPackageName} : ${log.explanationText}"
            )
        }

        InsightsUiState(
            hasUsagePermission = hasPerm,
            selectedPeriod = params.period,
            selectedDate = params.date,
            summary = summary,
            appLabelsByPackage = labelsMap,
            distractionsPreventedToday = domainSnap.preventedToday,
            awakeStartMinutes = gamification.awakeStartMinutes,
            awakeEndMinutes = gamification.awakeEndMinutes,
            heatmapGrid = heatmap,
            auditLogs = visibleLogs,
            auditFilterDate = params.auditDate,
            canLoadMoreAuditLogs = filteredAuditSource.size > params.auditLimit
        )
    }
        .flowOn(Dispatchers.Default) // 4.21 : Calculs exécutés sur Dispatchers.Default
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InsightsUiState()
        )

    fun refreshPermissionCheck() {
        permissionRefreshTrigger.value += 1
    }

    fun selectPeriod(period: InsightsPeriod) {
        selectedPeriodFlow.value = period
    }

    fun navigateDate(step: Int) {
        val current = selectedDateFlow.value
        selectedDateFlow.value = when (selectedPeriodFlow.value) {
            InsightsPeriod.DAY -> current.plusDays(step.toLong())
            InsightsPeriod.WEEK, InsightsPeriod.TREND -> current.plusWeeks(step.toLong())
        }
    }

    fun resetToToday() {
        selectedDateFlow.value = LocalDate.now(clock)
    }

    fun navigateHeatmapMonth(deltaMonths: Long) {
        selectedHeatmapMonthFlow.value = selectedHeatmapMonthFlow.value.plusMonths(deltaMonths)
    }

    fun setAuditDateFilter(date: LocalDate?) {
        auditFilterDateFlow.value = date
        auditLimitFlow.value = 50
    }

    fun loadMoreAuditLogs() {
        auditLimitFlow.value += 50
    }

    /**
     * 4.17 : Mise à jour de la catégorie d'une application et recalcul en direct du graphique.
     */
    fun assignAppCategory(packageName: String, category: AppCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = appMetadataRepository.getMetadataForPackage(packageName)
            appMetadataRepository.upsertMetadata(
                AppMetadata(
                    packageName = packageName,
                    category = category,
                    isAlwaysWhitelisted = current?.isAlwaysWhitelisted ?: false
                )
            )
            _events.tryEmit("Catégorie mise à jour : graphique recalculé.")
        }
    }

    /**
     * 4.18 : Mise à jour des horaires d'éveil pour le calcul de Balance.
     */
    fun updateAwakeWindow(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            gamificationStore.update { state ->
                state.copy(
                    awakeStartMinutes = startMinutes,
                    awakeEndMinutes = endMinutes
                )
            }
            _events.tryEmit("Fenêtre d'éveil mise à jour.")
        }
    }

    /**
     * 4.21 : Générateur de 30 jours de données de test (uniquement actif si BuildConfig.DEBUG).
     */
    fun seedThirtyDaysDebugData() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now(clock)
            for (daysAgo in 1..30) {
                val date = today.minusDays(daysAgo.toLong())
                val epochDay = date.toEpochDay()
                val igMs = ((15 + (daysAgo * 7) % 45) * 60_000L)
                val ytMs = ((20 + (daysAgo * 11) % 50) * 60_000L)
                val stravaMs = ((10 + (daysAgo * 5) % 30) * 60_000L)
                dailyUsageRepository.saveDayUsage(
                    epochDay = epochDay,
                    usageByPackageMs = mapOf(
                        "com.instagram.android" to igMs,
                        "com.google.android.youtube" to ytMs,
                        "com.strava" to stravaMs
                    )
                )

                val focusMins = (daysAgo * 25) % 140
                if (focusMins > 0) {
                    val start = date.atTime(9, 0).atZone(clock.zone).toInstant()
                    focusSessionRepository.upsertSession(
                        FocusSession(
                            id = "debug-hist-$epochDay",
                            sessionType = SessionType.DEEP_WORK,
                            label = "Session Debug $daysAgo",
                            startedAt = start,
                            plannedEndAt = start.plusSeconds(focusMins * 60L),
                            actualEndedAt = start.plusSeconds(focusMins * 60L),
                            status = SessionStatus.COMPLETED
                        )
                    )
                }
            }

            blockAuditRepository.insertLog(
                BlockAuditLog(
                    timestamp = clock.instant().minusSeconds(120),
                    blockedPackageName = "com.instagram.android",
                    triggerRuleId = "quota-ig",
                    reasonCode = BlockReason.SCHEDULE_OR_QUOTA,
                    explanationText = "Quota journalier (30m/30m) atteint"
                )
            )
            _events.tryEmit("30 jours d'historique de test générés (Debug).")
        }
    }

    private data class FilterParams(
        val period: InsightsPeriod,
        val date: LocalDate,
        val heatmapMonth: YearMonth,
        val auditDate: LocalDate?,
        val auditLimit: Int
    )

    private data class DomainSnapshot(
        val sessions: List<FocusSession>,
        val tasks: List<com.example.focuslock.domain.QuestTask>,
        val logs: List<BlockAuditLog>,
        val preventedToday: Int
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return InsightsViewModel(
                        clock = container.clock,
                        usageStatsTracker = container.usageStatsTracker,
                        dailyUsageRepository = container.dailyUsageRepository,
                        appMetadataRepository = container.appMetadataRepository,
                        focusSessionRepository = container.focusSessionRepository,
                        questTaskRepository = container.questTaskRepository,
                        blockAuditRepository = container.blockAuditRepository,
                        gamificationStore = container.gamificationStore,
                        appsLoader = container.installedAppsLoader
                    ) as T
                }
            }
    }
}
