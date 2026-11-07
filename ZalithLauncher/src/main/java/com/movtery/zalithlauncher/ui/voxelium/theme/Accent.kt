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

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.movtery.zalithlauncher.R

/**
 * One accent colour the player can choose in Settings → Launcher. Each has a shade for dark mode
 * and one for light mode, so buttons stay readable on both backgrounds; the containers and
 * highlights are mixed from it. The same 25 colours as Voxelium for iOS.
 */
data class Accent(val id: String, @StringRes val name: Int, val dark: Color, val light: Color) {
    fun shade(dark: Boolean) = if (dark) this.dark else light

    companion object {
        const val DEFAULT_ID = "voxelium"

        /** 25 colours people use everywhere, in rainbow order, then the neutrals. */
        val all: List<Accent> = listOf(
            Accent(DEFAULT_ID, R.string.voxelium_ui_accent_voxelium, Color(0xFF3DDC97), Color(0xFF0F7B5A)),
            Accent("red", R.string.voxelium_ui_accent_red, Color(0xFFFF6B6B), Color(0xFFC62828)),
            Accent("crimson", R.string.voxelium_ui_accent_crimson, Color(0xFFFF5C7A), Color(0xFFA4123F)),
            Accent("coral", R.string.voxelium_ui_accent_coral, Color(0xFFFF8A65), Color(0xFFC2410C)),
            Accent("orange", R.string.voxelium_ui_accent_orange, Color(0xFFFF9F43), Color(0xFFC2570A)),
            Accent("amber", R.string.voxelium_ui_accent_amber, Color(0xFFFFC145), Color(0xFF9A6200)),
            Accent("gold", R.string.voxelium_ui_accent_gold, Color(0xFFE6C35C), Color(0xFF8D6E00)),
            Accent("yellow", R.string.voxelium_ui_accent_yellow, Color(0xFFFFE14D), Color(0xFF7F6A00)),
            Accent("lime", R.string.voxelium_ui_accent_lime, Color(0xFFA6E35A), Color(0xFF4F7A00)),
            Accent("green", R.string.voxelium_ui_accent_green, Color(0xFF5BD66B), Color(0xFF2E7D32)),
            Accent("mint", R.string.voxelium_ui_accent_mint, Color(0xFF6FE3C1), Color(0xFF00796B)),
            Accent("teal", R.string.voxelium_ui_accent_teal, Color(0xFF2EC4C4), Color(0xFF00737A)),
            Accent("cyan", R.string.voxelium_ui_accent_cyan, Color(0xFF4DD8F0), Color(0xFF00788A)),
            Accent("sky", R.string.voxelium_ui_accent_sky, Color(0xFF6CC4FF), Color(0xFF0369A1)),
            Accent("blue", R.string.voxelium_ui_accent_blue, Color(0xFF5B9DFF), Color(0xFF1E5BD8)),
            Accent("navy", R.string.voxelium_ui_accent_navy, Color(0xFF7C9CFF), Color(0xFF1A2A80)),
            Accent("indigo", R.string.voxelium_ui_accent_indigo, Color(0xFF8C8CFF), Color(0xFF3F3DB5)),
            Accent("purple", R.string.voxelium_ui_accent_purple, Color(0xFFB48CFF), Color(0xFF6A1B9A)),
            Accent("violet", R.string.voxelium_ui_accent_violet, Color(0xFFD08CFF), Color(0xFF8E24AA)),
            Accent("magenta", R.string.voxelium_ui_accent_magenta, Color(0xFFFF6FD8), Color(0xFFAD1457)),
            Accent("pink", R.string.voxelium_ui_accent_pink, Color(0xFFFF8FB8), Color(0xFFC2185B)),
            Accent("brown", R.string.voxelium_ui_accent_brown, Color(0xFFC89B7B), Color(0xFF6D4C41)),
            Accent("gray", R.string.voxelium_ui_accent_gray, Color(0xFFB0BAC0), Color(0xFF546E7A)),
            // White would vanish on light backgrounds and black on dark ones, so each keeps its
            // look where it can and turns to charcoal or graphite where it can't.
            Accent("white", R.string.voxelium_ui_accent_white, Color(0xFFFFFFFF), Color(0xFF2B3135)),
            Accent("black", R.string.voxelium_ui_accent_black, Color(0xFF59636A), Color(0xFF111416)),
        )

        fun named(id: String?): Accent = all.firstOrNull { it.id == id } ?: all.first()
    }
}

/** Black or white text, whichever reads on [c]. */
private fun onColor(c: Color, threshold: Float) = if (c.luminance() > threshold) lerp(c, Color.Black, 0.84f) else Color.White

/**
 * [base] and [extras] with their accent slots taken from [accent]. Voxelium green keeps the
 * original values exactly.
 */
internal fun applyAccent(base: ColorScheme, extras: VoxeliumColors, accent: Accent, dark: Boolean): Pair<ColorScheme, VoxeliumColors> {
    if (accent.id == Accent.DEFAULT_ID) return base to extras
    val black = Color.Black
    val white = Color.White
    return if (dark) {
        val a = accent.dark
        val surface = Color(0xFF161B1E)
        val text = Color(0xFFE8EEF0)
        val onContainer = lerp(text, a, 0.25f)
        base.copy(
            primary = a,
            onPrimary = onColor(a, 0.4f),
            primaryContainer = lerp(surface, a, 0.18f),
            onPrimaryContainer = onContainer,
            inversePrimary = accent.light,
            secondary = lerp(a, white, 0.35f),
            onSecondary = onColor(lerp(a, white, 0.35f), 0.4f),
            secondaryContainer = lerp(surface, a, 0.22f),
            onSecondaryContainer = onContainer,
            surfaceTint = a
        ) to extras.copy(
            highlight = lerp(Color(0xFF0E1214), a, 0.13f),
            onHighlight = lerp(Color(0xFFB0B8BC), a, 0.2f),
            highlightBorder = lerp(surface, a, 0.28f)
        )
    } else {
        val a = accent.light
        val text = Color(0xFF12181B)
        base.copy(
            primary = a,
            onPrimary = onColor(a, 0.5f),
            primaryContainer = lerp(white, a, 0.22f),
            onPrimaryContainer = lerp(a, black, 0.55f),
            inversePrimary = accent.dark,
            secondary = lerp(a, text, 0.3f),
            onSecondary = Color.White,
            secondaryContainer = lerp(white, a, 0.16f),
            onSecondaryContainer = lerp(a, black, 0.65f),
            surfaceTint = a
        ) to extras.copy(
            highlight = lerp(white, a, 0.14f),
            onHighlight = lerp(a, text, 0.6f),
            highlightBorder = lerp(white, a, 0.30f)
        )
    }
}
