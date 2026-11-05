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

package com.movtery.zalithlauncher.ui.voxelium.play

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.isMicrosoftAccount
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.game.voxelium.VoxeliumContent
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.voxelium.components.LoaderBadge
import com.movtery.zalithlauncher.ui.voxelium.components.PageHeader
import com.movtery.zalithlauncher.ui.voxelium.components.RoundPlayButton
import com.movtery.zalithlauncher.ui.voxelium.components.RowCard
import com.movtery.zalithlauncher.ui.voxelium.components.SectionLabel
import com.movtery.zalithlauncher.ui.voxelium.theme.Voxelium
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import com.movtery.zalithlauncher.viewmodel.sendToast
import io.github.voxelium.core.content.ContentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plays a version through ZalithLauncher2's launcher and remembers it as the last played one. */
fun playVersion(id: String, eventViewModel: EventViewModel) {
    VoxeliumGame.play(
        id,
        launch = { version ->
            VersionsManager.saveCurrentVersion(id)
            eventViewModel.sendEvent(EventViewModel.Event.Launch.Game(version))
        },
        onError = { eventViewModel.sendToast(androidText(it)) }
    )
}

/**
 * Voxelium: Play, the home screen. The last played version in a big card with Play; every
 * installed version beside it, each opening its detail page.
 */
@Composable
fun VoxeliumPlayScreen(
    backScreenViewModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    onAccounts: () -> Unit,
    onInstall: () -> Unit,
    onOpenVersion: (String) -> Unit
) {
    BaseScreen(screenKey = NormalNavKey.Voxelium, currentKey = backScreenViewModel.mainScreen.currentKey) { isVisible ->
        if (!isVisible) return@BaseScreen
        val installed by VoxeliumGame.installed.collectAsStateWithLifecycle()
        val states by VoxeliumGame.installState.collectAsStateWithLifecycle()
        val zalithVersions by VersionsManager.versions.collectAsStateWithLifecycle()
        val current by VersionsManager.currentVersion.collectAsStateWithLifecycle()
        val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()

        LaunchedEffect(zalithVersions) { VoxeliumGame.refreshInstalled() }

        val last = installed.firstOrNull { it.id == current?.getVersionName() } ?: installed.firstOrNull()

        Column(modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(title = stringResource(R.string.voxelium_ui_play)) {
                Surface(
                    onClick = onAccounts,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_person_outlined), contentDescription = null, modifier = Modifier.size(22.dp))
                        Column {
                            val a = account
                            Text(a?.username ?: stringResource(R.string.voxelium_ui_no_account), style = MaterialTheme.typography.labelMedium)
                            if (a != null) {
                                Text(
                                    when {
                                        a.isMicrosoftAccount() -> stringResource(R.string.voxelium_account_microsoft)
                                        a.isLocalAccount() -> stringResource(R.string.voxelium_account_offline)
                                        else -> a.accountType ?: ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (last == null) {
                    EmptyCard(onInstall)
                } else {
                    LastPlayedCard(last, onPlay = { playVersion(last.id, eventViewModel) }, onOpen = { onOpenVersion(last.id) })
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(stringResource(R.string.voxelium_ui_installed_count, installed.size), modifier = Modifier.weight(1f))
                            TextButton(onClick = onInstall) { Text(stringResource(R.string.voxelium_ui_add_install)) }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(installed.filter { it.id != last.id }, key = { it.id }) { v ->
                                RowCard(onClick = { onOpenVersion(v.id) }) {
                                    LoaderBadge(v.loader, v.isModpack)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            statusText(states[v.id]) ?: v.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    RoundPlayButton(stringResource(R.string.voxelium_ui_play_version, v.title), onClick = { playVersion(v.id, eventViewModel) })
                                }
                            }
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.voxelium_ui_notice, BuildKeys.LAUNCHER_NAME) +
                    if (BuildConfig.DEBUG) " " + stringResource(R.string.voxelium_ui_test_build) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LastPlayedCard(v: VoxeliumGame.InstalledVersion, onPlay: () -> Unit, onOpen: () -> Unit) {
    val c = Voxelium.colors
    val counts by produceState<Pair<Int, Int>?>(null, v.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val content = VoxeliumContent(v)
                val mods = if (v.isModded) content.manager.list(ContentKind.MODS).count { it.isEnabled } else 0
                mods to content.worlds.list().size
            }.getOrNull()
        }
    }
    Surface(
        modifier = Modifier.width(330.dp).fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
        color = c.highlight,
        border = BorderStroke(1.dp, c.highlightBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.voxelium_ui_last_played).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(v.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(v.subtitle, style = MaterialTheme.typography.bodySmall, color = c.onHighlight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            counts?.let { (mods, worlds) ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (v.isModded) Chip(pluralStringResource(R.plurals.voxelium_ui_mods_count, mods, mods))
                    Chip(pluralStringResource(R.plurals.voxelium_ui_worlds_count, worlds, worlds))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow_filled), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.voxelium_ui_play), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedIconButton(onClick = onOpen, modifier = Modifier.size(48.dp), shape = CircleShape) {
                    Icon(painterResource(R.drawable.ic_more_horiz), contentDescription = stringResource(R.string.voxelium_ui_open_version))
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun EmptyCard(onInstall: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.voxelium_ui_nothing_installed), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.voxelium_ui_nothing_installed_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onInstall) { Text(stringResource(R.string.voxelium_ui_go_install)) }
        }
    }
}

/** A row's install or repair progress, as a short line. */
@Composable
internal fun statusText(state: VoxeliumGame.InstallState?): String? = when (state) {
    null, is VoxeliumGame.InstallState.Finished -> null
    is VoxeliumGame.InstallState.Preparing -> stringResource(R.string.voxelium_preparing)
    is VoxeliumGame.InstallState.Downloading -> stringResource(R.string.voxelium_progress, stringResource(R.string.voxelium_stage_game_files), state.done, state.total)
    is VoxeliumGame.InstallState.SettingUp -> stringResource(R.string.voxelium_setting_up, state.loader)
    is VoxeliumGame.InstallState.Failed -> state.message
}

/** For pages that only have a version id. */
fun findInstalled(id: String): VoxeliumGame.InstalledVersion? = VoxeliumGame.installed.value.firstOrNull { it.id == id }

/** ZalithLauncher2's version object, for its settings screen. */
fun zalithVersion(id: String): Version? =
    VersionsManager.loadVersion(com.movtery.zalithlauncher.game.path.getGameHome(), id)
