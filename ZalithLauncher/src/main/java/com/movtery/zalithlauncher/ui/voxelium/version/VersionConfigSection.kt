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

package com.movtery.zalithlauncher.ui.voxelium.version

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.multirt.RuntimesManager
import com.movtery.zalithlauncher.game.plugin.driver.DriverPluginManager
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.game.version.installed.SettingState
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionConfig
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.unit.getOrMin
import com.movtery.zalithlauncher.setting.unit.min
import com.movtery.zalithlauncher.ui.voxelium.components.ChoiceSetting
import com.movtery.zalithlauncher.ui.voxelium.components.SectionLabel
import com.movtery.zalithlauncher.ui.voxelium.components.SliderSetting
import com.movtery.zalithlauncher.ui.voxelium.components.SwitchSetting
import com.movtery.zalithlauncher.ui.voxelium.components.TextSetting
import com.movtery.zalithlauncher.ui.voxelium.settings.PickRow
import com.movtery.zalithlauncher.ui.voxelium.settings.rendererSummary
import com.movtery.zalithlauncher.utils.platform.getMaxMemoryForSettings

/**
 * Voxelium: one version's own settings, stored in its version.config (VersionConfig) like
 * ZalithLauncher2 does, so the launcher applies them when this version starts. An empty value or
 * FOLLOW_GLOBAL means "same as Settings".
 */
@Composable
fun VersionConfigSection(version: Version) {
    val config = version.getVersionConfig()
    var tick by remember(version) { mutableIntStateOf(0) }
    fun edit(change: VersionConfig.() -> Unit) {
        config.change()
        config.save()
        tick++
    }

    key(tick) {
        Text(stringResource(R.string.voxelium_ui_set_this_version_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Memory: -1 (or anything under 256) uses the global setting.
        val context = LocalContext.current
        val maxMemory = remember { getMaxMemoryForSettings(context) }
        val global = AllSettings.ramAllocation.getOrMin()
        val own = config.ramAllocation >= 256
        SwitchSetting(
            title = stringResource(R.string.voxelium_ui_set_own_memory),
            summary = if (own) null else stringResource(R.string.voxelium_ui_set_global_memory, global),
            checked = own,
            onChange = { on -> edit { ramAllocation = if (on) global.coerceAtMost(maxMemory) else -1 } }
        )
        if (own) {
            SliderSetting(
                title = stringResource(R.string.voxelium_ui_set_memory),
                value = config.ramAllocation.coerceIn(AllSettings.ramAllocation.min, maxMemory),
                range = AllSettings.ramAllocation.min..maxMemory,
                step = 64,
                label = { stringResource(R.string.voxelium_ui_set_mb, it) },
                onChange = { mb -> edit { ramAllocation = mb } }
            )
        }

        val same = stringResource(R.string.voxelium_ui_set_follow_global)
        val runtimes = remember { RuntimesManager.getRuntimes().filter { it.isCompatible() }.map { it.name } }
        ChoiceSetting(
            title = stringResource(R.string.voxelium_ui_set_java),
            options = listOf("") + runtimes,
            selected = config.javaRuntime.takeIf { it.isEmpty() || it in runtimes } ?: "",
            label = { it.ifEmpty { same } },
            onPick = { name -> edit { javaRuntime = name } }
        )
        StateChoice(stringResource(R.string.voxelium_ui_set_isolation), config.isolationType) { s -> edit { isolationType = s } }
        StateChoice(stringResource(R.string.voxelium_ui_set_skip_check), config.skipGameIntegrityCheck) { s -> edit { skipGameIntegrityCheck = s } }
        TextSetting(
            title = stringResource(R.string.voxelium_ui_set_jvm_args),
            value = config.jvmArgs,
            placeholder = AllSettings.jvmArgs.getValue(),
            onChange = { text -> config.jvmArgs = text; config.save() }
        )
        TextSetting(
            title = stringResource(R.string.voxelium_ui_set_game_args),
            value = config.gameArgs,
            onChange = { text -> config.gameArgs = text; config.save() }
        )

        val renderers = remember { Renderers.getRenderers() }
        SectionLabel(stringResource(R.string.voxelium_ui_set_renderer), modifier = Modifier.padding(top = 8.dp))
        PickRow(title = same, summary = null, selected = config.renderer.isEmpty(), onClick = { edit { renderer = "" } })
        for (r in renderers) {
            PickRow(
                title = r.getRendererName(),
                summary = rendererSummary(r.getRendererSummary(), r.getDisplayMinMCVersion()),
                selected = config.renderer == r.getUniqueIdentifier(),
                onClick = { edit { renderer = r.getUniqueIdentifier() } }
            )
        }
        val drivers = remember { DriverPluginManager.getDriverList() }
        if (drivers.size > 1) {
            SectionLabel(stringResource(R.string.voxelium_ui_set_driver), modifier = Modifier.padding(top = 8.dp))
            PickRow(title = same, summary = null, selected = config.driver.isEmpty(), onClick = { edit { driver = "" } })
            for (d in drivers) {
                PickRow(title = d.name, summary = d.summary, selected = config.driver == d.id, onClick = { edit { driver = d.id } })
            }
        }
    }
}

@Composable
private fun StateChoice(title: String, value: SettingState, onPick: (SettingState) -> Unit) {
    val same = stringResource(R.string.voxelium_ui_set_follow_global)
    val on = stringResource(R.string.voxelium_ui_set_on)
    val off = stringResource(R.string.voxelium_ui_set_off)
    ChoiceSetting(
        title = title,
        options = SettingState.entries,
        selected = value,
        label = {
            when (it) {
                SettingState.FOLLOW_GLOBAL -> same
                SettingState.ENABLE -> on
                SettingState.DISABLE -> off
            }
        },
        onPick = onPick
    )
}
