package com.example.focuslock.ui.quests

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
import androidx.compose.ui.unit.dp
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.ui.components.*
import com.example.focuslock.ui.navigation.Destination
import com.example.focuslock.ui.navigation.TopSectionBar
import com.example.focuslock.ui.theme.FocusTheme

@Composable
fun QuestsScreen(onOpenDrawer: () -> Unit) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    var showSheet by remember { mutableStateOf(false) }

    // États factices de la démo ModalBottomSheet (1.10)
    var selectedPriority by remember { mutableStateOf(QuestPriority.P1) }
    val daysLabels = listOf("L", "M", "M", "J", "V", "S", "D")
    var activeDays by remember { mutableStateOf(setOf(0, 1, 2, 3, 4)) }
    var strictMalusEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.nightAbyss)
            .systemBarsPadding()
    ) {
        TopSectionBar(title = Destination.Quests.title, onOpenDrawer = onOpenDrawer)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard {
                Text("Système de Quêtes & Malus", style = typo.cardTitleSerif, color = colors.ink)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Teste l'ouverture du FocusModalSheet standardisé (85 % de hauteur, fond Deep Slate, scrim 65 % et bouton Enregistrer persistant).",
                    style = typo.bodySecondary,
                    color = colors.mutedInk
                )
                Spacer(modifier = Modifier.height(16.dp))
                MintPillButton(
                    text = "+ Nouvelle Quête / Règle (Ouvrir Sheet)",
                    onClick = { showSheet = true }
                )
            }
        }
    }

    if (showSheet) {
        FocusModalSheet(
            title = "Nouvelle Quête Disciplinaire",
            onDismissRequest = { showSheet = false },
            onSaveClick = { showSheet = false }
        ) {
            // Carte 1 : Degré de Priorité & Gain d'XP
            ElevatedCard {
                Text("CARTE 1 — DEGRÉ DE PRIORITÉ", style = typo.monoTag, color = colors.mutedInk)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Choisis l'impact en XP et la sévérité du malus en cas de retard :",
                    style = typo.bodySecondary,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuestPriority.entries.forEach { priority ->
                        val isSelected = selectedPriority == priority
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.deepSlate else colors.nightAbyss)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.electricAzureStart else colors.steelLine,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPriority = priority }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PriorityBadge(priority = priority)
                        }
                    }
                }
            }

            // Carte 2 : Jours actifs (L–D) & Verrouillage strict
            ElevatedCard {
                Text("CARTE 2 — RÉCURRENCE & MALUS (L–D)", style = typo.monoTag, color = colors.mutedInk)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysLabels.forEachIndexed { index, label ->
                        val isDayActive = activeDays.contains(index)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isDayActive) colors.electricAzureStart else colors.deepSlate)
                                .border(
                                    1.dp,
                                    if (isDayActive) colors.electricAzureEnd else colors.steelLine,
                                    CircleShape
                                )
                                .clickable {
                                    activeDays = if (isDayActive) activeDays - index else activeDays + index
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = typo.monoMetric,
                                color = if (isDayActive) colors.nightAbyss else colors.mutedInk
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Dette de Focus automatique", style = typo.bodyPrimary, color = colors.ink)
                        Text(
                            "Déclenche 30 min de blocage strict si l'échéance est dépassée",
                            style = typo.bodySecondary,
                            color = colors.mutedInk
                        )
                    }
                    FocusSwitch(
                        checked = strictMalusEnabled,
                        onCheckedChange = { strictMalusEnabled = it }
                    )
                }
            }
        }
    }
}