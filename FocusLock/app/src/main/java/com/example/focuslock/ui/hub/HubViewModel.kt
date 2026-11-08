package com.example.focuslock.ui.hub

import androidx.lifecycle.ViewModel
import com.example.focuslock.domain.FocusMode
import com.example.focuslock.domain.QuestPreviewItem
import com.example.focuslock.domain.QuestPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HubUiState(
    val greeting: String = "Bonjour, prêt pour ta séance ?",
    val dateLabel: String = "Jeudi 5 Novembre",
    val streakDays: Int = 7,
    val levelLabel: String = "Niv. 5 • Structuré",
    val currentXp: Int = 450,
    val nextLevelXp: Int = 600,
    val completedFocusMinutes: Int = 60,
    val dailyGoalMinutes: Int = 120,
    val selectedDurationMinutes: Int = 60, // Plage : 5 min à 240 min (4 h)
    val selectedMode: FocusMode = FocusMode.LANGUAGE,
    val activeMalus: String? = "Dette de Focus active : 30 min de blocage strict obligatoire (Quotas loisirs -50 %)",
    val topQuests: List<QuestPreviewItem> = listOf(
        QuestPreviewItem("q1", "1h d'apprentissage Espagnol (Tablette)", QuestPriority.P1, false),
        QuestPreviewItem("q2", "Routine Matinale complète (07h00–07h30)", QuestPriority.P1, true),
        QuestPreviewItem("q3", "Livrable Architecture Clean Phase 1", QuestPriority.P1, false)
    )
)

class HubViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    fun adjustDuration(deltaMinutes: Int) {
        _uiState.update { state ->
            val newDuration = (state.selectedDurationMinutes + deltaMinutes).coerceIn(5, 240)
            state.copy(selectedDurationMinutes = newDuration)
        }
    }

    fun selectMode(mode: FocusMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun toggleDebugMalus(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                activeMalus = if (enabled) {
                    "Dette de Focus active : 30 min de blocage strict obligatoire (Quotas loisirs -50 %)"
                } else {
                    null
                }
            )
        }
    }

    fun toggleQuestCompletion(questId: String) {
        _uiState.update { state ->
            state.copy(
                topQuests = state.topQuests.map { quest ->
                    if (quest.id == questId) quest.copy(isCompleted = !quest.isCompleted) else quest
                }
            )
        }
    }
}