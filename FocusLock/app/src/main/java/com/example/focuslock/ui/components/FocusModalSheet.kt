package com.example.focuslock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.focuslock.ui.theme.FocusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModalSheet(
    title: String,
    onDismissRequest: () -> Unit,
    onSaveClick: () -> Unit,
    saveButtonText: String = "Enregistrer",
    rightActionSymbol: String = "✎",
    onRightActionClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = FocusTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.deepSlate,
        scrimColor = Color(0xFF020617).copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.steelLine)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
        ) {
            // 1. En-tête fixe (bouton rond 36 dp gauche, titre centré, action droite)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.elevatedCard)
                        .border(1.dp, colors.steelLine, CircleShape)
                        .clickable(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = colors.ink, style = FocusTheme.typography.bodyPrimary)
                }

                Text(
                    text = title,
                    style = FocusTheme.typography.cardTitleSerif,
                    color = colors.ink
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.elevatedCard)
                        .border(1.dp, colors.steelLine, CircleShape)
                        .clickable(enabled = onRightActionClick != null) {
                            onRightActionClick?.invoke()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(rightActionSymbol, color = colors.mutedInk, style = FocusTheme.typography.bodyPrimary)
                }
            }

            HorizontalDivider(color = colors.steelLine, thickness = 1.dp)

            // 2. Corps défilant verticalement
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )

            // 3. Pied fixe avec MintPillButton toujours visible
            HorizontalDivider(color = colors.steelLine, thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.deepSlate)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                MintPillButton(
                    text = saveButtonText,
                    onClick = onSaveClick
                )
            }
        }
    }
}