package com.example.focuslock.ui.vault

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.focuslock.FocusLockApplication
import com.example.focuslock.domain.SavedPost
import com.example.focuslock.domain.repository.VaultSaveResult
import com.example.focuslock.domain.vault.InstagramUrlParser
import com.example.focuslock.domain.vault.ParsedInstagramShare
import com.example.focuslock.domain.vault.VaultTags
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.FocusModalSheet
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.theme.FocusLockTheme
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private sealed interface ShareCaptureState {
    data object Loading : ShareCaptureState
    data object InvalidLink : ShareCaptureState
    data class Duplicate(val existingPost: SavedPost) : ShareCaptureState
    data class Ready(val parsed: ParsedInstagramShare, val availableTags: List<String>) : ShareCaptureState
}

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (applicationContext as FocusLockApplication).container
        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else {
            ""
        }

        setContent {
            FocusLockTheme {
                var captureState by remember { mutableStateOf<ShareCaptureState>(ShareCaptureState.Loading) }

                LaunchedEffect(sharedText) {
                    val parsed = InstagramUrlParser.parseSharedText(sharedText)
                    if (parsed == null) {
                        captureState = ShareCaptureState.InvalidLink
                    } else {
                        val existing = withContext(Dispatchers.IO) {
                            container.savedContentRepository.getSavedPostBySourceUrl(parsed.normalizedUrl)
                        }
                        if (existing != null) {
                            captureState = ShareCaptureState.Duplicate(existing)
                        } else {
                            captureState = ShareCaptureState.Ready(
                                parsed = parsed,
                                availableTags = VaultTags.PRESET_TAGS
                            )
                        }
                    }
                }

                when (val state = captureState) {
                    is ShareCaptureState.Loading -> Unit

                    is ShareCaptureState.InvalidLink -> {
                        FocusModalSheet(
                            title = "Lien non reconnu",
                            saveButtonText = "Fermer et revenir",
                            rightActionSymbol = "⚠",
                            onDismissRequest = { finish() },
                            onSaveClick = { finish() }
                        ) {
                            ElevatedCard {
                                Text(
                                    text = "LIEN INSTAGRAM INVALIDE",
                                    style = FocusTheme.typography.monoTag,
                                    color = FocusTheme.colors.softCoral
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Seuls les liens https vers un post, un Reel ou une vidéo Instagram (instagram.com/p/..., /reel/..., /tv/...) peuvent être ajoutés au Coffre.",
                                    style = FocusTheme.typography.bodySecondary,
                                    color = FocusTheme.colors.ink
                                )
                            }
                        }
                    }

                    is ShareCaptureState.Duplicate -> {
                        FocusModalSheet(
                            title = "Déjà dans le coffre",
                            saveButtonText = "Retourner à Instagram",
                            rightActionSymbol = "✓",
                            onDismissRequest = { finish() },
                            onSaveClick = { finish() }
                        ) {
                            ElevatedCard {
                                Text(
                                    text = "DOUBLON DÉTECTÉ",
                                    style = FocusTheme.typography.monoTag,
                                    color = FocusTheme.colors.mintClarityStart
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ce contenu est déjà enregistré sous le titre :",
                                    style = FocusTheme.typography.bodySecondary,
                                    color = FocusTheme.colors.mutedInk
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "« ${state.existingPost.userTitle} »",
                                    style = FocusTheme.typography.cardTitleSerif,
                                    color = FocusTheme.colors.ink
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tag : ${state.existingPost.tag}",
                                    style = FocusTheme.typography.monoMetric,
                                    color = FocusTheme.colors.electricAzureStart
                                )
                            }
                        }
                    }

                    is ShareCaptureState.Ready -> {
                        ShareCaptureFormSheet(
                            parsed = state.parsed,
                            initialTags = state.availableTags,
                            onDismiss = { finish() },
                            onSave = { title, tag, note ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val newPost = SavedPost(
                                        id = "vault-${UUID.randomUUID()}",
                                        sourceUrl = state.parsed.normalizedUrl,
                                        extractedCaption = state.parsed.extractedCaption,
                                        userTitle = title.trim(),
                                        personalNote = note.trim().takeIf { it.isNotEmpty() },
                                        tag = tag.trim(),
                                        savedAt = container.clock.instant(),
                                        isReviewed = false,
                                        isFavorite = false,
                                        openCount = 0
                                    )
                                    val res = container.savedContentRepository.insertIfAbsent(newPost)
                                    withContext(Dispatchers.Main) {
                                        if (res is VaultSaveResult.Duplicate) {
                                            captureState = ShareCaptureState.Duplicate(res.existingPost)
                                        } else {
                                            finish()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareCaptureFormSheet(
    parsed: ParsedInstagramShare,
    initialTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, tag: String, note: String) -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography

    var title by remember {
        mutableStateOf(
            parsed.extractedCaption
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(55)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: ""
        )
    }
    var tagsList by remember { mutableStateOf(initialTags) }
    var selectedTag by remember { mutableStateOf(initialTags.first()) }
    var customTagInput by remember { mutableStateOf("") }
    var personalNote by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    FocusModalSheet(
        title = "Sauvegarder dans le Coffre",
        saveButtonText = "Enregistrer dans le Coffre",
        rightActionSymbol = "📥",
        onDismissRequest = onDismiss,
        onSaveClick = {
            if (title.isBlank()) {
                errorText = "Un titre explicite est obligatoire pour retrouver ce post hors-ligne."
                return@FocusModalSheet
            }
            onSave(title, selectedTag, personalNote)
        }
    ) {
        if (errorText != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.softCoral.copy(alpha = 0.16f))
                    .border(1.dp, colors.softCoral, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(errorText!!, style = typo.bodySecondary, color = colors.softCoral)
            }
        }

        // Carte 1 : Titre obligatoire & Lien normalisé
        ElevatedCard {
            Text("TITRE EXPLICITE (OBLIGATOIRE)", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    errorText = null
                },
                label = { Text("Ex. Routine mobilité épaules / Subjonctif espagnol") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.electricAzureStart,
                    unfocusedBorderColor = colors.steelLine,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = parsed.normalizedUrl,
                style = typo.monoTag,
                color = colors.electricAzureStart
            )
        }

        // Carte 2 : Pastilles de Tags (Préréglages + Création personnalisée)
        ElevatedCard {
            Text("TAG DE CLASSEMENT", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(10.dp))
            tagsList.chunked(3).forEach { rowTags ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTags.forEach { tagItem ->
                        val active = selectedTag.equals(tagItem, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (active) colors.electricAzureStart else colors.deepSlate)
                                .border(
                                    1.dp,
                                    if (active) colors.electricAzureEnd else colors.steelLine,
                                    CircleShape
                                )
                                .clickable { selectedTag = tagItem }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tagItem,
                                style = typo.monoTag,
                                color = if (active) colors.nightAbyss else colors.ink,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customTagInput,
                    onValueChange = { customTagInput = it },
                    placeholder = { Text("Créer un nouveau tag...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.mintClarityStart)
                        .clickable {
                            val clean = customTagInput.trim()
                            if (clean.isNotEmpty()) {
                                if (tagsList.none { it.equals(clean, ignoreCase = true) }) {
                                    tagsList = tagsList + clean
                                }
                                selectedTag = clean
                                customTagInput = ""
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Text("+ Tag", style = typo.monoTag, color = colors.mintOnButton)
                }
            }
        }

        // Carte 3 : Note personnelle optionnelle & Légende extraite
        ElevatedCard {
            Text("NOTE PERSONNELLE (OPTIONNEL)", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = personalNote,
                onValueChange = { personalNote = it },
                label = { Text("Pourquoi ce post est-il utile ? Que dois-tu appliquer ?") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            if (!parsed.extractedCaption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("LÉGENDE EXTRAITE :", style = typo.monoTag, color = colors.mutedInk)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = parsed.extractedCaption,
                    style = typo.bodySecondary,
                    color = colors.mutedInk,
                    maxLines = 4
                )
            }
        }
    }
}
