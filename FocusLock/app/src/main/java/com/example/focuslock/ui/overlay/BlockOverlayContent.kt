package com.example.focuslock.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.theme.FocusTheme

@Composable
fun BlockOverlayContent(
    blockedPackage: String,
    openCount: Int,
    onDismiss: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.nightAbyss)
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Pilule supérieure d'état
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.elevatedCard)
                    .border(1.dp, colors.electricAzureStart.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "🔒 MODE STRICT • PREUVE DE FAISABILITÉ",
                    style = typo.monoTag,
                    color = colors.electricAzureStart
                )
            }

            // Grand titre Fraunces
            Text(
                text = "C'est ton temps d'étude.",
                style = typo.screenTitle,
                color = colors.ink,
                textAlign = TextAlign.Center
            )

            // Carte d'information contextuelle
            ElevatedCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "APPLICATION INTERCEPTÉE",
                        style = typo.monoTag,
                        color = colors.mutedInk
                    )
                    Text(
                        text = blockedPackage,
                        style = typo.monoMetric,
                        color = colors.softCoral
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tentative #$openCount durant cette session de test",
                        style = typo.bodySecondary,
                        color = colors.ink,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bouton principal Fermer en dégradé Mint Clarity
            MintPillButton(
                text = "Fermer l'overlay (Retour au calme)",
                onClick = onDismiss
            )
        }
    }
}