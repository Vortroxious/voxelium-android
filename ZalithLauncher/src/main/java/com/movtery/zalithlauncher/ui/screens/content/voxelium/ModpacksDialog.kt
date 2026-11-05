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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame.ModpackState
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import io.github.voxelium.core.content.ModpackInstaller
import io.github.voxelium.core.content.ModrinthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PAGE = 20

/**
 * Voxelium: Modrinth modpacks (Fabric, Quilt, Forge, NeoForge) and .mrpack import, each installed
 * as its own version through VoxeliumCore. Mirrors the iOS app's ModpackBrowseView.
 */
@Composable
fun ModpacksDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by VoxeliumGame.modpack.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ModrinthApi.SearchHit>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ModrinthApi.SearchHit?>(null) }
    var searchId by remember { mutableIntStateOf(0) }

    fun search(reset: Boolean) {
        scope.launch {
            loading = true
            error = null
            runCatching { VoxeliumGame.searchModpacks(query, if (reset) 0 else hits.size) }
                .onSuccess { page ->
                    hits = if (reset) page.hits else hits + page.hits
                    total = page.totalHits
                }
                .onFailure { error = it.message ?: it.toString() }
            loading = false
        }
    }

    LaunchedEffect(searchId) { search(reset = true) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = displayName(context, uri) ?: "modpack.mrpack"
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = File(File(PathManager.DIR_CACHE, "modpacks"), name.replace('/', '_'))
                    dest.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    dest
                }.getOrNull()
            }
            if (copy == null) error = context.getString(R.string.voxelium_modpack_import_failed)
            else VoxeliumGame.installModpackFile(copy, name.removeSuffix(".mrpack"))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = cardColor(false),
            contentColor = onCardColor(),
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selected?.title ?: stringResource(R.string.voxelium_modpacks),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (selected != null && state !is ModpackState.Working && state !is ModpackState.SettingUp) {
                        TextButton(onClick = { selected = null; VoxeliumGame.resetModpack() }) {
                            Text(stringResource(R.string.voxelium_back))
                        }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.voxelium_close)) }
                }

                if (state != ModpackState.Idle) {
                    ModpackStatus(state, onDone = { VoxeliumGame.resetModpack(); selected = null })
                    return@Column
                }

                val hit = selected
                if (hit != null) {
                    ModpackVersions(hit)
                    return@Column
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.voxelium_search_modpacks)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchId++ })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { searchId++ }) { Text(stringResource(R.string.voxelium_search)) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.voxelium_import_mrpack))
                    }
                }
                Text(
                    stringResource(R.string.voxelium_modpack_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(hits, key = { it.projectId }) { h ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = h }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(h.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(h.author, stringResource(R.string.voxelium_downloads, h.downloads)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(h.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider()
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                            if (loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            else if (hits.size < total) TextButton(onClick = { search(reset = false) }) {
                                Text(stringResource(R.string.voxelium_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModpackVersions(hit: ModrinthApi.SearchHit) {
    var versions by remember(hit.projectId) { mutableStateOf<List<ModrinthApi.Version>?>(null) }
    var error by remember(hit.projectId) { mutableStateOf<String?>(null) }
    var chosen by remember(hit.projectId) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(hit.projectId, attempt) {
        error = null
        runCatching { VoxeliumGame.modpackVersions(hit.projectId) }
            .onSuccess { list ->
                versions = list
                chosen = ModrinthApi.preferred(list)?.id
            }
            .onFailure { error = it.message ?: it.toString() }
    }

    Text(hit.description, style = MaterialTheme.typography.bodyMedium)
    when {
        error != null -> {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.voxelium_try_again)) }
        }
        versions == null -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        versions!!.isEmpty() -> Text(stringResource(R.string.voxelium_modpack_no_versions))
        else -> {
            Text(stringResource(R.string.voxelium_modpack_version), style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                items(versions!!, key = { it.id }) { v ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { chosen = v.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chosen == v.id, onClick = { chosen = v.id })
                        Column {
                            Text(v.versionNumber)
                            Text(
                                (v.loaders + v.gameVersions).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            val version = versions!!.firstOrNull { it.id == chosen }
            Button(enabled = version?.primaryFile != null, onClick = { version?.let { VoxeliumGame.installModpack(it) } }) {
                Text(stringResource(R.string.voxelium_install_modpack, hit.title))
            }
        }
    }
}

@Composable
private fun ModpackStatus(state: ModpackState, onDone: () -> Unit) {
    when (state) {
        is ModpackState.Idle -> {}
        is ModpackState.Working -> {
            val stage = stringResource(
                when (state.stage) {
                    null -> R.string.voxelium_modpack_downloading
                    ModpackInstaller.Stage.MINECRAFT -> R.string.voxelium_modpack_stage_minecraft
                    ModpackInstaller.Stage.LOADER -> R.string.voxelium_modpack_stage_loader
                    ModpackInstaller.Stage.MODPACK_FILES -> R.string.voxelium_modpack_stage_mods
                    ModpackInstaller.Stage.OVERRIDES -> R.string.voxelium_modpack_stage_overrides
                }
            )
            Text(stage)
            if (state.stage == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(
                progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.stage != null) {
                Text(
                    stringResource(R.string.voxelium_modpack_count, state.done, state.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.voxelium_modpack_background),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is ModpackState.SettingUp -> {
            Text(stringResource(R.string.voxelium_setting_up, state.loader))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is ModpackState.Finished -> {
            Text(stringResource(R.string.voxelium_modpack_installed, state.id))
            Button(onClick = onDone) { Text(stringResource(R.string.voxelium_ok)) }
        }
        is ModpackState.Failed -> {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onDone) { Text(stringResource(R.string.voxelium_ok)) }
        }
    }
}
