/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.voxelium.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.setting.AllSettings

/**
 * Voxelium's own look (the design approved on 2026-11-05): deep slate surfaces with one
 * teal-green accent (or the one chosen in Settings, see Accent.kt), rounded 16 dp cards and pill
 * buttons. Light and dark come from the same seed.
 */
private val VoxeliumDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF3DDC97),
    onPrimary = Color(0xFF04241A),
    primaryContainer = Color(0xFF1F3A31),
    onPrimaryContainer = Color(0xFFCFE9DD),
    inversePrimary = Color(0xFF0F7B5A),
    secondary = Color(0xFF7CEBBB),
    onSecondary = Color(0xFF04241A),
    secondaryContainer = Color(0xFF23423A),
    onSecondaryContainer = Color(0xFFCFE9DD),
    tertiary = Color(0xFFF2B84B),
    onTertiary = Color(0xFF2A1C00),
    tertiaryContainer = Color(0xFF3A3524),
    onTertiaryContainer = Color(0xFFF2DFA6),
    background = Color(0xFF0E1214),
    onBackground = Color(0xFFE8EEF0),
    surface = Color(0xFF0E1214),
    onSurface = Color(0xFFE8EEF0),
    surfaceVariant = Color(0xFF1B2124),
    onSurfaceVariant = Color(0xFF9AA8AE),
    surfaceTint = Color(0xFF3DDC97),
    inverseSurface = Color(0xFFE8EEF0),
    inverseOnSurface = Color(0xFF161B1E),
    error = Color(0xFFFF9C9C),
    onError = Color(0xFF3A0B0B),
    errorContainer = Color(0xFF5A2E2E),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF2C353A),
    outlineVariant = Color(0xFF222A2E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1E2529),
    surfaceDim = Color(0xFF0B0F11),
    surfaceContainerLowest = Color(0xFF0B0F11),
    surfaceContainerLow = Color(0xFF12171A),
    surfaceContainer = Color(0xFF161B1E),
    surfaceContainerHigh = Color(0xFF1B2124),
    surfaceContainerHighest = Color(0xFF222A2E)
)

private val VoxeliumLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF0F7B5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9F0DE),
    onPrimaryContainer = Color(0xFF00382A),
    inversePrimary = Color(0xFF3DDC97),
    secondary = Color(0xFF2F6B55),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7EDE3),
    onSecondaryContainer = Color(0xFF0C2E23),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE1A8),
    onTertiaryContainer = Color(0xFF2A1C00),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF12181B),
    surface = Color(0xFFF4F7F6),
    onSurface = Color(0xFF12181B),
    surfaceVariant = Color(0xFFE1E8E6),
    onSurfaceVariant = Color(0xFF4E5B60),
    surfaceTint = Color(0xFF0F7B5A),
    inverseSurface = Color(0xFF1E2529),
    inverseOnSurface = Color(0xFFE8EEF0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFC3CDD0),
    outlineVariant = Color(0xFFDCE3E1),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE4EAE8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F3),
    surfaceContainer = Color(0xFFEAF0EE),
    surfaceContainerHigh = Color(0xFFE4EBE9),
    surfaceContainerHighest = Color(0xFFDDE5E3)
)

private val VoxeliumShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

/** The system sans in Voxelium's weights: bold, tight headings and calm body text. */
private fun voxeliumTypography(family: FontFamily = FontFamily.Default) = Typography(
    displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp)
)

/** Colours the Material scheme has no slot for. */
@Immutable
data class VoxeliumColors(
    /** The "last played" card and other highlighted surfaces. */
    val highlight: Color,
    val onHighlight: Color,
    val highlightBorder: Color,
    /** Loader badges: background and text. */
    val fabric: Pair<Color, Color>,
    val quilt: Pair<Color, Color>,
    val forge: Pair<Color, Color>,
    val neoForge: Pair<Color, Color>,
    val vanilla: Pair<Color, Color>,
    val modpack: Pair<Color, Color>
)

private val DarkExtras = VoxeliumColors(
    highlight = Color(0xFF1A2F28),
    onHighlight = Color(0xFFB6C6BF),
    highlightBorder = Color(0xFF2A4A3E),
    fabric = Color(0xFF2D2A45) to Color(0xFFC9C2FF),
    quilt = Color(0xFF3A3524) to Color(0xFFF2DFA6),
    forge = Color(0xFF3A2B26) to Color(0xFFF5C3A9),
    neoForge = Color(0xFF45302A) to Color(0xFFFFC9B8),
    vanilla = Color(0xFF22343F) to Color(0xFFB5DDF5),
    modpack = Color(0xFF23423A) to Color(0xFFCFE9DD)
)

private val LightExtras = VoxeliumColors(
    highlight = Color(0xFFDDF3E9),
    onHighlight = Color(0xFF2F4A40),
    highlightBorder = Color(0xFFB4DDC9),
    fabric = Color(0xFFE6E2FF) to Color(0xFF3B3189),
    quilt = Color(0xFFF6EBC4) to Color(0xFF5A4500),
    forge = Color(0xFFF8E0D4) to Color(0xFF6B2E12),
    neoForge = Color(0xFFFFDCD0) to Color(0xFF7A2A10),
    vanilla = Color(0xFFD6ECF8) to Color(0xFF0E4A6B),
    modpack = Color(0xFFD7EDE3) to Color(0xFF0C2E23)
)

val LocalVoxeliumColors = staticCompositionLocalOf { DarkExtras }

object Voxelium {
    val colors: VoxeliumColors
        @Composable get() = LocalVoxeliumColors.current
}

@Composable
fun VoxeliumTheme(dark: Boolean, content: @Composable () -> Unit) {
    // The accent colour from Settings → Launcher; changing it recolours the launcher at once.
    val accent = Accent.named(AllSettings.voxeliumAccent.state)
    val (scheme, extras) = remember(accent, dark) {
        applyAccent(if (dark) VoxeliumDark else VoxeliumLight, if (dark) DarkExtras else LightExtras, accent, dark)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalVoxeliumColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = VoxeliumShapes,
            typography = voxeliumTypography(),
            content = content
        )
    }
}
