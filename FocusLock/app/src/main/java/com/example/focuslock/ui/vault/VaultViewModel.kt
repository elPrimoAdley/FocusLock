package com.example.focuslock.ui.vault

import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.focuslock.data.AppContainer
import com.example.focuslock.data.accessibility.AccessDecisionEvaluator
import com.example.focuslock.domain.BlockDecision
import com.example.focuslock.domain.SavedPost
import com.example.focuslock.domain.repository.SavedContentRepository
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.domain.vault.VaultQuery
import com.example.focuslock.domain.vault.VaultStatusFilter
import com.example.focuslock.domain.vault.VaultTags
import kotlinx.coroutines.Dispatchers
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
import java.time.ZoneId

data class BlockedOpeningNotice(
    val postTitle: String,
    val reasonTitle: String,
    val reasonDetail: String
)

data class VaultUiState(
    val allPostsCount: Int = 0,
    val filteredPosts: List<SavedPost> = emptyList(),
    val availableTags: List<String> = VaultTags.PRESET_TAGS,
    val selectedTag: String? = null, // null = Tous les tags
    val statusFilter: VaultStatusFilter = VaultStatusFilter.ALL,
    val searchQuery: String = "",
    val blockedNotice: BlockedOpeningNotice? = null,
    val zoneId: ZoneId = ZoneId.systemDefault()
)

class VaultViewModel(
    private val clock: Clock,
    private val packageManager: PackageManager,
    private val savedContentRepository: SavedContentRepository,
    private val gamificationStore: GamificationStore,
    private val accessDecisionEvaluator: AccessDecisionEvaluator
) : ViewModel() {

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val VIEWING_WINDOW_SECONDS = 3 * 60L // 4.8 : Fenêtre stricte de 3 minutes

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VaultViewModel(
                        clock = container.clock,
                        packageManager = container.appContext.packageManager,
                        savedContentRepository = container.savedContentRepository,
                        gamificationStore = container.gamificationStore,
                        accessDecisionEvaluator = container.accessDecisionEvaluator
                    ) as T
                }
            }
    }

    private val selectedTagFlow = MutableStateFlow<String?>(null)
    private val statusFilterFlow = MutableStateFlow(VaultStatusFilter.ALL)
    private val searchQueryFlow = MutableStateFlow("")
    private val blockedNoticeFlow = MutableStateFlow<BlockedOpeningNotice?>(null)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _openInstagramUrlEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openInstagramUrlEvents: SharedFlow<String> = _openInstagramUrlEvents.asSharedFlow()

    val uiState: StateFlow<VaultUiState> = combine(
        savedContentRepository.observeAllSavedPosts(),
        selectedTagFlow,
        statusFilterFlow,
        searchQueryFlow,
        blockedNoticeFlow
    ) { allPosts, selectedTag, statusFilter, query, blockedNotice ->
        val dynamicTags = (VaultTags.PRESET_TAGS + allPosts.map { it.tag })
            .distinctBy { VaultQuery.normalizeForSearch(it) }

        val filtered = VaultQuery.filter(
            posts = allPosts,
            selectedTag = selectedTag,
            statusFilter = statusFilter,
            searchQuery = query
        )

        VaultUiState(
            allPostsCount = allPosts.size,
            filteredPosts = filtered,
            availableTags = dynamicTags,
            selectedTag = selectedTag,
            statusFilter = statusFilter,
            searchQuery = query,
            blockedNotice = blockedNotice,
            zoneId = clock.zone
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultUiState(zoneId = clock.zone)
    )

    fun selectTag(tag: String?) {
        selectedTagFlow.value = tag
    }

    fun selectStatusFilter(filter: VaultStatusFilter) {
        statusFilterFlow.value = filter
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun dismissBlockedNotice() {
        blockedNoticeFlow.value = null
    }

    // ==================== 4.7 ACTIONS DE LA FICHE DÉTAIL (SANS FRICTION) ====================

    fun updatePostDetails(id: String, title: String, tag: String, personalNote: String?) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _events.tryEmit("Le titre ne peut pas être vide.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            savedContentRepository.updateTitleTagAndNote(
                id = id,
                userTitle = cleanTitle,
                tag = tag.trim().ifEmpty { VaultTags.PRESET_TAGS.first() },
                personalNote = personalNote?.trim()?.takeIf { it.isNotEmpty() }
            )
            _events.tryEmit("Fiche « $cleanTitle » mise à jour.")
        }
    }

    fun toggleFavorite(post: SavedPost) {
        viewModelScope.launch(Dispatchers.IO) {
            savedContentRepository.updateFavoriteStatus(post.id, !post.isFavorite)
        }
    }

    fun toggleReviewed(post: SavedPost) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = !post.isReviewed
            savedContentRepository.updateReviewedStatus(post.id, next)
            _events.tryEmit(if (next) "Marqué comme consulté." else "Remis dans « À voir ».")
        }
    }

    fun deletePost(id: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            savedContentRepository.deleteSavedPostById(id)
            _events.tryEmit("Fiche « $title » supprimée du Coffre.")
        }
    }

    // ==================== 4.8 OUVERTURE CONTRÔLÉE (FENÊTRE DE 3 MIN) ====================

    fun requestOpenPost(post: SavedPost) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Évalue le même moteur que le service d'accessibilité pour com.instagram.android
            val snapshot = accessDecisionEvaluator.evaluateLiveForPackage(
                targetPackage = INSTAGRAM_PACKAGE,
                ignoreViewingWindow = true
            )

            when (val decision = snapshot.decision) {
                is BlockDecision.Block -> {
                    val matchedRule = snapshot.rules.firstOrNull { it.id == decision.ruleId }
                    val matchedSession = snapshot.sessions.firstOrNull { it.id == decision.ruleId }
                    val ruleLabel = matchedRule?.title ?: matchedSession?.label ?: decision.message ?: decision.reason.name
                    blockedNoticeFlow.value = BlockedOpeningNotice(
                        postTitle = post.userTitle,
                        reasonTitle = "Accès refusé par « $ruleLabel »",
                        reasonDetail = "Le Coffre de Contenu respecte strictement tes règles actives et tes malus. Termine ta session ou attends ton créneau autorisé pour consulter ce post."
                    )
                    return@launch
                }

                is BlockDecision.Allow -> {
                    // 2. Vérifie qu'Instagram est installé (aucun repli vers un navigateur)
                    if (!isInstagramInstalled()) {
                        _events.tryEmit("Ouverture refusée : l'application Instagram n'est pas installée (aucun repli navigateur).")
                        return@launch
                    }

                    // 3. Crée et persiste la fenêtre de 3 min dans DataStore
                    val expiresAt = clock.instant().plusSeconds(VIEWING_WINDOW_SECONDS)
                    gamificationStore.update { state ->
                        state.copy(
                            viewingWindowPackage = INSTAGRAM_PACKAGE,
                            viewingWindowExpiresAtTimestamp = expiresAt.toEpochMilli()
                        )
                    }

                    // 4. Incrémente openCount (+1) et passe automatiquement le statut à Consulté (isReviewed = true)
                    savedContentRepository.incrementOpenCountAndMarkReviewed(post.id)

                    // 5. Déclenche l'Intent ACTION_VIEW ciblant exclusivement com.instagram.android
                    _events.tryEmit("Fenêtre de consultation de 3 min ouverte.")
                    _openInstagramUrlEvents.tryEmit(post.sourceUrl)
                }
            }
        }
    }

    private fun isInstagramInstalled(): Boolean {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    INSTAGRAM_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(INSTAGRAM_PACKAGE, 0)
            }
            true
        }.getOrDefault(false)
    }
}
