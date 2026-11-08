package com.example.focuslock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val ColorTokens = FocusColors()

private val FocusDarkColorScheme = darkColorScheme(
    primary = ColorTokens.electricAzureStart,
    secondary = ColorTokens.mintClarityStart,
    tertiary = ColorTokens.electricAzureEnd,
    background = ColorTokens.nightAbyss,
    surface = ColorTokens.deepSlate,
    surfaceVariant = ColorTokens.elevatedCard,
    error = ColorTokens.softCoral,
    onPrimary = ColorTokens.nightAbyss,
    onSecondary = ColorTokens.mintOnButton,
    onBackground = ColorTokens.ink,
    onSurface = ColorTokens.ink,
    onSurfaceVariant = ColorTokens.mutedInk,
    outline = ColorTokens.steelLine
)

object FocusTheme {
    val colors: FocusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current

    val typography: FocusTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusTypography.current
}

@Composable
fun FocusLockTheme(
    content: @Composable () -> Unit
) {
    val focusColors = FocusColors()
    val focusTypography = FocusTypography()

    CompositionLocalProvider(
        LocalFocusColors provides focusColors,
        LocalFocusTypography provides focusTypography
    ) {
        MaterialTheme(
            colorScheme = FocusDarkColorScheme,
            typography = MaterialBridgeTypography,
            content = content
        )
    }
}