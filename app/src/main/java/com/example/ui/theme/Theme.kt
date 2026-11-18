package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class NothingColors(
    val isDark: Boolean,
    val isMonochrome: Boolean = false,
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
    isMonochrome = false,
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
    isMonochrome = false,
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

// Tonal M3 fallback used by SYSTEM_UI on Android < 12 (API < 31)
private val SystemUiFallbackLightScheme = lightColorScheme()
private val SystemUiFallbackDarkScheme = darkColorScheme()

enum class ThemeMode(val label: String) {
    LIGHT("HELL"),
    DARK("DUNKEL"),
    SYSTEM("SYSTEM"),
    SYSTEM_UI("SYSTEM UI")
}

fun Color.toMonochrome(): Color {
    val luma = (0.299f * red + 0.587f * green + 0.114f * blue).coerceIn(0f, 1f)
    return Color(red = luma, green = luma, blue = luma, alpha = alpha)
}

fun contrastInkFor(background: Color): Color {
    val luma = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luma > 0.54f) Color(0xFF15171A) else Color.White
}

fun NothingColors.toMonochromePalette(): NothingColors = copy(
    isMonochrome = true,
    canvas = canvas.toMonochrome(),
    card = card.toMonochrome(),
    cardSecondary = cardSecondary.toMonochrome(),
    cardBorder = cardBorder.toMonochrome(),
    inkPrimary = inkPrimary.toMonochrome(),
    inkSecondary = inkSecondary.toMonochrome(),
    inkMuted = inkMuted.toMonochrome(),
    pillTrack = pillTrack.toMonochrome(),
    pillActiveContainer = pillActiveContainer.toMonochrome(),
    pillActiveContent = pillActiveContent.toMonochrome(),
    red = red.toMonochrome(),
    yellow = yellow.toMonochrome(),
    blue = blue.toMonochrome(),
    green = green.toMonochrome(),
    greenGlow = greenGlow.toMonochrome(),
    dimRed = dimRed.toMonochrome(),
    frostedCard = frostedCard.toMonochrome(),
    graphBackdrop = graphBackdrop.toMonochrome(),
    graphGridDot = graphGridDot.toMonochrome()
)

fun ColorScheme.toMonochromeColorScheme(): ColorScheme = copy(
    primary = primary.toMonochrome(),
    onPrimary = onPrimary.toMonochrome(),
    primaryContainer = primaryContainer.toMonochrome(),
    onPrimaryContainer = onPrimaryContainer.toMonochrome(),
    inversePrimary = inversePrimary.toMonochrome(),
    secondary = secondary.toMonochrome(),
    onSecondary = onSecondary.toMonochrome(),
    secondaryContainer = secondaryContainer.toMonochrome(),
    onSecondaryContainer = onSecondaryContainer.toMonochrome(),
    tertiary = tertiary.toMonochrome(),
    onTertiary = onTertiary.toMonochrome(),
    tertiaryContainer = tertiaryContainer.toMonochrome(),
    onTertiaryContainer = onTertiaryContainer.toMonochrome(),
    background = background.toMonochrome(),
    onBackground = onBackground.toMonochrome(),
    surface = surface.toMonochrome(),
    onSurface = onSurface.toMonochrome(),
    surfaceVariant = surfaceVariant.toMonochrome(),
    onSurfaceVariant = onSurfaceVariant.toMonochrome(),
    surfaceTint = surfaceTint.toMonochrome(),
    inverseSurface = inverseSurface.toMonochrome(),
    inverseOnSurface = inverseOnSurface.toMonochrome(),
    error = error.toMonochrome(),
    onError = onError.toMonochrome(),
    errorContainer = errorContainer.toMonochrome(),
    onErrorContainer = onErrorContainer.toMonochrome(),
    outline = outline.toMonochrome(),
    outlineVariant = outlineVariant.toMonochrome(),
    scrim = scrim.toMonochrome(),
    surfaceBright = surfaceBright.toMonochrome(),
    surfaceContainer = surfaceContainer.toMonochrome(),
    surfaceContainerHigh = surfaceContainerHigh.toMonochrome(),
    surfaceContainerHighest = surfaceContainerHighest.toMonochrome(),
    surfaceContainerLow = surfaceContainerLow.toMonochrome(),
    surfaceContainerLowest = surfaceContainerLowest.toMonochrome(),
    surfaceDim = surfaceDim.toMonochrome()
)

fun dynamicSchemeToNothingColors(scheme: ColorScheme, useDark: Boolean): NothingColors {
    return NothingColors(
        isDark = useDark,
        isMonochrome = false,
        canvas = scheme.background,
        card = scheme.surfaceContainer,
        cardSecondary = scheme.surfaceContainerHigh,
        cardBorder = scheme.outlineVariant,
        inkPrimary = scheme.onBackground,
        inkSecondary = scheme.onSurfaceVariant,
        inkMuted = scheme.outline,
        pillTrack = scheme.surfaceContainerHighest,
        pillActiveContainer = scheme.primary,
        pillActiveContent = scheme.onPrimary,
        red = scheme.primary,
        yellow = scheme.tertiary,
        blue = scheme.secondary,
        green = NothingGreen,
        greenGlow = NothingGreenGlow,
        dimRed = scheme.errorContainer,
        frostedCard = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
        graphBackdrop = if (useDark) scheme.surfaceContainerLow else scheme.surface,
        graphGridDot = scheme.outlineVariant
    )
}

@Composable
fun DotEqTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    monochrome: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM, ThemeMode.SYSTEM_UI -> systemDark
    }

    val baseScheme: ColorScheme = if (themeMode == ThemeMode.SYSTEM_UI) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }.getOrElse {
                if (useDark) SystemUiFallbackDarkScheme else SystemUiFallbackLightScheme
            }
        } else {
            if (useDark) SystemUiFallbackDarkScheme else SystemUiFallbackLightScheme
        }
    } else {
        if (useDark) DarkM3Scheme else LightM3Scheme
    }

    val baseNothingColors: NothingColors = if (themeMode == ThemeMode.SYSTEM_UI) {
        dynamicSchemeToNothingColors(baseScheme, useDark)
    } else {
        if (useDark) DarkNothingColors else LightNothingColors
    }

    val nothingColors = if (monochrome) baseNothingColors.toMonochromePalette() else baseNothingColors
    val m3Scheme = if (monochrome) baseScheme.toMonochromeColorScheme() else baseScheme

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
