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

package com.movtery.zalithlauncher.ui.voxelium.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.plugin.renderer_v2.RendererV2Data
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.game.version.installed.GraphicsApi
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.GamepadInputMode
import com.movtery.zalithlauncher.setting.unit.max
import com.movtery.zalithlauncher.setting.unit.min
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.components.SimpleEditDialog
import com.movtery.zalithlauncher.ui.control.GamepadBindingKeyboard
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadMap
import com.movtery.zalithlauncher.ui.control.gamepad.JoystickMode
import com.movtery.zalithlauncher.ui.control.gamepad.getNameByGamepadEvent
import com.movtery.zalithlauncher.ui.control.gamepad.remapperMMKV
import com.movtery.zalithlauncher.ui.control.gyroscope.isGyroscopeAvailable
import com.movtery.zalithlauncher.ui.screens.content.settings.RendererV2ConfigDialog
import com.movtery.zalithlauncher.ui.voxelium.components.ChoiceSetting
import com.movtery.zalithlauncher.ui.voxelium.components.SectionLabel
import com.movtery.zalithlauncher.ui.voxelium.components.SettingRow
import com.movtery.zalithlauncher.ui.voxelium.components.SliderSetting
import com.movtery.zalithlauncher.ui.voxelium.components.SwitchSetting
import com.movtery.zalithlauncher.utils.device.checkVulkanSupport
import com.movtery.zalithlauncher.utils.isAdrenoGPU
import com.movtery.zalithlauncher.utils.string.isEmptyOrBlank
import com.movtery.zalithlauncher.viewmodel.GAMEPAD_CONFIG_NAME_LENGTH
import com.movtery.zalithlauncher.viewmodel.GamepadViewModel

/** The renderer's own options (renderer plugins that offer them), opened from Graphics. */
@Composable
internal fun RendererOptionsRow(rendererId: String?) {
    val units = remember(rendererId) {
        Renderers.getRenderers().filterIsInstance<RendererV2Data>()
            .find { it.getUniqueIdentifier() == rendererId }
            ?.env?.getConfigurableUnits()?.takeIf { it.isNotEmpty() }
    } ?: return
    var open by remember { mutableStateOf(false) }
    SettingRow(title = stringResource(R.string.voxelium_ui_set_renderer_options), summary = stringResource(R.string.voxelium_ui_set_renderer_options_hint)) {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.voxelium_ui_set_open)) }
    }
    if (open) RendererV2ConfigDialog(units = units, onDismissRequest = { open = false })
}

/** Graphics settings for when something doesn't draw right. */
@Composable
internal fun GraphicsAdvanced() {
    SectionLabel(stringResource(R.string.voxelium_ui_set_advanced), modifier = Modifier.padding(top = 8.dp))
    ChoiceSetting(
        title = stringResource(R.string.voxelium_ui_set_graphics_api),
        summary = stringResource(R.string.voxelium_ui_set_graphics_api_hint),
        options = GraphicsApi.entries,
        selected = AllSettings.graphicsApi.state,
        label = {
            when (it) {
                GraphicsApi.DEFAULT -> stringResource(R.string.voxelium_ui_set_graphics_api_game)
                GraphicsApi.DEFAULT_OPENGL -> stringResource(R.string.voxelium_ui_set_graphics_api_default_opengl)
                else -> it.displayName
            }
        },
        onPick = { AllSettings.graphicsApi.save(it) }
    )
    val context = LocalContext.current
    if (remember { checkVulkanSupport(context.packageManager) }) {
        var adrenoWarning by remember { mutableStateOf(false) }
        SwitchSetting(
            title = stringResource(R.string.voxelium_ui_set_zink_system),
            summary = stringResource(R.string.voxelium_ui_set_zink_system_hint),
            checked = AllSettings.zinkPreferSystemDriver.state,
            onChange = { on -> if (on && isAdrenoGPU()) adrenoWarning = true else AllSettings.zinkPreferSystemDriver.save(on) }
        )
        if (adrenoWarning) {
            SimpleAlertDialog(
                title = stringResource(R.string.generic_warning),
                text = stringResource(R.string.settings_renderer_zink_driver_adreno),
                onConfirm = { AllSettings.zinkPreferSystemDriver.save(true); adrenoWarning = false },
                onDismiss = { adrenoWarning = false }
            )
        }
    }
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_zink_vsync),
        summary = stringResource(R.string.voxelium_ui_set_zink_vsync_hint),
        checked = AllSettings.vsyncInZink.state,
        onChange = { AllSettings.vsyncInZink.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_surface_view),
        summary = stringResource(R.string.voxelium_ui_set_surface_view_hint),
        checked = AllSettings.useSurfaceView.state,
        onChange = { AllSettings.useSurfaceView.save(it) }
    )
}

/** Gamepad: on/off, how it reaches the game, sticks and speeds, and the button mapping. */
@Composable
internal fun GamepadSectionFull() {
    val viewModel: GamepadViewModel = viewModel()
    var refreshed by remember { mutableStateOf(false) }
    var inGame by remember { mutableStateOf(true) }
    var binding by remember { mutableStateOf<GamepadMap?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    val mapping = remember(refreshed) { viewModel.currentMapping }

    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_gamepad_on),
        summary = stringResource(R.string.voxelium_ui_set_gamepad_on_hint),
        checked = AllSettings.gamepadControl.state,
        onChange = { AllSettings.gamepadControl.save(it) }
    )
    if (!AllSettings.gamepadControl.state) return
    ChoiceSetting(
        title = stringResource(R.string.voxelium_ui_set_gamepad_mode),
        summary = stringResource(AllSettings.gamepadInputMode.state.summaryRes),
        options = GamepadInputMode.entries,
        selected = AllSettings.gamepadInputMode.state,
        label = { stringResource(it.titleRes) },
        onPick = { AllSettings.gamepadInputMode.save(it) }
    )
    SliderSetting(
        title = stringResource(R.string.voxelium_ui_set_dead_zone),
        value = AllSettings.gamepadDeadZoneScale.state,
        range = AllSettings.gamepadDeadZoneScale.min..AllSettings.gamepadDeadZoneScale.max,
        step = 5,
        label = { "$it%" },
        onChange = { AllSettings.gamepadDeadZoneScale.save(it) }
    )
    SliderSetting(
        title = stringResource(R.string.voxelium_ui_set_camera_speed),
        value = AllSettings.gamepadCameraSensitivity.state,
        range = AllSettings.gamepadCameraSensitivity.min..AllSettings.gamepadCameraSensitivity.max,
        step = 5,
        label = { "$it%" },
        onChange = { AllSettings.gamepadCameraSensitivity.save(it) }
    )
    SliderSetting(
        title = stringResource(R.string.voxelium_ui_set_cursor_speed),
        value = AllSettings.gamepadCursorSensitivity.state,
        range = AllSettings.gamepadCursorSensitivity.min..AllSettings.gamepadCursorSensitivity.max,
        step = 5,
        label = { "$it%" },
        onChange = { AllSettings.gamepadCursorSensitivity.save(it) }
    )

    // The mapping only applies when the gamepad is turned into keys and mouse.
    if (AllSettings.gamepadInputMode.state != GamepadInputMode.Mapped) return
    ChoiceSetting(
        title = stringResource(R.string.voxelium_ui_set_sticks),
        summary = stringResource(AllSettings.joystickControlMode.state.summaryRes),
        options = JoystickMode.entries,
        selected = AllSettings.joystickControlMode.state,
        label = { stringResource(it.titleRes) },
        onPick = { AllSettings.joystickControlMode.save(it) }
    )
    SettingRow(title = stringResource(R.string.voxelium_ui_set_reset_controllers), summary = stringResource(R.string.voxelium_ui_set_reset_controllers_hint)) {
        OutlinedButton(onClick = { remapperMMKV().clearAll(); resetDone = true }, enabled = !resetDone) {
            Text(stringResource(if (resetDone) R.string.voxelium_ui_set_reset_done else R.string.generic_reset))
        }
    }

    SectionLabel(stringResource(R.string.voxelium_ui_set_buttons), modifier = Modifier.padding(top = 8.dp))
    val profiles = remember(refreshed) { viewModel.getAllConfigKeys() }
    if (profiles.isNotEmpty()) {
        ChoiceSetting(
            title = stringResource(R.string.voxelium_ui_set_profile),
            options = profiles,
            selected = profiles.find { it == AllSettings.gamepadMappingConfig.state },
            label = { it },
            onPick = {
                AllSettings.gamepadMappingConfig.save(it)
                viewModel.reloadAllMappings()
                refreshed = !refreshed
            }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { creating = true }) { Text(stringResource(R.string.voxelium_ui_set_profile_new)) }
        if (mapping != null) {
            TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.voxelium_ui_set_profile_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (mapping != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = inGame, onClick = { inGame = true }, label = { Text(stringResource(R.string.voxelium_ui_set_in_game)) })
            FilterChip(selected = !inGame, onClick = { inGame = false }, label = { Text(stringResource(R.string.voxelium_ui_set_in_menus)) })
        }
        for (button in GamepadMap.entries) {
            val codes = remember(button, inGame, refreshed) { mapping.findByMap(button, inGame = inGame)?.toList().orEmpty() }
            Surface(
                onClick = { binding = button },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(button.getIconRes()), contentDescription = null, modifier = Modifier.size(28.dp))
                    Text(
                        if (codes.isEmpty()) stringResource(R.string.voxelium_ui_set_unbound) else codes.map { getNameByGamepadEvent(it) }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (codes.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { mapping.resetMapping(button, inGame); refreshed = !refreshed }) { Text(stringResource(R.string.generic_reset)) }
                }
            }
        }
    }

    binding?.let { button ->
        val selected = remember(button, inGame) { mapping?.findByMap(button, inGame = inGame)?.toMutableList() ?: mutableListOf() }
        GamepadBindingKeyboard(
            selectedKeys = selected,
            onKeyAdd = { key -> selected.add(key); mapping?.saveMapping(button, selected.toSet(), inGame); refreshed = !refreshed },
            onKeyRemove = { key -> selected.remove(key); mapping?.saveMapping(button, selected.toSet(), inGame); refreshed = !refreshed },
            onDismissRequest = { binding = null }
        )
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        val taken = remember(name) { !name.isEmptyOrBlank() && viewModel.containsConfig(name) }
        SimpleEditDialog(
            title = stringResource(R.string.voxelium_ui_set_profile_new),
            value = name,
            onValueChange = { name = it.take(GAMEPAD_CONFIG_NAME_LENGTH) },
            label = { Text("${stringResource(R.string.voxelium_ui_controls_name)} (${name.length}/$GAMEPAD_CONFIG_NAME_LENGTH)") },
            isError = taken || name.isEmptyOrBlank(),
            supportingText = { if (taken) Text(stringResource(R.string.voxelium_ui_set_profile_taken)) },
            singleLine = true,
            onConfirm = {
                if (!taken && !name.isEmptyOrBlank()) {
                    viewModel.createNewConfig(name = name, onContainsConfig = {}, onFinished = { refreshed = !refreshed })
                    creating = false
                }
            },
            onDismissRequest = { creating = false }
        )
    }
    if (deleting && mapping != null) {
        SimpleAlertDialog(
            title = stringResource(R.string.voxelium_ui_set_profile_delete),
            text = stringResource(R.string.voxelium_ui_set_profile_delete_text, mapping.name),
            confirmText = stringResource(R.string.generic_delete),
            onConfirm = { viewModel.deleteConfig(name = mapping.name, onFinished = { refreshed = !refreshed }); deleting = false },
            onDismiss = { deleting = false }
        )
    }
}

/** Motion: aim by turning the phone (gyroscope), while looking around in game. */
@Composable
internal fun MotionSection() {
    val context = LocalContext.current
    val available = remember { isGyroscopeAvailable(context = context) }
    if (!available) {
        Text(stringResource(R.string.voxelium_ui_set_no_gyroscope), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_gyro),
        summary = stringResource(R.string.voxelium_ui_set_gyro_hint),
        checked = AllSettings.gyroscopeControl.state,
        onChange = { AllSettings.gyroscopeControl.save(it) }
    )
    if (!AllSettings.gyroscopeControl.state) return
    SliderSetting(
        title = stringResource(R.string.voxelium_ui_set_gyro_speed),
        value = AllSettings.gyroscopeSensitivity.state,
        range = AllSettings.gyroscopeSensitivity.min..AllSettings.gyroscopeSensitivity.max,
        step = 5,
        label = { "$it%" },
        onChange = { AllSettings.gyroscopeSensitivity.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_gyro_invert_x),
        checked = AllSettings.gyroscopeInvertX.state,
        onChange = { AllSettings.gyroscopeInvertX.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_gyro_invert_y),
        checked = AllSettings.gyroscopeInvertY.state,
        onChange = { AllSettings.gyroscopeInvertY.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_gyro_smoothing),
        summary = stringResource(R.string.voxelium_ui_set_gyro_smoothing_hint),
        checked = AllSettings.gyroscopeSmoothing.state,
        onChange = { AllSettings.gyroscopeSmoothing.save(it) }
    )
    if (AllSettings.gyroscopeSmoothing.state) {
        SliderSetting(
            title = stringResource(R.string.voxelium_ui_set_gyro_smoothing_window),
            value = AllSettings.gyroscopeSmoothingWindow.state,
            range = AllSettings.gyroscopeSmoothingWindow.min..AllSettings.gyroscopeSmoothingWindow.max,
            label = { "$it" },
            onChange = { AllSettings.gyroscopeSmoothingWindow.save(it) }
        )
    }
    SliderSetting(
        title = stringResource(R.string.voxelium_ui_set_gyro_rate),
        summary = stringResource(R.string.voxelium_ui_set_gyro_rate_hint),
        value = AllSettings.gyroscopeSampleRate.state,
        range = AllSettings.gyroscopeSampleRate.min..AllSettings.gyroscopeSampleRate.max,
        label = { "$it ms" },
        onChange = { AllSettings.gyroscopeSampleRate.save(it) }
    )
}
