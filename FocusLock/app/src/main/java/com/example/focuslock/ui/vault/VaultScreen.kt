package com.example.focuslock.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.domain.SavedPost
import com.example.focuslock.domain.vault.VaultStatusFilter
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.FocusModalSheet
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.navigation.Destination
import com.example.focuslock.ui.navigation.TopSectionBar
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onOpenDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedPostForDetail by remember { mutableStateOf<SavedPost?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.openInstagramUrlEvents.collectLatest { sourceUrl ->
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)).apply {
                    setPackage(VaultViewModel.INSTAGRAM_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                snackbarHostState.showSnackbar("Impossible d'ouvrir Instagram sur cet appareil.")
            }
        }
    }

    Scaffold(
        containerColor = colors.nightAbyss,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.nightAbyss)
                .padding(innerPadding)
                .systemBarsPadding()
        ) {
            TopSectionBar(title = Destination.Vault.title, onOpenDrawer = onOpenDrawer)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 4.6 : Barre de recherche sans accents ni majuscules
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Rechercher (titre, note, légende, tag sans accent)...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.electricAzureStart,
                            unfocusedBorderColor = colors.steelLine,
                            focusedTextColor = colors.ink,
                            unfocusedTextColor = colors.ink
                        )
                    )
                }

                // 4.6 : Filtres de statut (Tous, À voir, Consultés, Favoris)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VaultStatusFilter.entries.forEach { filter ->
                            val selected = uiState.statusFilter == filter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.elevatedCard else colors.deepSlate)
                                    .border(
                                        1.dp,
                                        if (selected) colors.electricAzureStart else colors.steelLine,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectStatusFilter(filter) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.label,
                                    style = typo.bodySecondary,
                                    color = if (selected) colors.ink else colors.mutedInk,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // 4.6 : Chips de filtrage par Tag
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val allSelected = uiState.selectedTag == null
                            TagFilterChip(
                                label = "Tous les tags (${uiState.allPostsCount})",
                                isSelected = allSelected,
                                onClick = { viewModel.selectTag(null) }
                            )
                        }
                        items(uiState.availableTags) { tag ->
                            val isSelected = uiState.selectedTag != null &&
                                uiState.selectedTag.equals(tag, ignoreCase = true)
                            TagFilterChip(
                                label = tag,
                                isSelected = isSelected,
                                onClick = {
                                    viewModel.selectTag(if (isSelected) null else tag)
                                }
                            )
                        }
                    }
                }

                // 4.6 : Liste des cartes ou État vide
                if (uiState.filteredPosts.isEmpty()) {
                    item {
                        ElevatedCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📥", fontSize = 30.sp)
                                Text(
                                    text = if (uiState.allPostsCount == 0) {
                                        "Ton Coffre de Contenu est vide"
                                    } else {
                                        "Aucun post ne correspond à ces filtres"
                                    },
                                    style = typo.cardTitleSerif,
                                    color = colors.ink,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Dans Instagram, appuie sur Partager → Partager via… → FocusLock pour sauvegarder un Reel ou un carrousel hors-ligne avec un titre et un tag.",
                                    style = typo.bodySecondary,
                                    color = colors.mutedInk,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.filteredPosts, key = { it.id }) { post ->
                        VaultPostCard(
                            post = post,
                            zoneId = uiState.zoneId,
                            onCardClick = { selectedPostForDetail = post },
                            onToggleFavorite = { viewModel.toggleFavorite(post) },
                            onOpenClick = { viewModel.requestOpenPost(post) }
                        )
                    }
                }
            }
        }
    }

    // 4.7 : Fiche détail & édition sans friction
    val detailPost = selectedPostForDetail?.let { current ->
        uiState.filteredPosts.firstOrNull { it.id == current.id } ?: current
    }
    if (detailPost != null) {
        VaultDetailModalSheet(
            post = detailPost,
            availableTags = uiState.availableTags,
            onDismiss = { selectedPostForDetail = null },
            onSaveEdits = { newTitle, newTag, newNote ->
                viewModel.updatePostDetails(detailPost.id, newTitle, newTag, newNote)
                selectedPostForDetail = null
            },
            onToggleFavorite = { viewModel.toggleFavorite(detailPost) },
            onToggleReviewed = { viewModel.toggleReviewed(detailPost) },
            onCopyLink = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Lien Instagram", detailPost.sourceUrl))
                scope.launch {
                    snackbarHostState.showSnackbar("Lien copié dans le presse-papiers.")
                }
            },
            onDeleteConfirmed = {
                viewModel.deletePost(detailPost.id, detailPost.userTitle)
                selectedPostForDetail = null
            },
            onOpenInInstagram = {
                selectedPostForDetail = null
                viewModel.requestOpenPost(detailPost)
            }
        )
    }

    // 4.8 : Feuille de refus d'ouverture lorsque le RuleEngine bloque Instagram (zéro contournement)
    val blocked = uiState.blockedNotice
    if (blocked != null) {
        FocusModalSheet(
            title = "Ouverture Verrouillée",
            saveButtonText = "Compris (Rester concentré)",
            rightActionSymbol = "🔒",
            onDismissRequest = { viewModel.dismissBlockedNotice() },
            onSaveClick = { viewModel.dismissBlockedNotice() }
        ) {
            ElevatedCard {
                Text(
                    text = "PROTECTION DU COFFRE ACTIVE",
                    style = typo.monoTag,
                    color = colors.softCoral
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = blocked.reasonTitle,
                    style = typo.cardTitleSerif,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Post demandé : « ${blocked.postTitle} »",
                    style = typo.monoMetric,
                    color = colors.electricAzureStart
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = blocked.reasonDetail,
                    style = typo.bodySecondary,
                    color = colors.mutedInk
                )
            }
        }
    }
}

@Composable
private fun TagFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = FocusTheme.colors
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) colors.electricAzureStart else colors.deepSlate)
            .border(
                1.dp,
                if (isSelected) colors.electricAzureEnd else colors.steelLine,
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = FocusTheme.typography.monoTag,
            color = if (isSelected) colors.nightAbyss else colors.ink
        )
    }
}

@Composable
private fun VaultPostCard(
    post: SavedPost,
    zoneId: ZoneId,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenClick: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val dateStr = remember(post.savedAt, zoneId) {
        post.savedAt.atZone(zoneId).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    ElevatedCard(onClick = onCardClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.electricAzureStart.copy(alpha = 0.16f))
                        .border(1.dp, colors.electricAzureStart.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(post.tag, style = typo.monoTag, color = colors.electricAzureStart)
                }

                // Badge Statut (À voir vs Consulté)
                val statusColor = if (post.isReviewed) colors.mintClarityStart else colors.mutedInk
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.deepSlate)
                        .border(1.dp, statusColor.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (post.isReviewed) "✓ Consulté" else "À voir",
                        style = typo.monoTag,
                        color = statusColor
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Compteur d'utilisation en police monospace + date
                Text(
                    text = "${post.openCount}× • $dateStr",
                    style = typo.monoMetric.copy(fontSize = 11.sp),
                    color = colors.mutedInk
                )
                Text(
                    text = if (post.isFavorite) "★" else "☆",
                    fontSize = 18.sp,
                    color = if (post.isFavorite) colors.mintClarityStart else colors.mutedInk,
                    modifier = Modifier.clickable { onToggleFavorite() }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = post.userTitle,
            style = typo.cardTitleSerif,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!post.personalNote.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.personalNote,
                style = typo.bodySecondary,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (!post.extractedCaption.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.extractedCaption,
                style = typo.bodySecondary,
                color = colors.mutedInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Détails & Notes",
                style = typo.monoTag,
                color = colors.mutedInk,
                modifier = Modifier.clickable { onCardClick() }
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.deepSlate)
                    .border(1.dp, colors.mintClarityStart, CircleShape)
                    .clickable { onOpenClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Ouvrir le post (3 min) ↗",
                    style = typo.monoTag,
                    color = colors.mintClarityStart
                )
            }
        }
    }
}

// ==================== 4.7 FICHE DÉTAIL & ÉDITION ====================

@Composable
private fun VaultDetailModalSheet(
    post: SavedPost,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onSaveEdits: (title: String, tag: String, personalNote: String?) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleReviewed: () -> Unit,
    onCopyLink: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onOpenInInstagram: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography

    var title by remember(post.id) { mutableStateOf(post.userTitle) }
    var tag by remember(post.id) { mutableStateOf(post.tag) }
    var note by remember(post.id) { mutableStateOf(post.personalNote ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    FocusModalSheet(
        title = "Fiche du Coffre",
        saveButtonText = "Enregistrer les modifications",
        rightActionSymbol = if (post.isFavorite) "★" else "☆",
        onRightActionClick = onToggleFavorite,
        onDismissRequest = onDismiss,
        onSaveClick = {
            onSaveEdits(title, tag, note)
        }
    ) {
        // Carte 1 : Ouverture contrôlée & Actions rapides (Consulté, Favori, Copier le lien)
        ElevatedCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONSULTATION CONTRÔLÉE (3 MIN MAX)",
                    style = typo.monoTag,
                    color = colors.mintClarityStart
                )
                Text(
                    text = "Ouvert ${post.openCount}×",
                    style = typo.monoMetric,
                    color = colors.ink
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            MintPillButton(
                text = "Ouvrir le post dans Instagram (3 min)",
                onClick = onOpenInInstagram
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.deepSlate)
                        .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                        .clickable { onToggleReviewed() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (post.isReviewed) "✓ Consulté" else "Marquer consulté",
                        style = typo.monoTag,
                        color = if (post.isReviewed) colors.mintClarityStart else colors.ink
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.deepSlate)
                        .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                        .clickable { onCopyLink() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Copier le lien", style = typo.monoTag, color = colors.electricAzureStart)
                }
            }
        }

        // Carte 2 : Édition du titre, du tag et de la note
        ElevatedCard {
            Text("ÉDITION DE LA FICHE", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text("Tag :", style = typo.bodySecondary, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(6.dp))
            availableTags.chunked(3).forEach { rowTags ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowTags.forEach { t ->
                        val active = tag.equals(t, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (active) colors.electricAzureStart else colors.deepSlate)
                                .border(1.dp, colors.steelLine, CircleShape)
                                .clickable { tag = t }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t,
                                style = typo.monoTag,
                                color = if (active) colors.nightAbyss else colors.ink,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note personnelle") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Carte 3 : Légende originale & Suppression avec confirmation
        ElevatedCard {
            if (!post.extractedCaption.isNullOrBlank()) {
                Text("LÉGENDE INSTAGRAM EXTRAITE", style = typo.monoTag, color = colors.mutedInk)
                Spacer(modifier = Modifier.height(6.dp))
                Text(post.extractedCaption, style = typo.bodySecondary, color = colors.mutedInk)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!confirmDelete) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.deepSlate)
                        .border(1.dp, colors.softCoral.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { confirmDelete = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Supprimer cette fiche du Coffre", style = typo.monoTag, color = colors.softCoral)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.softCoral)
                            .clickable { onDeleteConfirmed() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Confirmer la suppression", style = typo.monoTag, color = colors.nightAbyss)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.deepSlate)
                            .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                            .clickable { confirmDelete = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Annuler", style = typo.monoTag, color = colors.ink)
                    }
                }
            }
        }
    }
}
