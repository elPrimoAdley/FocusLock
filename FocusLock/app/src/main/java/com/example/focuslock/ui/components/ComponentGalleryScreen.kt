package com.example.focuslock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.ui.theme.FocusLockTheme
import com.example.focuslock.ui.theme.FocusTheme

@Composable
fun ComponentGalleryScreen() {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    var switchState by remember { mutableStateOf(true) }
    var chipDone by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.nightAbyss)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("1.2 — 8 Tokens de Couleur", style = typo.screenTitle, color = colors.ink)
        }

        item {
            ElevatedCard {
                val swatches = listOf(
                    "Night Abyss (#070B14)" to colors.nightAbyss,
                    "Deep Slate (#0F172A)" to colors.deepSlate,
                    "Elevated Card (#162038)" to colors.elevatedCard,
                    "Steel Line (#283750)" to colors.steelLine,
                    "Electric Azure (#38BDF8)" to colors.electricAzureStart,
                    "Mint Clarity (#6FE6C4)" to colors.mintClarityStart,
                    "Soft Coral (#F87171)" to colors.softCoral,
                    "Ink & Muted (#F8FAFC)" to colors.ink
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    swatches.forEach { (name, color) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, colors.steelLine, CircleShape)
                            )
                            Text(name, style = typo.monoMetric, color = colors.ink)
                        }
                    }
                }
            }
        }

        item {
            Text("1.3 — Rôles Typographiques", style = typo.screenTitle, color = colors.ink)
        }

        item {
            ElevatedCard {
                Text("01:45:00", style = typo.timerDisplay, color = colors.ink)
                Text("Titre Fraunces Serif", style = typo.screenTitle, color = colors.ink)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Corps principal Inter Medium (UI)", style = typo.bodyPrimary, color = colors.ink)
                Text("Légende secondaire Inter Regular", style = typo.bodySecondary, color = colors.mutedInk)
                Spacer(modifier = Modifier.height(6.dp))
                Text("IBM PLEX MONO • 450 / 500 XP", style = typo.monoMetric, color = colors.electricAzureStart)
            }
        }

        item {
            Text("1.4 — Composants Atomiques", style = typo.screenTitle, color = colors.ink)
        }

        item {
            ElevatedCard {
                Text("Badges de Priorité", style = typo.bodyPrimary, color = colors.ink)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriorityBadge(QuestPriority.P1)
                    PriorityBadge(QuestPriority.P2)
                    PriorityBadge(QuestPriority.P3)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Puces d'état & Switch", style = typo.bodyPrimary, color = colors.ink)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(isCompleted = chipDone, onClick = { chipDone = !chipDone })
                        StatusChip(isCompleted = true)
                    }
                    FocusSwitch(checked = switchState, onCheckedChange = { switchState = it })
                }

                Spacer(modifier = Modifier.height(16.dp))
                MintPillButton(text = "Enregistrer (MintPillButton)", onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ComponentGalleryPreview() {
    FocusLockTheme {
        ComponentGalleryScreen()
    }
}