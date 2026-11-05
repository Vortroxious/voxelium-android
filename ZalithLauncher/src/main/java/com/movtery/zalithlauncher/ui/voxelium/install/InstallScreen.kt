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

package com.movtery.zalithlauncher.ui.voxelium.install

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame.InstallState
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.voxelium.ModpacksDialog
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.ui.voxelium.components.RowCard
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import io.github.voxelium.core.launch.GameInstaller
import io.github.voxelium.core.launch.LoaderVersion
import io.github.voxelium.core.launch.ModLoader
import io.github.voxelium.core.launch.ModLoaderMeta
import io.github.voxelium.core.manifest.VersionManifest
import io.github.voxelium.core.manifest.VersionType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Voxelium: Install. Minecraft versions on the left; for the chosen one, the mod loader, its build
 * (the recommended one preselected) and one Install button on the right. Modpacks open from the top.
 */
@Composable
fun VoxeliumInstallScreen(backScreenViewModel: ScreenBackStackViewModel) {
    BaseScreen(screenKey = NormalNavKey.VoxeliumInstall, currentKey = backScreenViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        val manifest by VoxeliumGame.manifest.collectAsStateWithLifecycle()
        val installed by VoxeliumGame.installed.collectAsStateWithLifecycle()
        val states by VoxeliumGame.installState.collectAsStateWithLifecycle()
        val tasks by TaskSystem.tasksFlow.collectAsStateWithLifecycle()
        val snapshots = AllSettings.voxeliumShowSnapshots.state
        var selected by remember { mutableStateOf<String?>(null) }
        var showModpacks by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (VoxeliumGame.manifest.value !is VoxeliumGame.ManifestState.Loaded) VoxeliumGame.loadManifest()
            VoxeliumGame.refreshInstalled()
        }

        val coreIds = installed.filter { it.isCore }.map { it.id }.toSet()
        Row(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.width(330.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PageHeader(title = stringResource(R.string.voxelium_ui_install)) {
                    OutlinedButton(onClick = { showModpacks = true }) { Text(stringResource(R.string.voxelium_modpacks)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !snapshots, onClick = { AllSettings.voxeliumShowSnapshots.save(false) }, label = { Text(stringResource(R.string.voxelium_ui_releases)) })
                    FilterChip(selected = snapshots, onClick = { AllSettings.voxeliumShowSnapshots.save(true) }, label = { Text(stringResource(R.string.voxelium_ui_snapshots)) })
                }
                when (val m = manifest) {
                    is VoxeliumGame.ManifestState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    is VoxeliumGame.ManifestState.Failed -> {
                        Text(stringResource(R.string.voxelium_versions_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { VoxeliumGame.loadManifest() }) { Text(stringResource(R.string.voxelium_refresh)) }
                    }
                    is VoxeliumGame.ManifestState.Loaded -> {
                        val list = VoxeliumGame.visibleVersions(m.manifest, snapshots)
                        LaunchedEffect(list) { if (selected == null || list.none { it.id == selected }) selected = list.firstOrNull()?.id }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(list, key = { it.id }) { entry ->
                                RowCard(selected = entry.id == selected, onClick = { selected = entry.id }) {
                                    Text(entry.id, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    if (entry.id in coreIds) {
                                        Text(stringResource(R.string.voxelium_installed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Text(DATE.format(entry.releaseTime.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val entry = (manifest as? VoxeliumGame.ManifestState.Loaded)?.manifest?.entry(selected ?: "")
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                if (entry == null) {
                    Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.voxelium_ui_pick_version), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    InstallPanel(
                        entry = entry,
                        alreadyInstalled = entry.id in coreIds,
                        state = states[entry.id],
                        busy = tasks.any { it.id == "voxelium-install-${entry.id}" }
                    )
                }
            }
        }

        if (showModpacks) {
            ModpacksDialog(onDismiss = {
                showModpacks = false
                VoxeliumGame.resetModpack()
            })
        }
    }
}

@Composable
private fun InstallPanel(entry: VersionManifest.Entry, alreadyInstalled: Boolean, state: InstallState?, busy: Boolean) {
    var loader by remember(entry.id) { mutableStateOf(ModLoader.VANILLA) }
    var builds by remember(entry.id) { mutableStateOf<List<LoaderVersion>>(emptyList()) }
    var build by remember(entry.id) { mutableStateOf("") }
    var loading by remember(entry.id) { mutableStateOf(false) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }

    LaunchedEffect(entry.id, loader, attempt) {
        builds = emptyList()
        build = ""
        error = null
        if (loader == ModLoader.VANILLA) return@LaunchedEffect
        loading = true
        runCatching { VoxeliumGame.loaderBuilds(loader, entry.id) }
            .onSuccess { list -> builds = list; build = ModLoaderMeta.recommended(list)?.version ?: "" }
            .onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.voxelium_ui_minecraft_version, entry.id), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(if (entry.type == VersionType.SNAPSHOT) R.string.voxelium_ui_snapshot else R.string.voxelium_ui_release),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(stringResource(R.string.voxelium_mod_loader), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (l in ModLoader.entries) {
                val available = l.isAvailable(entry.id)
                val chosen = l == loader
                Surface(
                    onClick = { if (available) loader = l },
                    enabled = available,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            l.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (available) shortBlurb(l) else stringResource(R.string.voxelium_ui_not_offered, l.oldestMinecraft ?: "?"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (loader != ModLoader.VANILLA) {
            Text(stringResource(R.string.voxelium_loader_version, loader.displayName), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.voxelium_try_again)) }
                }
                else -> Box {
                    val recommended = ModLoaderMeta.recommended(builds)?.version
                    fun label(b: LoaderVersion): String =
                        b.version + (if (!b.stable) " (beta)" else "") + (if (b.version == recommended) " · recommended" else "")
                    OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(builds.firstOrNull { it.version == build }?.let(::label) ?: build, modifier = Modifier.weight(1f))
                        Icon(painterResource(R.drawable.ic_list_down), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = picker, onDismissRequest = { picker = false }) {
                        for (b in builds.take(40)) {
                            DropdownMenuItem(text = { Text(label(b)) }, onClick = { build = b.version; picker = false })
                        }
                    }
                }
            }
            Text(stringResource(R.string.voxelium_ui_modded_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loader.usesForgeInstaller) {
                Text(stringResource(R.string.voxelium_forge_note, loader.displayName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (alreadyInstalled) {
            Text(stringResource(R.string.voxelium_ui_installed_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.weight(1f))
        ProgressLine(state)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !busy && loader.isAvailable(entry.id) && (loader == ModLoader.VANILLA || build.isNotEmpty()),
                onClick = { VoxeliumGame.install(entry, loader, build) },
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    if (loader == ModLoader.VANILLA) stringResource(R.string.voxelium_install_vanilla, entry.id)
                    else stringResource(R.string.voxelium_install_loader, loader.displayName, entry.id)
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(state: InstallState?) {
    when (state) {
        null -> {}
        is InstallState.Preparing -> Text(stringResource(R.string.voxelium_preparing), style = MaterialTheme.typography.bodySmall)
        is InstallState.Downloading -> {
            val stage = stringResource(if (state.stage == GameInstaller.Stage.ASSETS) R.string.voxelium_stage_assets else R.string.voxelium_stage_game_files)
            LinearProgressIndicator(progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f }, modifier = Modifier.fillMaxWidth())
            Text(
                if (state.failed > 0) stringResource(R.string.voxelium_progress_failed, stage, state.done, state.total, state.failed)
                else stringResource(R.string.voxelium_progress, stage, state.done, state.total),
                style = MaterialTheme.typography.bodySmall
            )
        }
        is InstallState.SettingUp -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.voxelium_setting_up, state.loader), style = MaterialTheme.typography.bodySmall)
        }
        is InstallState.Finished -> Text(
            if (state.failed == 0) stringResource(R.string.voxelium_all_verified, state.ok) else stringResource(R.string.voxelium_some_failed, state.failed),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.failed == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        is InstallState.Failed -> Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun shortBlurb(l: ModLoader): String = stringResource(
    when (l) {
        ModLoader.VANILLA -> R.string.voxelium_ui_short_vanilla
        ModLoader.FABRIC -> R.string.voxelium_ui_short_fabric
        ModLoader.QUILT -> R.string.voxelium_ui_short_quilt
        ModLoader.FORGE -> R.string.voxelium_ui_short_forge
        ModLoader.NEOFORGE -> R.string.voxelium_ui_short_neoforge
    }
)

private val DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
