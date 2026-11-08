package com.example.focuslock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.ui.theme.FocusTheme

/**
 * Bouton pilule pleine largeur (54 dp), dégradé Mint Clarity (#6FE6C4 -> #34D399), texte #06281E.
 */
@Composable
fun MintPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = FocusTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(CircleShape)
            .background(
                brush = colors.mintClarityGradient,
                alpha = if (enabled) 1f else 0.45f
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = FocusTheme.typography.buttonLabel,
            color = colors.mintOnButton
        )
    }
}

/**
 * Carte sombre standardisée : fond #162038, rayon 18 dp, bordure 1 dp en #283750.
 */
@Composable
fun ElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = FocusTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Surface(
        color = colors.elevatedCard,
        shape = shape,
        border = BorderStroke(1.dp, colors.steelLine),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Puce d'état (En attente / Terminé).
 */
@Composable
fun StatusChip(
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = FocusTheme.colors
    val bgColor = if (isCompleted) colors.mintClarityStart.copy(alpha = 0.16f) else colors.deepSlate
    val borderColor = if (isCompleted) colors.mintClarityStart else colors.steelLine
    val textColor = if (isCompleted) colors.mintClarityStart else colors.mutedInk
    val label = if (isCompleted) "✓ Terminé" else "En attente"

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = FocusTheme.typography.monoTag,
            color = textColor
        )
    }
}

/**
 * Badge de priorité P1 (+100 XP), P2 (+50 XP), P3 (+20 XP).
 */
@Composable
fun PriorityBadge(
    priority: QuestPriority,
    showXp: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = FocusTheme.colors
    val accentColor: Color = when (priority) {
        QuestPriority.P1 -> colors.softCoral
        QuestPriority.P2 -> colors.electricAzureStart
        QuestPriority.P3 -> colors.mintClarityStart
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (showXp) "${priority.label} +${priority.xpReward}XP" else priority.label,
            style = FocusTheme.typography.monoTag,
            color = accentColor
        )
    }
}

/**
 * Switch thémé Midnight Blue & Mint.
 */
@Composable
fun FocusSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FocusTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.mintOnButton,
            checkedTrackColor = colors.mintClarityStart,
            checkedBorderColor = colors.mintClarityEnd,
            uncheckedThumbColor = colors.mutedInk,
            uncheckedTrackColor = colors.deepSlate,
            uncheckedBorderColor = colors.steelLine
        )
    )
}