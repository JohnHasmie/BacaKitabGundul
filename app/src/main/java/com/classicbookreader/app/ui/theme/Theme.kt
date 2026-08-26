package com.classicbookreader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors that Material's scheme has no slot for (glass surfaces, hairlines,
 * accent inks). Read them via [AppTheme.glass].
 */
@Immutable
data class GlassColors(
    val surface: Color,
    val surfaceStrong: Color,
    val hairline: Color,
    val inkWash: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val inkFaint: Color,
    val amber: Color,
    val amberInk: Color,
    val amberDeep: Color,
    val danger: Color,
)

private val LocalGlassColors = staticCompositionLocalOf {
    lightGlassColors
}

private val lightGlassColors = GlassColors(
    surface = AppColors.GlassSurface,
    surfaceStrong = AppColors.GlassSurfaceStrong,
    hairline = AppColors.Hairline,
    inkWash = AppColors.InkWash,
    inkSecondary = AppColors.InkSecondary,
    inkTertiary = AppColors.InkTertiary,
    inkFaint = AppColors.InkFaint,
    amber = AppColors.Amber,
    amberInk = AppColors.AmberInk,
    amberDeep = AppColors.AmberDeep,
    danger = AppColors.Danger,
)

private val darkGlassColors = GlassColors(
    surface = AppColors.DarkGlassSurface,
    surfaceStrong = AppColors.DarkGlassSurface,
    hairline = AppColors.DarkHairline,
    inkWash = AppColors.DarkHairline,
    inkSecondary = AppColors.DarkInkSecondary,
    inkTertiary = AppColors.DarkInkSecondary,
    inkFaint = AppColors.DarkInkSecondary,
    amber = AppColors.Amber,
    amberInk = AppColors.Amber,
    amberDeep = AppColors.Amber,
    danger = AppColors.Danger,
)

private val lightScheme = lightColorScheme(
    primary = AppColors.Green,
    onPrimary = Color.White,
    secondary = AppColors.Amber,
    onSecondary = AppColors.Ink,
    background = AppColors.Cream,
    onBackground = AppColors.Ink,
    surface = AppColors.Cream,
    onSurface = AppColors.Ink,
    surfaceVariant = AppColors.GlassSurface,
    onSurfaceVariant = AppColors.InkSecondary,
    outline = AppColors.Hairline,
    error = AppColors.Danger,
)

private val darkScheme = darkColorScheme(
    primary = AppColors.GreenBright,
    onPrimary = Color.White,
    secondary = AppColors.Amber,
    onSecondary = AppColors.Ink,
    background = AppColors.DarkBackground,
    onBackground = AppColors.DarkInk,
    surface = AppColors.DarkBackground,
    onSurface = AppColors.DarkInk,
    surfaceVariant = AppColors.DarkGlassSurface,
    onSurfaceVariant = AppColors.DarkInkSecondary,
    outline = AppColors.DarkHairline,
    error = AppColors.Danger,
)

object AppTheme {
    val glass: GlassColors
        @Composable get() = LocalGlassColors.current
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkScheme else lightScheme
    val glass = if (darkTheme) darkGlassColors else lightGlassColors
    CompositionLocalProvider(LocalGlassColors provides glass) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
