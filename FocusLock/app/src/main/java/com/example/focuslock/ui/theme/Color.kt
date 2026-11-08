package com.example.focuslock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class FocusColors(
    val nightAbyss: Color = Color(0xFF070B14),
    val deepSlate: Color = Color(0xFF0F172A),
    val elevatedCard: Color = Color(0xFF162038),
    val steelLine: Color = Color(0xFF283750),
    val electricAzureStart: Color = Color(0xFF38BDF8),
    val electricAzureEnd: Color = Color(0xFF60A5FA),
    val mintClarityStart: Color = Color(0xFF6FE6C4),
    val mintClarityEnd: Color = Color(0xFF34D399),
    val mintOnButton: Color = Color(0xFF06281E),
    val softCoral: Color = Color(0xFFF87171),
    val ink: Color = Color(0xFFF8FAFC),
    val mutedInk: Color = Color(0xFF94A3B8)
) {
    val electricAzureGradient: Brush
        get() = Brush.linearGradient(listOf(electricAzureStart, electricAzureEnd))

    val mintClarityGradient: Brush
        get() = Brush.horizontalGradient(listOf(mintClarityStart, mintClarityEnd))
}

val LocalFocusColors = staticCompositionLocalOf { FocusColors() }