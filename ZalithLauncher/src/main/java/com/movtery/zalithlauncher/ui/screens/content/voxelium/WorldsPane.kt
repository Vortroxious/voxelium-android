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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.voxelium.VoxeliumContent
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.utils.file.shareFile
import io.github.voxelium.core.content.WorldItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Voxelium: a version's worlds (import a .zip, share as .zip, delete). Mirrors the iOS app's
 * WorldsView. Worlds are copied only while Minecraft is closed (the launcher is in front then).
 */
@Composable
fun WorldsPane(content: VoxeliumContent, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var worlds by remember { mutableStateOf<List<WorldItem>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<WorldItem?>(null) }

    LaunchedEffect(reload) { worlds = runCatching { content.listWorlds() }.getOrDefault(emptyList()) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = null
            val zip = withContext(Dispatchers.IO) { copyToCache(context, uri, "world-import") }
            if (zip == null) {
                message = context.getString(R.string.voxelium_modpack_import_failed)
            } else {
                runCatching { content.importWorldZip(zip) }
                    .onSuccess { message = context.getString(R.string.voxelium_world_imported, it.name) }
                    .onFailure { message = it.message ?: it.toString() }
                withContext(Dispatchers.IO) { zip.delete() }
            }
            busy = false
            reload++
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.voxelium_world_count, worlds?.size ?: 0),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                Text(stringResource(R.string.voxelium_import_world))
            }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.voxelium_working), style = MaterialTheme.typography.bodySmall)
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        val list = worlds
        when {
            list == null -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            list.isEmpty() -> Text(stringResource(R.string.voxelium_no_worlds), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(list, key = { it.id }) { world ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(world.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        world.lastPlayed?.let { DATE.format(it.atZone(ZoneId.systemDefault())) },
                                        formatSize(world.size)
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { content.exportWorld(world) }
                                        .onSuccess { zip -> shareFile(context, zip) }
                                        .onFailure { message = it.message ?: it.toString() }
                                    busy = false
                                }
                            }) { Text(stringResource(R.string.voxelium_share_zip)) }
                            IconButton(enabled = !busy, onClick = { pendingDelete = world }) {
                                Icon(painterResource(R.drawable.ic_delete_outlined), contentDescription = stringResource(R.string.voxelium_delete_world))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        Text(stringResource(R.string.voxelium_worlds_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    pendingDelete?.let { world ->
        SimpleAlertDialog(
            title = stringResource(R.string.voxelium_delete_world_title, world.name),
            text = stringResource(R.string.voxelium_delete_world_text),
            confirmText = stringResource(R.string.voxelium_delete_world),
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    runCatching { content.deleteWorld(world) }.onFailure { message = it.message ?: it.toString() }
                    reload++
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
