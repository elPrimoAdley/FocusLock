package com.example.focuslock.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focuslock.domain.BlockReason
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.delay
import java.time.Instant

data class OverlayUiModel(
    val blockedPackageName: String,
    val appDisplayName: String,
    val reason: BlockReason,
    val ruleOrSessionId: String?,
    val ruleTitle: String,
    val customMessage: String,
    val statusPillOverride: String? = null,
    val targetEndInstant: Instant? = null,
    val totalDurationSeconds: Long = 3600L,
    val todayAttempts: Int = 1,
    val totalAttempts: Int = 1,
    val routineChecklist: List<QuestTask> = emptyList(),
    val startOfToday: Instant = Instant.EPOCH
)

@Composable
fun BlockOverlayContent(
    model: OverlayUiModel,
    onToggleChecklistItem: (QuestTask) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    var showEmergencyPlaceholder by remember { mutableStateOf(false) }

    // 2.20 : Cycle de respiration sinusoïdal de 7 secondes (3500 ms inspiration + 3500 ms expiration)
    val infiniteTransition = rememberInfiniteTransition(label = "breathe_7s")
    val breathePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_halo"
    )

    // Compte à rebours temps réel si une fin de session/routine est définie
    var remainingSeconds by remember(model.targetEndInstant) {
        mutableLongStateOf(computeRemainingSeconds(model.targetEndInstant))
    }
    LaunchedEffect(model.targetEndInstant) {
        while (model.targetEndInstant != null) {
            remainingSeconds = computeRemainingSeconds(model.targetEndInstant)
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.nightAbyss)
    ) {
        // Halo radial atmosphérique animé à la base de l'écran
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * (0.65f + 0.25f * breathePhase)
            val center = Offset(x = size.width / 2f, y = size.height * (1.05f - 0.08f * breathePhase))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.electricAzureStart.copy(alpha = 0.28f * (0.5f + 0.5f * breathePhase)),
                        colors.mintClarityStart.copy(alpha = 0.12f * breathePhase),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        if (showEmergencyPlaceholder) {
            EmergencyPlaceholderScreen(
                onBackToLock = { showEmergencyPlaceholder = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Pilule supérieure d'état
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.elevatedCard.copy(alpha = 0.9f))
                        .border(1.dp, statusColorFor(model.reason, colors), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = model.statusPillOverride ?: statusBadgeLabelFor(model.reason),
                        style = typo.monoTag,
                        color = statusColorFor(model.reason, colors)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Anneau de Compte à Rebours (180 x 180 dp) + Temps en Fraunces 38 sp
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val progress = if (model.totalDurationSeconds > 0 && remainingSeconds > 0) {
                        (remainingSeconds.toFloat() / model.totalDurationSeconds.toFloat()).coerceIn(0.05f, 1f)
                    } else {
                        1f
                    }

                    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        val strokePx = 10.dp.toPx()
                        drawArc(
                            color = colors.elevatedCard,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokePx)
                        )
                        drawArc(
                            brush = colors.electricAzureGradient,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = if (remainingSeconds > 0) formatTime(remainingSeconds) else "VERROUILLÉ",
                            style = typo.timerDisplay.copy(fontSize = if (remainingSeconds > 0) 38.sp else 22.sp),
                            color = colors.ink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = model.ruleTitle,
                            style = typo.bodySecondary,
                            color = colors.mutedInk,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Message personnalisé bienveillant + Cause + Compteur de tentatives en Mono (2.19 & 2.20)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = model.customMessage,
                        style = typo.cardTitleSerif,
                        color = colors.ink,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Bloqué par « ${model.ruleTitle} »",
                        style = typo.bodySecondary,
                        color = colors.electricAzureStart,
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.deepSlate.copy(alpha = 0.85f))
                            .border(1.dp, colors.steelLine, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Tentative d'ouverture de ${model.appDisplayName} : " +
                                "${model.todayAttempts}× aujourd'hui | ${model.totalAttempts}× au total",
                            style = typo.monoMetric.copy(fontSize = 12.sp),
                            color = colors.mutedInk,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Checklist Hors-Écran Intégrée (2.21 : affichée si le blocage provient d'une routine)
                if (model.reason == BlockReason.MORNING_ROUTINE && model.routineChecklist.isNotEmpty()) {
                    ElevatedCard {
                        val doneCount = model.routineChecklist.count { it.completedAt != null }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CHECKLIST HORS-ÉCRAN REQUISE",
                                style = typo.monoTag,
                                color = colors.mintClarityStart
                            )
                            Text(
                                text = "$doneCount / ${model.routineChecklist.size}",
                                style = typo.monoMetric,
                                color = colors.ink
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            model.routineChecklist.forEach { item ->
                                val isDone = item.completedAt != null && !item.completedAt.isBefore(model.startOfToday)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.deepSlate)
                                        .border(
                                            1.dp,
                                            if (isDone) colors.mintClarityStart else colors.steelLine,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onToggleChecklistItem(item) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Pastille circulaire dot-check
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (isDone) colors.mintClarityStart else Color.Transparent)
                                            .border(
                                                1.5.dp,
                                                if (isDone) colors.mintClarityStart else colors.mutedInk,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isDone) {
                                            Text(
                                                text = "✓",
                                                color = colors.mintOnButton,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Text(
                                        text = item.title,
                                        style = typo.bodyPrimary,
                                        color = if (isDone) colors.mutedInk else colors.ink,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 5. Actions de bas d'écran : Bouton principal + Lien discret « Urgence / Bris de glace »
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MintPillButton(
                        text = "Fermer / Retour au calme",
                        onClick = onDismiss
                    )

                    Text(
                        text = "Urgence / Bris de glace",
                        style = typo.bodySecondary,
                        color = colors.mutedInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEmergencyPlaceholder = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmergencyPlaceholderScreen(onBackToLock: () -> Unit) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ElevatedCard {
            Text(
                text = "🔒 SAS BRIS DE GLACE (PHASE 5)",
                style = typo.monoTag,
                color = colors.softCoral
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Clavier PIN PBKDF2 & Délai Mindful Edits",
                style = typo.cardTitleSerif,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Cet écran accueillera en Phase 5 le sas de décompression de 10 à 60 secondes et le clavier PIN circulaire indépendant pour déverrouiller 15 minutes en cas d'urgence.",
                style = typo.bodySecondary,
                color = colors.mutedInk
            )
            Spacer(modifier = Modifier.height(16.dp))
            MintPillButton(
                text = "Retourner à l'écran de concentration",
                onClick = onBackToLock
            )
        }
    }
}

private fun statusBadgeLabelFor(reason: BlockReason): String = when (reason) {
    BlockReason.ACTIVE_FOCUS_SESSION -> "🔒 MODE STRICT • SESSION D'ÉTUDE EN COURS"
    BlockReason.FOCUS_DEBT_PENALTY -> "⚠ DETTE DE FOCUS • PÉNALITÉ AUTOMATIQUE"
    BlockReason.MORNING_ROUTINE -> "🌅 MORNING ROUTINE • CHECKLIST REQUISE"
    BlockReason.SABBATICAL_OR_DETOX -> "🌿 PAUSE DÉTOX • JOUR OFF / SABBATIQUE"
    BlockReason.SCHEDULE_OR_QUOTA -> "⏳ PLANNING & QUOTA • ACCÈS VERROUILLÉ"
    BlockReason.VIEWING_WINDOW_ENDED -> "📥 COFFRE DE CONTENU • CONSULTATION TERMINÉE"
}

@Composable
private fun statusColorFor(
    reason: BlockReason,
    colors: com.example.focuslock.ui.theme.FocusColors
): Color = when (reason) {
    BlockReason.FOCUS_DEBT_PENALTY -> colors.softCoral
    BlockReason.MORNING_ROUTINE, BlockReason.VIEWING_WINDOW_ENDED -> colors.mintClarityStart
    else -> colors.electricAzureStart
}

private fun computeRemainingSeconds(targetEndInstant: Instant?): Long {
    if (targetEndInstant == null) return 0L
    val diff = targetEndInstant.epochSecond - Instant.now().epochSecond
    return diff.coerceAtLeast(0L)
}

private fun formatTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}