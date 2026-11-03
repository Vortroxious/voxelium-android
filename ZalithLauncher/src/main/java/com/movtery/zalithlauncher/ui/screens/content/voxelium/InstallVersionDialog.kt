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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame
import com.movtery.zalithlauncher.ui.components.rememberDialogMaxHeight
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import io.github.voxelium.core.launch.LoaderVersion
import io.github.voxelium.core.launch.ModLoader
import io.github.voxelium.core.launch.ModLoaderMeta
import io.github.voxelium.core.manifest.VersionManifest

private const val SHOWN_BUILDS = 12

/**
 * Voxelium: which mod loader (and build) to install a Minecraft version with. Mirrors the iOS
 * app's InstallVersionSheet.
 */
@Composable
fun InstallVersionDialog(
    entry: VersionManifest.Entry,
    initialLoader: ModLoader,
    onDismiss: () -> Unit,
    onInstall: (ModLoader, String) -> Unit
) {
    var loader by remember { mutableStateOf(if (initialLoader.isAvailable(entry.id)) initialLoader else ModLoader.VANILLA) }
    var builds by remember { mutableStateOf<List<LoaderVersion>>(emptyList()) }
    var build by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    val needsBuild = loader != ModLoader.VANILLA

    LaunchedEffect(loader, attempt) {
        builds = emptyList()
        build = ""
        error = null
        showAll = false
        if (!needsBuild) return@LaunchedEffect
        loading = true
        runCatching { VoxeliumGame.loaderBuilds(loader, entry.id) }
            .onSuccess { list ->
                builds = list
                build = ModLoaderMeta.recommended(list)?.version ?: ""
            }
            .onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(6.dp).heightIn(max = rememberDialogMaxHeight()),
            shape = MaterialTheme.shapes.extraLarge,
            color = cardColor(false),
            contentColor = onCardColor(),
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.voxelium_install_title, entry.id), style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.voxelium_mod_loader), style = MaterialTheme.typography.labelLarge)
                    for (l in ModLoader.entries) {
                        val available = l.isAvailable(entry.id)
                        ChoiceRow(
                            selected = loader == l,
                            enabled = available,
                            title = l.displayName,
                            subtitle = if (available) blurb(l) else stringResource(R.string.voxelium_loader_for_newer, l.oldestMinecraft ?: "?"),
                            onClick = { loader = l }
                        )
                    }
                    if (loader.usesForgeInstaller) {
                        Text(
                            stringResource(R.string.voxelium_forge_note, loader.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (needsBuild) {
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.voxelium_loader_version, loader.displayName), style = MaterialTheme.typography.labelLarge)
                        when {
                            loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.voxelium_loading_builds, loader.displayName))
                            }
                            error != null -> {
                                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.voxelium_try_again)) }
                            }
                            else -> {
                                val recommended = ModLoaderMeta.recommended(builds)?.version
                                val shown = if (showAll) builds else builds.take(SHOWN_BUILDS)
                                for (b in shown) {
                                    var label = if (b.stable) b.version else stringResource(R.string.voxelium_build_beta, b.version)
                                    if (b.version == recommended) label = stringResource(R.string.voxelium_build_recommended, label)
                                    ChoiceRow(selected = build == b.version, enabled = true, title = label, subtitle = null) {
                                        build = b.version
                                    }
                                }
                                if (builds.size > SHOWN_BUILDS && !showAll) {
                                    TextButton(onClick = { showAll = true }) {
                                        Text(stringResource(R.string.voxelium_show_all_builds, builds.size))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = loader.isAvailable(entry.id) && (!needsBuild || build.isNotEmpty()),
                        onClick = { onInstall(loader, build) }
                    ) {
                        Text(
                            if (loader == ModLoader.VANILLA) stringResource(R.string.voxelium_install_vanilla, entry.id)
                            else stringResource(R.string.voxelium_install_loader, loader.displayName, entry.id)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, enabled: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column {
            Text(
                title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun blurb(l: ModLoader): String = stringResource(
    when (l) {
        ModLoader.VANILLA -> R.string.voxelium_blurb_vanilla
        ModLoader.FABRIC -> R.string.voxelium_blurb_fabric
        ModLoader.QUILT -> R.string.voxelium_blurb_quilt
        ModLoader.FORGE -> R.string.voxelium_blurb_forge
        ModLoader.NEOFORGE -> R.string.voxelium_blurb_neoforge
    }
)
