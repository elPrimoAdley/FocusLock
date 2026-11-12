package com.example.focuslock.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focuslock.domain.store.GamificationState
import com.example.focuslock.domain.store.GamificationStore
import com.example.focuslock.engine.security.PinSecurityEngine
import com.example.focuslock.engine.security.PinVerificationResult
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant

/**
 * Contenu autonome du Sas Mindful Edits + Clavier PIN Circulaire (utilisable à la fois
 * dans un FocusModalSheet et en plein écran dans l'Overlay de verrouillage).
 */
@Composable
fun MindfulPinGateContent(
    actionTitle: String,
    actionDescription: String,
    gamificationState: GamificationState,
    gamificationStore: GamificationStore,
    clock: Clock,
    onAuthorized: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val scope = rememberCoroutineScope()

    val totalDelaySeconds = gamificationState.mindfulEditDelaySeconds.coerceIn(10, 60)
    var remainingDelaySeconds by remember(actionTitle) { mutableIntStateOf(totalDelaySeconds) }
    var enteredPin by remember { mutableStateOf("") }
    var feedbackError by remember { mutableStateOf<String?>(null) }
    var nowInstant by remember { mutableStateOf(clock.instant()) }

    // Décompte incompressible Mindful Edits + rafraîchissement du lockout PIN
    LaunchedEffect(actionTitle) {
        while (true) {
            nowInstant = clock.instant()
            if (remainingDelaySeconds > 0) {
                delay(1_000L)
                remainingDelaySeconds--
            } else {
                delay(500L)
            }
        }
    }

    val lockoutUntil = gamificationState.pinLockoutUntilTimestamp?.let(Instant::ofEpochMilli)
    val lockoutRemainingSeconds = if (lockoutUntil != null && nowInstant.isBefore(lockoutUntil)) {
        (lockoutUntil.epochSecond - nowInstant.epochSecond).coerceAtLeast(1L)
    } else {
        0L
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // En-tête avec icône de cadenas dans un cercle ardoise (44 dp) — Section 3.2
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.elevatedCard)
                .border(1.dp, colors.steelLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🔒", fontSize = 18.sp)
        }

        Text(
            text = actionTitle,
            style = typo.screenTitle,
            color = colors.ink,
            textAlign = TextAlign.Center
        )

        Text(
            text = actionDescription,
            style = typo.bodySecondary,
            color = colors.mutedInk,
            textAlign = TextAlign.Center
        )

        if (remainingDelaySeconds > 0) {
            // ==================== ÉTAPE 1 : SAS MINDFUL EDITS (10s - 60s) ====================
            val progress by animateFloatAsState(
                targetValue = remainingDelaySeconds.toFloat() / totalDelaySeconds.toFloat(),
                animationSpec = tween(durationMillis = 900, easing = LinearEasing),
                label = "mindful_countdown"
            )

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${remainingDelaySeconds}s",
                        style = typo.timerDisplay.copy(fontSize = 36.sp),
                        color = colors.ink
                    )
                    Text(
                        text = "RESPIRATION",
                        style = typo.monoTag,
                        color = colors.electricAzureStart
                    )
                }
            }

            ElevatedCard {
                Text(
                    text = "SAS MINDFUL EDITS ACTIF",
                    style = typo.monoTag,
                    color = colors.mintClarityStart
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ce délai volontaire brise l'impulsivité. Demande-toi si cette modification sert réellement tes objectifs d'aujourd'hui.",
                    style = typo.bodySecondary,
                    color = colors.ink
                )
            }

            MintPillButton(
                text = "Renoncer et garder ma discipline",
                onClick = onCancel
            )
        } else if (!gamificationState.isPinConfigured) {
            // Aucun PIN configuré : après le délai Mindful Edits, bouton de confirmation explicite
            ElevatedCard {
                Text(
                    text = "DÉLAI DE RÉFLEXION ÉCOULÉ",
                    style = typo.monoTag,
                    color = colors.electricAzureStart
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tu n'as pas encore défini de code PIN strict (configurable dans Profil & Sécurité). Confirme-tu cette action ?",
                    style = typo.bodySecondary,
                    color = colors.ink
                )
            }
            MintPillButton(
                text = "Garder le verrouillage (Recommandé)",
                onClick = onCancel
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.deepSlate)
                    .border(1.dp, colors.softCoral, CircleShape)
                    .clickable { onAuthorized() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Confirmer l'assouplissement",
                    style = typo.buttonLabel,
                    color = colors.softCoral
                )
            }
        } else {
            // ==================== ÉTAPE 2 : CLAVIER PIN CIRCULAIRE (3 x 4) ====================
            if (lockoutRemainingSeconds > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.softCoral.copy(alpha = 0.16f))
                        .border(1.dp, colors.softCoral, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "⏳ Trop d'essais incorrects. Réessaie dans ${lockoutRemainingSeconds}s.",
                        style = typo.monoMetric,
                        color = colors.softCoral,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (feedbackError != null) {
                Text(
                    text = feedbackError!!,
                    style = typo.monoMetric,
                    color = colors.softCoral,
                    textAlign = TextAlign.Center
                )
            }

            // Indicateurs ronds (pin-dots) qui se remplissent en bleu électrique/menthe
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) colors.mintClarityStart else colors.deepSlate
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (filled) colors.electricAzureStart else colors.steelLine,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Pavé numérique circulaire 3 x 4 en IBM Plex Mono (19 sp)
            CircularPinKeypad(
                enabled = lockoutRemainingSeconds == 0L,
                onDigitClick = { digit ->
                    if (enteredPin.length < 4) {
                        feedbackError = null
                        enteredPin += digit
                        if (enteredPin.length == 4) {
                            val candidate = enteredPin
                            scope.launch(Dispatchers.Default) {
                                val res = PinSecurityEngine.verifyPin(
                                    candidatePin = candidate,
                                    storedHashBase64 = gamificationState.pinHashPBKDF2.orEmpty(),
                                    storedSaltBase64 = gamificationState.pinSalt.orEmpty(),
                                    currentFailedAttempts = gamificationState.failedPinAttempts,
                                    currentLockoutUntil = lockoutUntil,
                                    now = clock.instant()
                                )
                                when (res) {
                                    is PinVerificationResult.Success -> {
                                        gamificationStore.update {
                                            it.copy(
                                                failedPinAttempts = 0,
                                                pinLockoutUntilTimestamp = null
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            enteredPin = ""
                                            onAuthorized()
                                        }
                                    }
                                    is PinVerificationResult.InvalidPin -> {
                                        gamificationStore.update {
                                            it.copy(
                                                failedPinAttempts = res.failedAttempts,
                                                pinLockoutUntilTimestamp = res.lockedOutUntil?.toEpochMilli()
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            enteredPin = ""
                                            feedbackError = if (res.remainingFreeAttempts > 0) {
                                                "Code PIN incorrect (${res.remainingFreeAttempts} essai(s) avant blocage)"
                                            } else {
                                                "Code PIN verrouillé temporairement"
                                            }
                                        }
                                    }
                                    is PinVerificationResult.CurrentlyLockedOut -> {
                                        withContext(Dispatchers.Main) {
                                            enteredPin = ""
                                            feedbackError = "Verrouillé pour encore ${res.remainingSeconds}s"
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                onClearClick = {
                    enteredPin = ""
                    feedbackError = null
                },
                onBackspaceClick = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                }
            )

            Text(
                text = "Annuler et revenir",
                style = typo.bodySecondary,
                color = colors.mutedInk,
                modifier = Modifier
                    .clickable { onCancel() }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun CircularPinKeypad(
    enabled: Boolean = true,
    onDigitClick: (String) -> Unit,
    onClearClick: () -> Unit,
    onBackspaceClick: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography

    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.elevatedCard)
                            .border(1.dp, colors.steelLine, CircleShape)
                            .clickable(enabled = enabled) {
                                when (key) {
                                    "C" -> onClearClick()
                                    "⌫" -> onBackspaceClick()
                                    else -> onDigitClick(key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            style = typo.monoMetric.copy(fontSize = 19.sp),
                            color = if (enabled) colors.ink else colors.mutedInk
                        )
                    }
                }
            }
        }
    }
}
