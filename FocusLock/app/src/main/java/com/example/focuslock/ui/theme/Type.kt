package com.example.focuslock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.Font
import com.example.focuslock.R

val FrauncesFontFamily = FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold))
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)
val IbmPlexMonoFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium)
)

@Immutable
data class FocusTypography(
    // Rôle Fraunces : Minuteur géant & titres de sections
    val timerDisplay: TextStyle = TextStyle(
        fontFamily = FrauncesFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 50.sp
    ),
    val screenTitle: TextStyle = TextStyle(
        fontFamily = FrauncesFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    val cardTitleSerif: TextStyle = TextStyle(
        fontFamily = FrauncesFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    // Rôle Inter : Corps UI, formulaires et boutons
    val bodyPrimary: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    val bodySecondary: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    val buttonLabel: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    // Rôle IBM Plex Mono : Compteurs XP, horaires, séries et badges
    val monoMetric: TextStyle = TextStyle(
        fontFamily = IbmPlexMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    val monoTag: TextStyle = TextStyle(
        fontFamily = IbmPlexMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp
    )
)

val LocalFocusTypography = staticCompositionLocalOf { FocusTypography() }

val MaterialBridgeTypography = Typography(
    headlineMedium = FocusTypography().screenTitle,
    titleMedium = FocusTypography().cardTitleSerif,
    bodyLarge = FocusTypography().bodyPrimary,
    bodyMedium = FocusTypography().bodySecondary,
    labelLarge = FocusTypography().buttonLabel
)