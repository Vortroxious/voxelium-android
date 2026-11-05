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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.voxelium.VoxeliumContent
import com.movtery.zalithlauncher.ui.components.DefaultSwitch
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import io.github.voxelium.core.content.ContentItem
import io.github.voxelium.core.content.ContentKind
import io.github.voxelium.core.content.ContentManager
import io.github.voxelium.core.content.ModUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where content is browsed from. */
enum class ContentSource { MODRINTH, CURSEFORGE }

/**
 * Voxelium: one kind of a version's content (mods, resource packs or shader packs) as a tab of the
 * version page: switch on/off, remove, Modrinth updates, add from Modrinth, CurseForge or a file.
 * Mirrors the iOS app's ContentLibraryView.
 */
@Composable
fun ContentLibraryPane(content: VoxeliumContent, kind: ContentKind, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<ContentItem?>(null) }
    var browsing by remember { mutableStateOf<ContentSource?>(null) }
    var updates by remember { mutableStateOf<Map<String, ModUpdate>>(emptyMap()) }
    var checking by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shaders by remember { mutableStateOf(content.iris.shadersEnabled) }

    LaunchedEffect(kind, reload) {
        items = runCatching { content.list(kind) }.getOrDefault(emptyList())
    }
    // Modrinth updates for the files that came from Modrinth (matched by SHA-1)
    LaunchedEffect(kind) {
        updates = emptyMap()
        checking = true
        updates = runCatching { content.checkUpdates(kind) }.getOrDefault(emptyList()).associateBy { it.file.path }
        checking = false
    }

    fun update(list: List<ModUpdate>) {
        scope.launch {
            updating = updating + list.map { it.file.path }
            val report = runCatching { content.installUpdates(list, kind) }
            updating = updating - list.map { it.file.path }.toSet()
            report.onFailure { message = it.message ?: it.toString() }
                .onSuccess { if (!it.succeeded) message = context.getString(R.string.voxelium_files_failed, it.failures.size) }
            updates = updates - list.filter { report.getOrNull()?.succeeded == true }.map { it.file.path }.toSet()
            reload++
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val problems = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val file = copyToCache(context, uri, "content-import") ?: return@mapNotNull context.getString(R.string.voxelium_modpack_import_failed)
                    try {
                        content.manager.add(file, kind, content.loader)
                        null
                    } catch (e: Exception) {
                        e.message ?: e.toString()
                    } finally {
                        file.delete()
                    }
                }
            }
            if (problems.isNotEmpty()) message = problems.joinToString("\n")
            reload++
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.voxelium_content_count, kindTitle(kind), items.count { it.isEnabled }, items.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { browsing = ContentSource.MODRINTH }) { Text(stringResource(R.string.voxelium_browse_modrinth)) }
            if (content.curseForgeAvailable) {
                OutlinedButton(onClick = { browsing = ContentSource.CURSEFORGE }) { Text(stringResource(R.string.voxelium_browse_curseforge)) }
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.voxelium_import_file)) }
        }

        if (kind == ContentKind.SHADER_PACKS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.voxelium_shaders_in_game))
                    Text(
                        stringResource(R.string.voxelium_shaders_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DefaultSwitch(checked = shaders, onCheckedChange = { on ->
                    runCatching { content.iris.setShadersEnabled(on) }.onSuccess { shaders = on }
                        .onFailure { message = it.message ?: it.toString() }
                })
            }
        }

        when {
            checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.voxelium_checking_updates), style = MaterialTheme.typography.bodySmall)
            }
            updates.isNotEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pluralStringResource(R.plurals.voxelium_updates_available, updates.size, updates.size), modifier = Modifier.weight(1f))
                TextButton(enabled = updating.isEmpty(), onClick = { update(updates.values.toList()) }) {
                    Text(stringResource(R.string.voxelium_update_all))
                }
            }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (items.isEmpty()) {
            Text(
                stringResource(R.string.voxelium_content_empty, kindTitle(kind).lowercase(), kind.fileExtension),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items, key = { it.id }) { item ->
                ContentRow(
                    item = item,
                    warning = ContentManager.compatibilityWarning(item, content.loader),
                    update = updates[item.file.path],
                    updating = item.file.path in updating,
                    onToggle = { on ->
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { content.manager.setEnabled(item, on) } }
                                .onFailure { message = it.message ?: it.toString() }
                            reload++
                        }
                    },
                    onUpdate = { u -> update(listOf(u)) },
                    onRemove = { pendingRemove = item }
                )
                HorizontalDivider()
            }
        }
    }

    pendingRemove?.let { item ->
        SimpleAlertDialog(
            title = stringResource(R.string.voxelium_remove_title, item.name),
            text = stringResource(R.string.voxelium_remove_text, item.kind.folderName),
            confirmText = stringResource(R.string.voxelium_remove),
            onConfirm = {
                pendingRemove = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { content.manager.remove(item) } }
                        .onFailure { message = it.message ?: it.toString() }
                    reload++
                }
            },
            onDismiss = { pendingRemove = null }
        )
    }

    browsing?.let { source ->
        ContentBrowseDialog(
            content = content,
            kind = kind,
            source = source,
            onDismiss = {
                browsing = null
                reload++
            }
        )
    }
}

@Composable
private fun ContentRow(
    item: ContentItem,
    warning: String?,
    update: ModUpdate?,
    updating: Boolean,
    onToggle: (Boolean) -> Unit,
    onUpdate: (ModUpdate) -> Unit,
    onRemove: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(item.version, formatSize(item.size), item.fileName.takeIf { it != item.name }).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            (item.problem ?: warning)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            when {
                updating -> Text(stringResource(R.string.voxelium_updating), style = MaterialTheme.typography.bodySmall)
                update != null -> TextButton(onClick = { onUpdate(update) }) {
                    Text(stringResource(R.string.voxelium_update_to, update.latest.versionNumber))
                }
            }
        }
        DefaultSwitch(checked = item.isEnabled, onCheckedChange = onToggle)
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_delete_outlined), contentDescription = stringResource(R.string.voxelium_remove))
        }
    }
}

@Composable
internal fun kindTitle(kind: ContentKind): String = stringResource(
    when (kind) {
        ContentKind.MODS -> R.string.voxelium_kind_mods
        ContentKind.RESOURCE_PACKS -> R.string.voxelium_kind_resource_packs
        ContentKind.SHADER_PACKS -> R.string.voxelium_kind_shader_packs
    }
)
