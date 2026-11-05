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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.multirt.Runtime
import com.movtery.zalithlauncher.game.multirt.RuntimesManager
import com.movtery.zalithlauncher.game.plugin.driver.DriverPluginManager
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.AppLanguage
import com.movtery.zalithlauncher.setting.enums.DarkMode
import com.movtery.zalithlauncher.setting.enums.applyLanguage
import com.movtery.zalithlauncher.setting.unit.getOrMin
import com.movtery.zalithlauncher.setting.unit.max
import com.movtery.zalithlauncher.setting.unit.min
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.settings.progressRuntimeUri
import com.movtery.zalithlauncher.ui.voxelium.components.ChoiceSetting
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.ui.voxelium.components.SectionLabel
import com.movtery.zalithlauncher.ui.voxelium.components.SettingRow
import com.movtery.zalithlauncher.ui.voxelium.components.SliderSetting
import com.movtery.zalithlauncher.ui.voxelium.components.SwitchSetting
import com.movtery.zalithlauncher.ui.voxelium.components.TextSetting
import com.movtery.zalithlauncher.utils.platform.getMaxMemoryForSettings
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

const val URL_VOXELIUM_ANDROID = "https://github.com/Vortroxious/Voxelium-android"
const val URL_VOXELIUM_IOS = "https://github.com/Vortroxious/Voxelium-launcher"

private enum class Section(val label: Int) {
    GAME(R.string.voxelium_ui_set_game),
    GRAPHICS(R.string.voxelium_ui_set_graphics),
    JAVA(R.string.voxelium_ui_set_java),
    GAMEPAD(R.string.voxelium_ui_set_gamepad),
    LAUNCHER(R.string.voxelium_ui_set_launcher),
    ABOUT(R.string.voxelium_ui_set_about)
}

/**
 * Voxelium: Settings. Sections on the left, their settings on the right. Everything is stored in
 * the launcher's existing settings (AllSettings), so the game reads them as before.
 */
@Composable
fun VoxeliumSettingsScreen(
    backScreenViewModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    openCredits: () -> Unit
) {
    BaseScreen(screenKey = NormalNavKey.VoxeliumSettings, currentKey = backScreenViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        var section by rememberSaveable { mutableStateOf(Section.GAME) }
        Row(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.width(190.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PageHeader(title = stringResource(R.string.voxelium_ui_settings))
                for (s in Section.entries) {
                    val selected = s == section
                    Surface(
                        onClick = { section = s },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(s.label),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (section) {
                    Section.GAME -> GameSection()
                    Section.GRAPHICS -> GraphicsSection()
                    Section.JAVA -> JavaSection(submitError)
                    Section.GAMEPAD -> GamepadSection()
                    Section.LAUNCHER -> LauncherSection()
                    Section.ABOUT -> AboutSection(
                        openLink = { eventViewModel.sendEvent(EventViewModel.Event.OpenLink(it)) },
                        openCredits = openCredits
                    )
                }
            }
        }
    }
}

@Composable
private fun GameSection() {
    val context = LocalContext.current
    val maxMemory = remember { getMaxMemoryForSettings(context) }
    val ram = AllSettings.ramAllocation
    SliderSetting(
        title = stringResource(R.string.voxelium_ui_set_memory),
        summary = stringResource(R.string.voxelium_ui_set_memory_hint),
        value = (ram.state ?: ram.getOrMin()).coerceIn(ram.min, maxMemory),
        range = ram.min..maxMemory,
        step = 64,
        label = { stringResource(R.string.voxelium_ui_set_mb, it) },
        onChange = { ram.save(it) }
    )
    TextSetting(
        title = stringResource(R.string.voxelium_ui_set_jvm_args),
        summary = stringResource(R.string.voxelium_ui_set_jvm_args_hint),
        value = AllSettings.jvmArgs.state,
        onChange = { AllSettings.jvmArgs.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_isolation),
        summary = stringResource(R.string.voxelium_ui_set_isolation_hint),
        checked = AllSettings.versionIsolation.state,
        onChange = { AllSettings.versionIsolation.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_skip_check),
        summary = stringResource(R.string.voxelium_ui_set_skip_check_hint),
        checked = AllSettings.skipGameIntegrityCheck.state,
        onChange = { AllSettings.skipGameIntegrityCheck.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_show_log),
        summary = stringResource(R.string.voxelium_ui_set_show_log_hint),
        checked = AllSettings.showLogAutomatic.state,
        onChange = { AllSettings.showLogAutomatic.save(it) }
    )
}

/** One choice of a list, as a radio row with an explanation under the name. */
@Composable
internal fun PickRow(title: String, summary: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!summary.isNullOrBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun rendererSummary(summary: String?, minMc: String?): String? {
    val from = minMc?.let { stringResource(R.string.voxelium_ui_set_minecraft_from, it) }
    return listOfNotNull(summary?.takeIf { it.isNotBlank() }, from).joinToString(" · ").ifEmpty { null }
}

@Composable
private fun GraphicsSection() {
    val renderers = remember { Renderers.getRenderers() }
    val drivers = remember { DriverPluginManager.getDriverList() }
    // An unknown or empty choice means the first renderer, as the launcher does at start.
    val rendererId = AllSettings.renderer.state.takeIf { id -> renderers.any { it.getUniqueIdentifier() == id } }
        ?: renderers.firstOrNull()?.getUniqueIdentifier()
    val driverId = AllSettings.vulkanDriver.state.takeIf { id -> drivers.any { it.id == id } } ?: drivers.firstOrNull()?.id

    SectionLabel(stringResource(R.string.voxelium_ui_set_renderer))
    Text(stringResource(R.string.voxelium_ui_set_renderer_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (r in renderers) {
        PickRow(
            title = r.getRendererName(),
            summary = rendererSummary(r.getRendererSummary(), r.getDisplayMinMCVersion()),
            selected = r.getUniqueIdentifier() == rendererId,
            onClick = { AllSettings.renderer.save(r.getUniqueIdentifier()) }
        )
    }
    if (drivers.isNotEmpty()) {
        SectionLabel(stringResource(R.string.voxelium_ui_set_driver), modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.voxelium_ui_set_driver_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (d in drivers) {
            PickRow(title = d.name, summary = d.summary, selected = d.id == driverId, onClick = { AllSettings.vulkanDriver.save(d.id) })
        }
    }
    SectionLabel(stringResource(R.string.voxelium_ui_set_graphics), modifier = Modifier.padding(top = 8.dp))
    SliderSetting(
        title = stringResource(R.string.voxelium_ui_set_resolution),
        summary = stringResource(R.string.voxelium_ui_set_resolution_hint),
        value = AllSettings.resolutionRatio.state,
        range = AllSettings.resolutionRatio.min..AllSettings.resolutionRatio.max,
        step = 5,
        label = { stringResource(R.string.voxelium_ui_set_percent, it) },
        onChange = { AllSettings.resolutionRatio.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_game_full_screen),
        summary = stringResource(R.string.voxelium_ui_set_game_full_screen_hint),
        checked = AllSettings.gameFullScreen.state,
        onChange = { AllSettings.gameFullScreen.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_sustained),
        summary = stringResource(R.string.voxelium_ui_set_sustained_hint),
        checked = AllSettings.sustainedPerformance.state,
        onChange = { AllSettings.sustainedPerformance.save(it) }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_max_refresh),
        summary = stringResource(R.string.voxelium_ui_set_max_refresh_hint),
        checked = AllSettings.requestMaxRefreshRate.state,
        onChange = { AllSettings.requestMaxRefreshRate.save(it) }
    )
}

@Composable
private fun JavaSection(submitError: (ErrorViewModel.ThrowableMessage) -> Unit) {
    val context = LocalContext.current
    var runtimes by remember { mutableStateOf(RuntimesManager.getRuntimes()) }
    var removing by remember { mutableStateOf<Runtime?>(null) }
    val refresh = { runtimes = RuntimesManager.getRuntimes(forceLoad = true) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) progressRuntimeUri(context, uri, callRefresh = refresh, submitError = submitError)
    }
    val usable = runtimes.filter { it.isCompatible() }

    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_auto_java),
        summary = stringResource(R.string.voxelium_ui_set_auto_java_hint),
        checked = AllSettings.autoPickJavaRuntime.state,
        onChange = { AllSettings.autoPickJavaRuntime.save(it) }
    )
    if (usable.isNotEmpty()) {
        ChoiceSetting(
            title = stringResource(R.string.voxelium_ui_set_default_java),
            summary = stringResource(R.string.voxelium_ui_set_default_java_hint),
            options = usable,
            selected = usable.find { it.name == AllSettings.javaRuntime.state },
            label = { it.name },
            onPick = { AllSettings.javaRuntime.save(it.name) }
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        SectionLabel(stringResource(R.string.voxelium_ui_set_runtimes), modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.voxelium_ui_set_import_java)) }
    }
    for (r in runtimes) {
        SettingRow(
            title = r.name,
            summary = if (!r.isCompatible()) stringResource(R.string.voxelium_ui_set_runtime_broken)
            else stringResource(
                R.string.voxelium_ui_set_runtime_line,
                r.javaVersion,
                listOfNotNull(r.versionString, r.arch, if (r.isProvidedByLauncher) stringResource(R.string.voxelium_ui_set_runtime_bundled) else null).joinToString(" · ")
            )
        ) {
            TextButton(onClick = { removing = r }) { Text(stringResource(R.string.generic_delete), color = MaterialTheme.colorScheme.error) }
        }
    }

    removing?.let { r ->
        SimpleAlertDialog(
            title = stringResource(R.string.voxelium_ui_set_remove_java, r.name),
            text = stringResource(R.string.voxelium_ui_set_remove_java_text),
            confirmText = stringResource(R.string.generic_delete),
            onConfirm = {
                removing = null
                runCatching { RuntimesManager.removeRuntime(r.name) }
                    .onFailure { submitError(ErrorViewModel.ThrowableMessage(title = androidText(R.string.multirt_runtime_delete_failed), message = androidText(it.message ?: it.toString()))) }
                refresh()
            },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun GamepadSection() {
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_gamepad_on),
        summary = stringResource(R.string.voxelium_ui_set_gamepad_on_hint),
        checked = AllSettings.gamepadControl.state,
        onChange = { AllSettings.gamepadControl.save(it) }
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
}

@Composable
private fun LauncherSection() {
    ChoiceSetting(
        title = stringResource(R.string.voxelium_ui_set_dark_mode),
        options = listOf(DarkMode.FollowSystem, DarkMode.Enable, DarkMode.Disable),
        selected = AllSettings.launcherDarkMode.state,
        label = {
            when (it) {
                DarkMode.Enable -> stringResource(R.string.voxelium_ui_set_dark)
                DarkMode.Disable -> stringResource(R.string.voxelium_ui_set_light)
                DarkMode.FollowSystem -> stringResource(R.string.voxelium_ui_set_follow_system)
            }
        },
        onPick = { AllSettings.launcherDarkMode.save(it) }
    )
    ChoiceSetting(
        title = stringResource(R.string.voxelium_ui_set_language),
        options = AppLanguage.entries,
        selected = AllSettings.launcherLanguage.state,
        label = { stringResource(it.textRes) },
        onPick = {
            if (it != AllSettings.launcherLanguage.state) {
                AllSettings.launcherLanguage.save(it)
                applyLanguage(it)
            }
        }
    )
    SwitchSetting(
        title = stringResource(R.string.voxelium_ui_set_launcher_full_screen),
        summary = stringResource(R.string.voxelium_ui_set_launcher_full_screen_hint),
        checked = AllSettings.launcherFullScreen.state,
        onChange = { AllSettings.launcherFullScreen.save(it) }
    )
    val off = stringResource(R.string.voxelium_ui_set_animation_off)
    SliderSetting(
        title = stringResource(R.string.voxelium_ui_set_animation),
        value = AllSettings.launcherAnimateSpeed.state,
        range = AllSettings.launcherAnimateSpeed.min..AllSettings.launcherAnimateSpeed.max,
        label = { if (it == 0) off else "${it}x" },
        onChange = { AllSettings.launcherAnimateSpeed.save(it) }
    )
}

@Composable
private fun AboutSection(openLink: (String) -> Unit, openCredits: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(BuildKeys.LAUNCHER_NAME, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.voxelium_ui_set_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.voxelium_ui_notice, BuildKeys.LAUNCHER_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
    SettingRow(title = stringResource(R.string.voxelium_ui_set_github), summary = URL_VOXELIUM_ANDROID.removePrefix("https://")) {
        OutlinedButton(onClick = { openLink(URL_VOXELIUM_ANDROID) }) { Text(stringResource(R.string.voxelium_ui_set_open)) }
    }
    SettingRow(title = stringResource(R.string.voxelium_ui_set_ios), summary = URL_VOXELIUM_IOS.removePrefix("https://")) {
        OutlinedButton(onClick = { openLink(URL_VOXELIUM_IOS) }) { Text(stringResource(R.string.voxelium_ui_set_open)) }
    }
    SettingRow(title = stringResource(R.string.voxelium_ui_set_based_on), summary = stringResource(R.string.voxelium_ui_set_based_on_hint)) {
        OutlinedButton(onClick = { openLink("https://github.com/ZalithLauncher/ZalithLauncher2") }) { Text(stringResource(R.string.voxelium_ui_set_open)) }
    }
    SettingRow(title = stringResource(R.string.voxelium_ui_set_licences), summary = stringResource(R.string.voxelium_ui_set_licences_hint)) {
        OutlinedButton(onClick = openCredits) { Text(stringResource(R.string.voxelium_ui_set_open)) }
    }
}
