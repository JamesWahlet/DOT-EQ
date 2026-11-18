package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class NothingColors(
    val isDark: Boolean,
    val canvas: Color,
    val card: Color,
    val cardSecondary: Color,
    val cardBorder: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val pillTrack: Color,
    val pillActiveContainer: Color,
    val pillActiveContent: Color,
    val red: Color,
    val yellow: Color,
    val blue: Color,
    val green: Color,
    val greenGlow: Color,
    val dimRed: Color,
    val frostedCard: Color,
    val graphBackdrop: Color,
    val graphGridDot: Color
)

private val LightNothingColors = NothingColors(
    isDark = false,
    canvas = NothingMatteGrey,
    card = NothingOffWhite,
    cardSecondary = NothingIceWhite,
    cardBorder = NothingFogGrey,
    inkPrimary = NothingCharcoal,
    inkSecondary = NothingDimGrey,
    inkMuted = NothingSlate,
    pillTrack = NothingIceWhite,
    pillActiveContainer = NothingCharcoal,
    pillActiveContent = NothingPureWhite,
    red = NothingRed,
    yellow = NothingYellow,
    blue = NothingBlue,
    green = NothingGreen,
    greenGlow = NothingGreenGlow,
    dimRed = NothingDimRed,
    frostedCard = NothingFrostLight,
    graphBackdrop = NothingPureWhite,
    graphGridDot = NothingFogGrey
)

private val DarkNothingColors = NothingColors(
    isDark = true,
    canvas = NothingBlack,
    card = NothingCharcoal,
    cardSecondary = NothingDarkElevated,
    cardBorder = NothingDarkBorder,
    inkPrimary = NothingOffWhite,
    inkSecondary = NothingFogGrey,
    inkMuted = NothingSlate,
    pillTrack = NothingDarkElevated,
    pillActiveContainer = NothingOffWhite,
    pillActiveContent = NothingBlack,
    red = NothingRed,
    yellow = NothingYellow,
    blue = NothingBlue,
    green = NothingGreen,
    greenGlow = NothingGreenGlow,
    dimRed = NothingDimRed,
    frostedCard = NothingFrostDark,
    graphBackdrop = Color(0xFF0C0E11),
    graphGridDot = Color(0xFF282C33)
)

val LocalNothingColors = staticCompositionLocalOf { LightNothingColors }

private val LightM3Scheme = lightColorScheme(
    primary = NothingCharcoal,
    onPrimary = NothingPureWhite,
    primaryContainer = NothingIceWhite,
    onPrimaryContainer = NothingCharcoal,
    secondary = NothingYellow,
    onSecondary = NothingBlack,
    tertiary = NothingBlue,
    onTertiary = NothingPureWhite,
    error = NothingRed,
    onError = NothingPureWhite,
    background = NothingMatteGrey,
    onBackground = NothingCharcoal,
    surface = NothingOffWhite,
    onSurface = NothingCharcoal,
    surfaceVariant = NothingIceWhite,
    onSurfaceVariant = NothingDimGrey,
    outline = NothingFogGrey
)

private val DarkM3Scheme = darkColorScheme(
    primary = NothingOffWhite,
    onPrimary = NothingBlack,
    primaryContainer = NothingDarkElevated,
    onPrimaryContainer = NothingOffWhite,
    secondary = NothingYellow,
    onSecondary = NothingBlack,
    tertiary = NothingBlue,
    onTertiary = NothingPureWhite,
    error = NothingRed,
    onError = NothingPureWhite,
    background = NothingBlack,
    onBackground = NothingOffWhite,
    surface = NothingCharcoal,
    onSurface = NothingOffWhite,
    surfaceVariant = NothingDarkElevated,
    onSurfaceVariant = NothingFogGrey,
    outline = NothingDarkBorder
)

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Composable
fun DotEqTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val nothingColors = if (useDark) DarkNothingColors else LightNothingColors
    val m3Scheme = if (useDark) DarkM3Scheme else LightM3Scheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalNothingColors provides nothingColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = DotEqTypography,
            content = content
        )
    }
}
