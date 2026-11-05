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

package com.movtery.zalithlauncher.ui.voxelium.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import io.github.voxelium.core.controls.ControlLayout
import kotlin.math.roundToInt

/**
 * The runtime menu (the "Runtime Menu" control or the gamepad's menu button): a Voxelium sheet
 * in the style of the launcher's sheets ([com.movtery.zalithlauncher.ui.screens.content.voxelium.VoxeliumSheet]),
 * sized to sit over the game.
 */
@Composable
internal fun RuntimeMenu(
    state: TouchControlsState,
    logsShown: Boolean,
    onDismiss: () -> Unit,
    onKeyboard: () -> Unit,
    onSwitchLogs: () -> Unit,
    onForceClose: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = cardColor(false),
            contentColor = onCardColor(),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.voxelium_game_menu_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    rememberMemoryReading()?.let { m ->
                        Text(
                            stringResource(R.string.voxelium_game_memory, m.text),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(m.background)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                MenuItem(stringResource(R.string.voxelium_game_resume), onClick = onDismiss)
                MenuItem(stringResource(R.string.voxelium_game_keyboard)) {
                    onDismiss()
                    onKeyboard()
                }
                MenuItem(
                    stringResource(if (state.guiHidden) R.string.voxelium_game_show_controls else R.string.voxelium_game_hide_controls)
                ) {
                    state.guiHidden = !state.guiHidden
                    onDismiss()
                }
                MenuItem(
                    stringResource(if (state.virtualMouse) R.string.voxelium_game_virtual_mouse_on else R.string.voxelium_game_virtual_mouse_off)
                ) { state.virtualMouse = !state.virtualMouse }
                MenuItem(stringResource(if (logsShown) R.string.voxelium_game_hide_logs else R.string.voxelium_game_show_logs)) {
                    onSwitchLogs()
                    onDismiss()
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row {
                        Text(
                            stringResource(R.string.voxelium_game_controls_opacity),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${(state.opacity * 100).roundToInt()} %", style = MaterialTheme.typography.bodyLarge)
                    }
                    Slider(
                        value = state.opacity,
                        onValueChange = { state.opacity = it },
                        onValueChangeFinished = { state.saveOpacity() },
                        valueRange = ControlLayout.OPACITY_RANGE.start.toFloat()..ControlLayout.OPACITY_RANGE.endInclusive.toFloat()
                    )
                }
                HorizontalDivider()
                MenuItem(stringResource(R.string.voxelium_game_force_close), destructive = true) {
                    onDismiss()
                    onForceClose()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else onCardColor(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
