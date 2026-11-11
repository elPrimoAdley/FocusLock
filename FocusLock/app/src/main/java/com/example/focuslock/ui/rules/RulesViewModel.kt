package com.example.focuslock.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focuslock.BuildConfig
import com.example.focuslock.data.AppContainer
import com.example.focuslock.data.apps.InstalledAppsLoader
import com.example.focuslock.data.apps.SelectableAppInfo
import com.example.focuslock.data.usage.UsageStatsTracker
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.domain.AppMetadata
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.EditPermission
import com.example.focuslock.domain.FrictionReason
import com.example.focuslock.domain.RuleType
import com.example.focuslock.domain.repository.AppMetadataRepository
import com.example.focuslock.domain.repository.RuleRepository
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.engine.EditGuard
import com.example.focuslock.engine.QuotaRingStatus
import com.example.focuslock.engine.RuleFormatterAndDetoxCalculator
import com.example.focuslock.ui.quests.PendingDebugBypassAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class RuleCardUiModel(
    val rule: BlockRule,
    val formattedSummary: String,
    val targetAppLabels: List<String>,
    val quotaRingStatus: QuotaRingStatus? = null,
    val sabbaticalCountdown: String? = null,
    val detoxReopenDate: LocalDate? = null
)

data class RulesUiState(
    val rules: List<RuleCardUiModel> = emptyList(),
    val selectableApps: List<SelectableAppInfo> = emptyList(),
    val activeQuotaMalusPercent: Int = 0,
    val nowInstant: Instant = Instant.now(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val pendingDebugBypass: PendingDebugBypassAction? = null
)

class RulesViewModel(
    private val clock: Clock,
    private val ruleRepository: RuleRepository,
    private val appMetadataRepository: AppMetadataRepository,
    private val gamificationStore: GamificationStore,
    private val usageStatsTracker: UsageStatsTracker,
    val appsLoader: InstalledAppsLoader
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val pendingBypassFlow = MutableStateFlow<PendingDebugBypassAction?>(null)

    /**
     * 3.18 : Rafraîchit l'usage réel toutes les ~30 s uniquement quand l'écran est visible.
     */
    private val usagePollingFlow: Flow<Pair<Instant, Map<String, Int>>> = flow {
        while (currentCoroutineContext().isActive) {
            val now = clock.instant()
            val usage = usageStatsTracker.getTodayUsageMinutes(forceRefresh = true)
            emit(now to usage)
            delay(30_000L)
        }
    }

    val uiState: StateFlow<RulesUiState> = combine(
        ruleRepository.observeAllRules(),
        appMetadataRepository.observeAllMetadata(),
        gamificationStore.state,
        usagePollingFlow,
        pendingBypassFlow
    ) { allRules, metadataList, gamification, (now, usageMap), pendingBypass ->
        val zone = clock.zone
        val today = now.atZone(zone).toLocalDate()
        val metadataMap = metadataList.associateBy { it.packageName }
        val selectableApps = appsLoader.loadSelectableApps(metadataMap)
        val appLabelByPkg = selectableApps.associate { it.packageName to it.label }

        val activeMalusPercent = if (gamification.quotaMalusEpochDay == today.toEpochDay()) {
            gamification.quotaMalusReductionPercent
        } else {
            0
        }

        // Exclut les règles ROUTINE gérées dans l'onglet Routines de Quêtes (3.14)
        val displayRules = allRules.filter { it.ruleType != RuleType.ROUTINE }

        val cardModels = displayRules.map { rule ->
            val labels = rule.targetPackages.map { pkg ->
                appLabelByPkg[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }

            val quotaRing = if (rule.ruleType == RuleType.DAILY_QUOTA) {
                val sharedUsed = rule.targetPackages.sumOf { pkg -> usageMap[pkg] ?: 0 }
                RuleFormatterAndDetoxCalculator.computeQuotaRingStatus(
                    baseQuotaMinutes = rule.dailyQuotaMinutes ?: 0,
                    quotaMalusReductionPercent = activeMalusPercent,
                    usedMinutes = sharedUsed
                )
            } else {
                null
            }

            val sabbaticalCountdown = if (rule.ruleType == RuleType.SABBATICAL && rule.pauseUntil != null) {
                RuleFormatterAndDetoxCalculator.formatSabbaticalCountdown(rule.pauseUntil, now)
            } else {
                null
            }

            val reopenDate = if (rule.ruleType == RuleType.ALTERNATING_DETOX) {
                RuleFormatterAndDetoxCalculator.nextReopenDate(rule, today)
            } else {
                null
            }

            RuleCardUiModel(
                rule = rule,
                formattedSummary = RuleFormatterAndDetoxCalculator.formatRuleSummary(rule, now, zone),
                targetAppLabels = labels,
                quotaRingStatus = quotaRing,
                sabbaticalCountdown = sabbaticalCountdown,
                detoxReopenDate = reopenDate
            )
        }

        RulesUiState(
            rules = cardModels,
            selectableApps = selectableApps,
            activeQuotaMalusPercent = activeMalusPercent,
            nowInstant = now,
            zoneId = zone,
            pendingDebugBypass = pendingBypass
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RulesUiState(
            nowInstant = clock.instant(),
            zoneId = clock.zone
        )
    )

    fun dismissDebugBypass() {
        pendingBypassFlow.value = null
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
     * 3.16 : Activer est immédiat ; désactiver une règle active passe par EditGuard.
     */
    fun toggleRuleEnabled(rule: BlockRule, enabled: Boolean) {
        val now = clock.instant()
        val updated = rule.copy(isEnabled = enabled)
        when (val perm = EditGuard.checkRuleEdit(rule, updated, now)) {
            is EditPermission.Allowed -> {
                viewModelScope.launch(Dispatchers.IO) {
                    ruleRepository.setRuleEnabled(rule.id, enabled)
                    _events.tryEmit("Règle « ${rule.title} » activée immédiatement.")
                }
            }
            is EditPermission.RequiresFriction -> {
                val msg = formatFrictionReason(perm.reason)
                _events.tryEmit("Désactivation refusée : $msg")
                if (BuildConfig.DEBUG) {
                    pendingBypassFlow.value = PendingDebugBypassAction(
                        description = "Forcer la désactivation de « ${rule.title} » (Debug)",
                        onConfirmBypass = {
                            viewModelScope.launch(Dispatchers.IO) {
                                ruleRepository.setRuleEnabled(rule.id, false)
                                _events.tryEmit("Désactivation forcée (Build Debug).")
                            }
                            pendingBypassFlow.value = null
                        }
                    )
                }
            }
        }
    }

    fun saveRule(
        existingRule: BlockRule?,
        draftRule: BlockRule,
        onSavedSuccess: () -> Unit
    ) {
        if (draftRule.title.isBlank()) {
            _events.tryEmit("Donne un titre à ta règle.")
            return
        }
        if (draftRule.targetPackages.isEmpty()) {
            _events.tryEmit("Sélectionne au moins une application cible.")
            return
        }

        val now = clock.instant()
        val finalRule = draftRule.copy(
            id = existingRule?.id ?: "rule-${UUID.randomUUID()}",
            title = draftRule.title.trim()
        )

        if (existingRule != null) {
            when (val perm = EditGuard.checkRuleEdit(existingRule, finalRule, now)) {
                is EditPermission.Allowed -> {
                    persistRule(finalRule, "Règle « ${finalRule.title} » durcie / mise à jour.")
                    onSavedSuccess()
                }
                is EditPermission.RequiresFriction -> {
                    val msg = formatFrictionReason(perm.reason)
                    _events.tryEmit("Modification refusée : $msg")
                    if (BuildConfig.DEBUG) {
                        pendingBypassFlow.value = PendingDebugBypassAction(
                            description = "Forcer l'assouplissement de « ${finalRule.title} » (Debug)",
                            onConfirmBypass = {
                                persistRule(finalRule, "Modification forcée (Build Debug).")
                                pendingBypassFlow.value = null
                            }
                        )
                    }
                    onSavedSuccess()
                }
            }
        } else {
            persistRule(finalRule, "Règle « ${finalRule.title} » engagée.")
            onSavedSuccess()
        }
    }

    fun requestDeleteRule(rule: BlockRule) {
        when (val perm = EditGuard.checkRuleDeletion(rule)) {
            is EditPermission.Allowed -> {
                viewModelScope.launch(Dispatchers.IO) {
                    ruleRepository.deleteRuleById(rule.id)
                    _events.tryEmit("Règle « ${rule.title} » supprimée.")
                }
            }
            is EditPermission.RequiresFriction -> {
                val msg = formatFrictionReason(perm.reason)
                _events.tryEmit("Suppression refusée : $msg")
                if (BuildConfig.DEBUG) {
                    pendingBypassFlow.value = PendingDebugBypassAction(
                        description = "Forcer la suppression de « ${rule.title} » (Debug)",
                        onConfirmBypass = {
                            viewModelScope.launch(Dispatchers.IO) {
                                ruleRepository.deleteRuleById(rule.id)
                                _events.tryEmit("Suppression forcée (Build Debug).")
                            }
                            pendingBypassFlow.value = null
                        }
                    )
                }
            }
        }
    }

    private fun persistRule(rule: BlockRule, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleRepository.upsertRule(rule)
            _events.tryEmit(message)
        }
    }

    private fun formatFrictionReason(reason: FrictionReason): String = when (reason) {
        FrictionReason.DISABLING_ACTIVE_RULE ->
            "Désactiver une règle active exige le sas Mindful Edits & PIN (Phase 5)."
        FrictionReason.DELETING_ACTIVE_RULE ->
            "Impossible de supprimer une règle active sans le code PIN strict."
        FrictionReason.REMOVING_TARGET_PACKAGES ->
            "Retirer une application cible d'une règle active est un assouplissement protégé."
        FrictionReason.REDUCING_ACTIVE_DAYS ->
            "Retirer un jour actif d'une règle active est un assouplissement protégé."
        FrictionReason.SHORTENING_SCHEDULE_WINDOW ->
            "Raccourcir une plage horaire active est bloqué par EditGuard."
        FrictionReason.LOOSENING_DAILY_QUOTA ->
            "Augmenter un quota journalier actif est bloqué (seule la réduction est immédiate)."
        FrictionReason.SHORTENING_SABBATICAL ->
            "Raccourcir ou annuler une Pause Sabbatique en cours est bloqué (prolonger est permis)."
        FrictionReason.LOOSENING_DETOX_CYCLE ->
            "Assouplir un cycle de Détox Intermittente actif est bloqué par EditGuard."
        else -> "Modification protégée par EditGuard."
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RulesViewModel(
                        clock = container.clock,
                        ruleRepository = container.ruleRepository,
                        appMetadataRepository = container.appMetadataRepository,
                        gamificationStore = container.gamificationStore,
                        usageStatsTracker = container.usageStatsTracker,
                        appsLoader = container.installedAppsLoader
                    ) as T
                }
            }
    }
}