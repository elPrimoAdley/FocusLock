package com.example.focuslock.ui.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focuslock.data.AppContainer
import com.example.focuslock.data.accessibility.FocusAccessibilityService
import com.example.focuslock.data.usage.UsageStatsTracker
import com.example.focuslock.domain.LevelProgress
import com.example.focuslock.domain.store.GamificationState
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.engine.LevelCalculator
import com.example.focuslock.engine.security.PinSecurityEngine
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
import java.time.Instant
import java.time.ZoneId

data class SystemHealthStatus(
    val isAccessibilityServiceEnabled: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val lastHeartbeatInstant: Instant? = null
)

data class PendingProtectedSettingChange(
    val title: String,
    val description: String,
    val onAuthorized: () -> Unit
)

data class ProfileSecurityUiState(
    val gamification: GamificationState = GamificationState(),
    val levelProgress: LevelProgress = LevelCalculator.fromXp(0),
    val health: SystemHealthStatus = SystemHealthStatus(),
    val pendingGateAction: PendingProtectedSettingChange? = null,
    val zoneId: ZoneId = ZoneId.systemDefault()
)

class ProfileSecurityViewModel(
    private val appContext: Context,
    val clock: Clock,
    val gamificationStore: GamificationStore,
    private val usageStatsTracker: UsageStatsTracker
) : ViewModel() {

    private val healthTickFlow = MutableStateFlow(0)
    private val pendingGateFlow = MutableStateFlow<PendingProtectedSettingChange?>(null)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            while (true) {
                healthTickFlow.value += 1
                delay(3_000L)
            }
        }
    }

    val uiState: StateFlow<ProfileSecurityUiState> = combine(
        gamificationStore.state,
        healthTickFlow,
        pendingGateFlow
    ) { gamification, _, pendingGate ->
        val health = SystemHealthStatus(
            isAccessibilityServiceEnabled = checkAccessibilityServiceEnabled(),
            isUsageStatsGranted = usageStatsTracker.hasUsagePermission(),
            isBatteryOptimizationIgnored = checkBatteryOptimizationIgnored(),
            lastHeartbeatInstant = gamification.lastServiceHeartbeatTimestamp?.let(Instant::ofEpochMilli)
        )
        ProfileSecurityUiState(
            gamification = gamification,
            levelProgress = LevelCalculator.fromXp(gamification.totalXp),
            health = health,
            pendingGateAction = pendingGate,
            zoneId = clock.zone
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileSecurityUiState(zoneId = clock.zone)
    )

    fun refreshHealthStatus() {
        healthTickFlow.value += 1
    }

    fun dismissGate() {
        pendingGateFlow.value = null
    }

    // ==================== 1. PROFIL DISCIPLINAIRE ====================

    fun updateDailyFocusGoal(minutes: Int) {
        val clamped = minutes.coerceIn(15, 360)
        viewModelScope.launch(Dispatchers.IO) {
            gamificationStore.update { it.copy(dailyFocusGoalMinutes = clamped) }
            _events.tryEmit("Objectif quotidien fixé à $clamped min.")
        }
    }

    fun updateDefaultCustomBlockMessage(message: String) {
        val clean = message.trim().ifEmpty {
            "C'est ton temps d'étude. Une minute ici est une minute que tu ne récupéreras pas."
        }
        viewModelScope.launch(Dispatchers.IO) {
            gamificationStore.update { it.copy(defaultCustomBlockMessage = clean) }
            _events.tryEmit("Message d'encouragement enregistré.")
        }
    }

    // ==================== 2. CODE PIN INDÉPENDANT (PBKDF2) ====================

    fun setOrUpdatePin(newPin: String) {
        if (!PinSecurityEngine.isValidPinFormat(newPin)) {
            _events.tryEmit("Le code PIN doit comporter exactement 4 chiffres.")
            return
        }
        val current = uiState.value.gamification
        val applyNewPin: () -> Unit = {
            viewModelScope.launch(Dispatchers.Default) {
                val hashed = PinSecurityEngine.createPinHash(newPin)
                gamificationStore.update {
                    it.copy(
                        pinHashPBKDF2 = hashed.hashBase64,
                        pinSalt = hashed.saltBase64,
                        failedPinAttempts = 0,
                        pinLockoutUntilTimestamp = null
                    )
                }
                _events.tryEmit("Code PIN strict (PBKDF2) enregistré.")
            }
        }

        if (!current.isPinConfigured) {
            // Premier enregistrement du PIN = durcissement -> immédiat !
            applyNewPin()
        } else {
            // Remplacer un PIN existant exige de valider d'abord l'ancien PIN
            pendingGateFlow.value = PendingProtectedSettingChange(
                title = "Modifier le Code PIN Strict",
                description = "Valide ton code PIN actuel pour enregistrer le nouveau code.",
                onAuthorized = {
                    pendingGateFlow.value = null
                    applyNewPin()
                }
            )
        }
    }

    // ==================== 3. PRÉFÉRENCES DE BLOCAGE STRICT ====================

    fun setBlockFloatingWindows(enabled: Boolean) {
        if (enabled) {
            updateStoreImmediately("Blocage des fenêtres flottantes activé.") {
                it.copy(blockFloatingWindows = true)
            }
        } else {
            requestProtectedChange(
                title = "Désactiver l'anti-fenêtres flottantes",
                description = "Autoriser l'affichage pop-up Samsung ouvre une brèche de contournement."
            ) {
                updateStoreImmediately("Blocage des fenêtres flottantes désactivé.") {
                    it.copy(blockFloatingWindows = false)
                }
            }
        }
    }

    fun setBlockUninstallAndSettings(enabled: Boolean) {
        if (enabled) {
            updateStoreImmediately("Protection anti-arrêt forcé activée.") {
                it.copy(blockUninstallAndSettings = true)
            }
        } else {
            requestProtectedChange(
                title = "Désactiver la protection des Réglages",
                description = "Attention : cela permet de forcer l'arrêt de FocusLock pendant une session."
            ) {
                updateStoreImmediately("Protection des réglages désactivée.") {
                    it.copy(blockUninstallAndSettings = false)
                }
            }
        }
    }

    fun setSecureAfterRestart(enabled: Boolean) {
        if (enabled) {
            updateStoreImmediately("Sécurité après redémarrage activée.") {
                it.copy(secureAfterRestart = true)
            }
        } else {
            requestProtectedChange(
                title = "Désactiver la sécurité au redémarrage",
                description = "Maintient les verrous actifs dès le démarrage du téléphone."
            ) {
                updateStoreImmediately("Sécurité après redémarrage désactivée.") {
                    it.copy(secureAfterRestart = false)
                }
            }
        }
    }

    fun setMindfulEditDelaySeconds(seconds: Int) {
        val current = uiState.value.gamification.mindfulEditDelaySeconds
        val target = seconds.coerceIn(10, 60)
        // Allonger le délai (ex. 10s -> 30s) = durcir -> immédiat
        if (target >= current) {
            updateStoreImmediately("Délai Mindful Edits fixé à ${target}s.") {
                it.copy(mindfulEditDelaySeconds = target)
            }
        } else {
            requestProtectedChange(
                title = "Raccourcir le délai Mindful Edits (${target}s)",
                description = "Réduire le temps de respiration facilite les décisions impulsives."
            ) {
                updateStoreImmediately("Délai Mindful Edits réduit à ${target}s.") {
                    it.copy(mindfulEditDelaySeconds = target)
                }
            }
        }
    }

    fun setStrictAutoRelockMinutes(minutes: Int) {
        val current = uiState.value.gamification.strictAutoRelockMinutes
        val target = minutes.coerceIn(5, 30)
        // Raccourcir la fenêtre d'urgence (ex. 15m -> 5m) = durcir -> immédiat
        if (target <= current) {
            updateStoreImmediately("Bris de glace fixé à ${target} min max.") {
                it.copy(strictAutoRelockMinutes = target)
            }
        } else {
            requestProtectedChange(
                title = "Allonger le déverrouillage d'urgence (${target} min)",
                description = "Augmente la durée de suspension lors d'un bris de glace."
            ) {
                updateStoreImmediately("Bris de glace fixé à ${target} min.") {
                    it.copy(strictAutoRelockMinutes = target)
                }
            }
        }
    }

    private fun requestProtectedChange(
        title: String,
        description: String,
        onAuthorizedAction: () -> Unit
    ) {
        pendingGateFlow.value = PendingProtectedSettingChange(
            title = title,
            description = description,
            onAuthorized = {
                pendingGateFlow.value = null
                onAuthorizedAction()
            }
        )
    }

    private fun updateStoreImmediately(
        feedback: String,
        transform: (GamificationState) -> GamificationState
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            gamificationStore.update(transform)
            _events.tryEmit(feedback)
        }
    }

    private fun checkAccessibilityServiceEnabled(): Boolean {
        val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { info ->
            info.resolveInfo?.serviceInfo?.packageName == appContext.packageName &&
                info.resolveInfo?.serviceInfo?.name == FocusAccessibilityService::class.java.name
        }
    }

    private fun checkBatteryOptimizationIgnored(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileSecurityViewModel(
                        appContext = container.appContext,
                        clock = container.clock,
                        gamificationStore = container.gamificationStore,
                        usageStatsTracker = container.usageStatsTracker
                    ) as T
                }
            }
    }
}
