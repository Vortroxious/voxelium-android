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

package com.movtery.zalithlauncher.ui.screens.content.voxelium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.isMicrosoftAccount
import com.movtery.zalithlauncher.game.path.getGameHome
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.game.voxelium.VoxeliumContent
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame.InstallState
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.DefaultSwitch
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.home.HomeCards
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import com.movtery.zalithlauncher.viewmodel.sendToast
import io.github.voxelium.core.launch.GameInstaller
import io.github.voxelium.core.launch.ModLoader
import io.github.voxelium.core.manifest.VersionManifest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Voxelium: the Versions screen, installing through VoxeliumCore (mirrors the iOS app's
 * VersionsView). Installed versions play through ZalithLauncher2's launcher.
 */
@Composable
fun VoxeliumVersionsScreen(
    backScreenViewModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    onAccounts: () -> Unit,
    onClassicHome: () -> Unit,
    onVersionSettings: (Version) -> Unit
) {
    BaseScreen(
        screenKey = NormalNavKey.Voxelium,
        currentKey = backScreenViewModel.mainScreen.currentKey
    ) { isVisible ->
        if (!isVisible) return@BaseScreen
        val manifest by VoxeliumGame.manifest.collectAsStateWithLifecycle()
        val installed by VoxeliumGame.installed.collectAsStateWithLifecycle()
        val states by VoxeliumGame.installState.collectAsStateWithLifecycle()
        val tasks by TaskSystem.tasksFlow.collectAsStateWithLifecycle()
        val showSnapshots = AllSettings.voxeliumShowSnapshots.state
        val zalithVersions by VersionsManager.versions.collectAsStateWithLifecycle()
        val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()

        var installTarget by remember { mutableStateOf<Pair<VersionManifest.Entry, ModLoader>?>(null) }
        var deleteTarget by remember { mutableStateOf<VoxeliumGame.InstalledVersion?>(null) }
        var message by remember { mutableStateOf<Pair<String, String>?>(null) }
        var showModpacks by remember { mutableStateOf(false) }
        var contentFor by remember { mutableStateOf<VoxeliumGame.InstalledVersion?>(null) }
        var worldsFor by remember { mutableStateOf<VoxeliumGame.InstalledVersion?>(null) }

        LaunchedEffect(Unit) {
            if (VoxeliumGame.manifest.value !is VoxeliumGame.ManifestState.Loaded) VoxeliumGame.loadManifest()
        }
        // Also lists the versions ZalithLauncher2 installed, so refresh when its list changes.
        LaunchedEffect(zalithVersions) { VoxeliumGame.refreshInstalled() }

        fun busy(key: String) = tasks.any { it.id == "voxelium-install-$key" }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Required by Zalith Launcher 2's GPLv3 section 7 terms; this is the home screen now.
            items(HomeCards.systemCards(), key = { it.id }) { card -> card.content() }
            item {
                BackgroundCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val a = account
                            Text(a?.username ?: stringResource(R.string.voxelium_no_account), style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    a == null -> stringResource(R.string.voxelium_no_account_hint)
                                    a.isMicrosoftAccount() -> stringResource(R.string.voxelium_account_microsoft)
                                    a.isLocalAccount() -> stringResource(R.string.voxelium_account_offline)
                                    else -> a.accountType ?: ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onAccounts) { Text(stringResource(R.string.voxelium_accounts)) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onClassicHome) { Text(stringResource(R.string.voxelium_classic_home)) }
                    }
                }
            }

            if (installed.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.voxelium_installed)) }
                items(installed, key = { "installed-" + it.id }) { v ->
                    InstalledRow(
                        version = v,
                        state = states[v.id],
                        busy = busy(v.id),
                        onPlay = {
                            VoxeliumGame.play(
                                v.id,
                                launch = { version -> eventViewModel.sendEvent(EventViewModel.Event.Launch.Game(version)) },
                                onError = { eventViewModel.sendToast(androidText(it)) }
                            )
                        },
                        onRepair = { VoxeliumGame.repair(v.id) },
                        onSettings = {
                            VersionsManager.loadVersion(getGameHome(), v.id)?.let(onVersionSettings)
                        },
                        onContent = { contentFor = v },
                        onWorlds = { worldsFor = v },
                        onDelete = { deleteTarget = v }
                    )
                }
            }

            item {
                BackgroundCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { showModpacks = true }) {
                            Text(stringResource(R.string.voxelium_modpacks_button))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.voxelium_show_snapshots))
                        Spacer(Modifier.width(12.dp))
                        DefaultSwitch(
                            checked = showSnapshots,
                            onCheckedChange = { AllSettings.voxeliumShowSnapshots.save(it) }
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.voxelium_versions_section)) }
            when (val m = manifest) {
                is VoxeliumGame.ManifestState.Loading -> item {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.voxelium_loading_versions))
                    }
                }
                is VoxeliumGame.ManifestState.Failed -> item {
                    BackgroundCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.voxelium_versions_failed))
                            Button(onClick = { VoxeliumGame.loadManifest() }) {
                                Text(stringResource(R.string.voxelium_refresh))
                            }
                        }
                    }
                }
                is VoxeliumGame.ManifestState.Loaded -> {
                    val installedIds = installed.filter { it.isCore }.map { it.id }.toSet()
                    items(VoxeliumGame.visibleVersions(m.manifest, showSnapshots), key = { "version-" + it.id }) { entry ->
                        VersionRow(
                            entry = entry,
                            installed = entry.id in installedIds,
                            state = states[entry.id],
                            busy = busy(entry.id),
                            onInstall = { loader -> installTarget = entry to loader },
                            onRepair = { VoxeliumGame.repair(entry.id) }
                        )
                    }
                }
            }
        }

        contentFor?.let { v ->
            val content = remember(v.id) { VoxeliumContent(v) }
            ContentLibraryDialog(content, onDismiss = { contentFor = null })
        }
        worldsFor?.let { v ->
            val content = remember(v.id) { VoxeliumContent(v) }
            WorldsDialog(content, onDismiss = { worldsFor = null })
        }

        if (showModpacks) {
            ModpacksDialog(onDismiss = {
                showModpacks = false
                VoxeliumGame.resetModpack()
            })
        }

        installTarget?.let { (entry, loader) ->
            InstallVersionDialog(
                entry = entry,
                initialLoader = loader,
                onDismiss = { installTarget = null },
                onInstall = { l, build ->
                    installTarget = null
                    VoxeliumGame.install(entry, l, build)
                }
            )
        }

        deleteTarget?.let { v ->
            when (val check = remember(v.id) { VoxeliumGame.deleteCheck(v.id) }) {
                is VoxeliumGame.DeleteCheck.NeededBy -> SimpleAlertDialog(
                    title = stringResource(R.string.voxelium_not_deleted),
                    text = stringResource(R.string.voxelium_needed_by, v.title, check.others.joinToString(", ")),
                    onDismiss = { deleteTarget = null }
                )
                is VoxeliumGame.DeleteCheck.Confirm -> {
                    val worlds = if (check.worlds == 0) stringResource(R.string.voxelium_delete_its_worlds)
                    else pluralStringResource(R.plurals.voxelium_delete_world_count, check.worlds, check.worlds)
                    SimpleAlertDialog(
                        title = stringResource(R.string.voxelium_delete_title, v.title),
                        text = if (check.ownFolder) stringResource(R.string.voxelium_delete_own_folder, worlds)
                        else stringResource(R.string.voxelium_delete_shared, v.title),
                        confirmText = stringResource(R.string.generic_delete),
                        onConfirm = {
                            deleteTarget = null
                            VoxeliumGame.delete(v.id) { error -> message = v.title to error }
                        },
                        onDismiss = { deleteTarget = null }
                    )
                }
            }
        }

        message?.let { (title, text) ->
            SimpleAlertDialog(
                title = stringResource(R.string.voxelium_not_deleted) + ": " + title,
                text = text,
                onDismiss = { message = null }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun InstalledRow(
    version: VoxeliumGame.InstalledVersion,
    state: InstallState?,
    busy: Boolean,
    onPlay: () -> Unit,
    onRepair: () -> Unit,
    onSettings: () -> Unit,
    onContent: () -> Unit,
    onWorlds: () -> Unit,
    onDelete: () -> Unit
) {
    BackgroundCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(version.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (version.isModded) version.subtitle else stringResource(R.string.voxelium_vanilla),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Button(onClick = onPlay) {
                        Icon(painterResource(R.drawable.ic_play_arrow_filled), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.voxelium_play))
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (version.isModded) R.string.voxelium_mods_and_packs else R.string.voxelium_resource_packs_menu)) },
                                onClick = { menu = false; onContent() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.voxelium_worlds_menu)) },
                                onClick = { menu = false; onWorlds() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.voxelium_version_settings)) },
                                onClick = { menu = false; onSettings() }
                            )
                            // ZalithLauncher2 checks its own versions' files when they launch.
                            if (version.isCore) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.voxelium_repair)) },
                                    onClick = { menu = false; onRepair() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.voxelium_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete() }
                            )
                        }
                    }
                }
            }
            InstallStatus(state)
        }
    }
}

@Composable
private fun VersionRow(
    entry: VersionManifest.Entry,
    installed: Boolean,
    state: InstallState?,
    busy: Boolean,
    onInstall: (ModLoader) -> Unit,
    onRepair: () -> Unit
) {
    BackgroundCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.id, style = MaterialTheme.typography.titleSmall)
                    Text(
                        DATE.format(entry.releaseTime.atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    busy -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    installed -> {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            FilledTonalButton(onClick = { menu = true }) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.voxelium_installed))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.voxelium_install_with_loader)) },
                                    onClick = { menu = false; onInstall(ModLoader.FABRIC) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.voxelium_repair)) },
                                    onClick = { menu = false; onRepair() }
                                )
                            }
                        }
                    }
                    else -> Button(onClick = { onInstall(ModLoader.VANILLA) }) {
                        Text(stringResource(R.string.voxelium_install))
                    }
                }
            }
            InstallStatus(state)
        }
    }
}

@Composable
private fun InstallStatus(state: InstallState?) {
    when (state) {
        null -> {}
        is InstallState.Preparing -> StatusText(stringResource(R.string.voxelium_preparing))
        is InstallState.Downloading -> {
            val stage = stringResource(
                if (state.stage == GameInstaller.Stage.ASSETS) R.string.voxelium_stage_assets else R.string.voxelium_stage_game_files
            )
            LinearProgressIndicator(
                progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            StatusText(
                if (state.failed > 0) stringResource(R.string.voxelium_progress_failed, stage, state.done, state.total, state.failed)
                else stringResource(R.string.voxelium_progress, stage, state.done, state.total)
            )
        }
        is InstallState.SettingUp -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            StatusText(stringResource(R.string.voxelium_setting_up, state.loader))
        }
        is InstallState.Finished -> StatusText(
            if (state.failed == 0) stringResource(R.string.voxelium_all_verified, state.ok)
            else stringResource(R.string.voxelium_some_failed, state.failed),
            color = if (state.failed == 0) Color(0xFF2E9E4F) else MaterialTheme.colorScheme.error
        )
        is InstallState.Failed -> StatusText(state.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StatusText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 4.dp))
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
