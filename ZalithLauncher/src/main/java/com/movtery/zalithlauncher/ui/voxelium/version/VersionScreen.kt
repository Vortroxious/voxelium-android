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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.voxelium.VoxeliumContent
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.voxelium.ContentLibraryPane
import com.movtery.zalithlauncher.ui.screens.content.voxelium.WorldsPane
import com.movtery.zalithlauncher.ui.screens.content.voxelium.kindTitle
import com.movtery.zalithlauncher.ui.voxelium.components.LoaderBadge
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.ui.voxelium.play.playVersion
import com.movtery.zalithlauncher.ui.voxelium.play.statusText
import com.movtery.zalithlauncher.ui.voxelium.play.zalithVersion
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import io.github.voxelium.core.content.ContentKind

private sealed interface VersionTab {
    data class Content(val kind: ContentKind) : VersionTab
    data object Worlds : VersionTab
    data object Settings : VersionTab
}

/**
 * Voxelium: one installed version. Play on top; tabs for its mods, resource packs, shader packs,
 * worlds and settings (replacing the old dialogs).
 */
@Composable
fun VoxeliumVersionScreen(
    key: NormalNavKey.VoxeliumVersion,
    backScreenViewModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    onBack: () -> Unit,
    onVersionSettings: (Version) -> Unit
) {
    BaseScreen(screenKey = key, currentKey = backScreenViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        val installed by VoxeliumGame.installed.collectAsStateWithLifecycle()
        val states by VoxeliumGame.installState.collectAsStateWithLifecycle()
        val tasks by TaskSystem.tasksFlow.collectAsStateWithLifecycle()
        val v = installed.firstOrNull { it.id == key.id }

        Column(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (v == null) {
                PageHeader(title = key.id, onBack = onBack)
                Text(stringResource(R.string.voxelium_ui_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val content = remember(v.id) { VoxeliumContent(v) }
            val busy = tasks.any { it.id == "voxelium-install-${v.id}" }
            PageHeader(title = v.title, subtitle = statusText(states[v.id]) ?: v.subtitle, onBack = onBack) {
                LoaderBadge(v.loader, v.isModpack)
                Button(onClick = { playVersion(v.id, eventViewModel) }, enabled = !busy) {
                    Icon(painterResource(R.drawable.ic_play_arrow_filled), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.voxelium_ui_play))
                }
            }

            val tabs = remember(v.id) { content.kinds.map { VersionTab.Content(it) } + VersionTab.Worlds + VersionTab.Settings }
            var tab by rememberSaveable(v.id) { mutableIntStateOf(0) }
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = {
                        Text(
                            when (t) {
                                is VersionTab.Content -> kindTitle(t.kind)
                                VersionTab.Worlds -> stringResource(R.string.voxelium_ui_tab_worlds)
                                VersionTab.Settings -> stringResource(R.string.voxelium_ui_tab_settings)
                            }
                        )
                    })
                }
            }
            val pane = Modifier.fillMaxWidth().weight(1f)
            when (val t = tabs[tab]) {
                is VersionTab.Content -> ContentLibraryPane(content, t.kind, modifier = pane)
                VersionTab.Worlds -> WorldsPane(content, modifier = pane)
                VersionTab.Settings -> SettingsPane(v, busy, pane, onVersionSettings, onDeleted = onBack)
            }
        }
    }
}

@Composable
private fun SettingsPane(
    v: VoxeliumGame.InstalledVersion,
    busy: Boolean,
    modifier: Modifier,
    onVersionSettings: (Version) -> Unit,
    onDeleted: () -> Unit
) {
    var deleting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingRow(
            title = stringResource(R.string.voxelium_ui_version_settings_title),
            text = stringResource(R.string.voxelium_ui_version_settings_more)
        ) {
            OutlinedButton(onClick = { zalithVersion(v.id)?.let(onVersionSettings) }) { Text(stringResource(R.string.voxelium_ui_version_settings_open)) }
        }
        // ZalithLauncher2 checks its own versions' files when they launch.
        if (v.isCore) {
            SettingRow(title = stringResource(R.string.voxelium_repair), text = stringResource(R.string.voxelium_ui_repair_hint)) {
                OutlinedButton(onClick = { VoxeliumGame.repair(v.id) }, enabled = !busy) { Text(stringResource(R.string.voxelium_repair)) }
            }
        }
        SettingRow(title = stringResource(R.string.voxelium_delete), text = stringResource(R.string.voxelium_ui_delete_hint)) {
            OutlinedButton(onClick = { deleting = true }, enabled = !busy) {
                Text(stringResource(R.string.generic_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    if (deleting) {
        when (val check = remember(v.id) { VoxeliumGame.deleteCheck(v.id) }) {
            is VoxeliumGame.DeleteCheck.NeededBy -> SimpleAlertDialog(
                title = stringResource(R.string.voxelium_not_deleted),
                text = stringResource(R.string.voxelium_needed_by, v.title, check.others.joinToString(", ")),
                onDismiss = { deleting = false }
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
                        deleting = false
                        VoxeliumGame.delete(v.id) { error -> problem = error }
                        onDeleted()
                    },
                    onDismiss = { deleting = false }
                )
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, text: String, action: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action()
        }
    }
}
