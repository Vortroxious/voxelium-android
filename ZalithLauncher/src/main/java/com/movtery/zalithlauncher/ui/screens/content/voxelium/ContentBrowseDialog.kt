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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.voxelium.VoxeliumContent
import io.github.voxelium.core.content.ContentKind
import io.github.voxelium.core.content.CurseForgeApi
import io.github.voxelium.core.content.CurseForgeInstallPlan
import io.github.voxelium.core.content.ModrinthApi
import io.github.voxelium.core.content.ModrinthInstallPlan
import io.github.voxelium.core.download.DownloadProgress
import kotlinx.coroutines.launch

/** One search result, from either site. */
private data class Hit(
    val id: String,
    val title: String,
    val author: String?,
    val downloads: Long,
    val description: String,
    val modrinth: ModrinthApi.SearchHit? = null,
    val curseForge: CurseForgeApi.Mod? = null
)

/**
 * Voxelium: search Modrinth or CurseForge for [kind] for the version's Minecraft version and loader,
 * then install a version with its required dependencies. Mirrors the iOS app's ModrinthBrowseView
 * and CurseForgeBrowseView.
 */
@Composable
fun ContentBrowseDialog(content: VoxeliumContent, kind: ContentKind, source: ContentSource, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchId by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Hit?>(null) }
    var installing by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun search(reset: Boolean) {
        scope.launch {
            loading = true
            error = null
            val offset = if (reset) 0 else hits.size
            runCatching {
                when (source) {
                    ContentSource.MODRINTH -> content.searchModrinth(query, kind, offset).let { page ->
                        page.totalHits to page.hits.map { Hit(it.projectId, it.title, it.author, it.downloads, it.description, modrinth = it) }
                    }
                    ContentSource.CURSEFORGE -> content.searchCurseForge(query, kind, offset).let { page ->
                        page.totalCount to page.mods.map {
                            Hit(it.id.toString(), it.name, it.authors?.firstOrNull()?.name, it.downloadCount.toLong(), it.summary, curseForge = it)
                        }
                    }
                }
            }.onSuccess { (count, page) ->
                hits = if (reset) page else hits + page
                total = count
            }.onFailure { error = it.message ?: it.toString() }
            loading = false
        }
    }

    LaunchedEffect(searchId) { search(reset = true) }
    LaunchedEffect(Unit) {
        installed = when (source) {
            ContentSource.MODRINTH -> content.installedModrinth(kind).keys
            ContentSource.CURSEFORGE -> runCatching { content.installedCurseForge() }.getOrDefault(emptySet()).map { it.toString() }.toSet()
        }
    }

    val siteName = stringResource(if (source == ContentSource.MODRINTH) R.string.voxelium_modrinth else R.string.voxelium_curseforge)
    VoxeliumSheet(
        title = selected?.title ?: "$siteName · ${kindTitle(kind)}",
        onClose = onDismiss,
        closeEnabled = !installing,
        actions = {
            if (selected != null && !installing) TextButton(onClick = { selected = null }) { Text(stringResource(R.string.voxelium_back)) }
        }
    ) {
        val hit = selected
        if (hit != null) {
            val onInstalled = { installed = installed + hit.id }
            if (hit.modrinth != null) {
                ModrinthDetail(content, kind, hit.modrinth, hit.id in installed, { installing = it }, onInstalled)
            } else if (hit.curseForge != null) {
                CurseForgeDetail(content, kind, hit.curseForge, hit.id in installed, { installing = it }, onInstalled)
            }
            return@VoxeliumSheet
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.voxelium_search_site, siteName, kindTitle(kind).lowercase())) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchId++ })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { searchId++ }) { Text(stringResource(R.string.voxelium_search)) }
        }
        Text(
            stringResource(R.string.voxelium_browse_for, content.minecraft) + (content.loader?.let { " · ${it.replaceFirstChar(Char::uppercase)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (kind == ContentKind.SHADER_PACKS) {
            Text(
                stringResource(if (content.loader == "forge") R.string.voxelium_shaders_need_oculus else R.string.voxelium_shaders_need_iris),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(hits, key = { it.id }) { h ->
                Column(modifier = Modifier.fillMaxWidth().clickable { selected = h }.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                        if (h.id in installed) {
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.voxelium_installed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
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
                    else if (hits.size < total) TextButton(onClick = { search(reset = false) }) { Text(stringResource(R.string.voxelium_load_more)) }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ModrinthDetail(
    content: VoxeliumContent,
    kind: ContentKind,
    hit: ModrinthApi.SearchHit,
    alreadyInstalled: Boolean,
    setInstalling: (Boolean) -> Unit,
    onInstalled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<ModrinthApi.Version>?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<ModrinthInstallPlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hit.projectId) {
        runCatching { content.modrinthVersions(hit.projectId, kind) }
            .onSuccess { versions = it; chosen = ModrinthApi.preferred(it)?.id }
            .onFailure { error = it.message ?: it.toString() }
    }
    val version = versions?.firstOrNull { it.id == chosen }
    LaunchedEffect(chosen) {
        plan = null
        val v = version ?: return@LaunchedEffect
        runCatching { content.planModrinth(v, hit.title, kind) }.onSuccess { plan = it }.onFailure { error = it.message ?: it.toString() }
    }

    Text(hit.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (alreadyInstalled) Text(stringResource(R.string.voxelium_reinstall_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    DetailColumns(left = {
        val list = versions
        when {
            list == null && error == null -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            list != null && list.isEmpty() -> Text(noVersionText(hit.title, content))
            list != null -> {
                Text(stringResource(R.string.voxelium_version_for, content.minecraft), style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(list, key = { it.id }) { v ->
                        ChoiceLine(chosen == v.id, v.versionNumber + if (v.versionType != "release") " (${v.versionType})" else "", v.name) { chosen = v.id }
                    }
                }
            }
        }
    }) {
    val p = plan
    if (p != null) {
        PlanSummary(
            items = p.items.map { it.title + ": " + it.version.versionNumber },
            alreadyInstalled = p.alreadyInstalled.size,
            unavailable = p.unavailable,
            conflicts = p.conflicts
        )
    } else if (version != null && error == null) {
        Text(stringResource(R.string.voxelium_checking_dependencies), style = MaterialTheme.typography.bodySmall)
    }
    InstallFooter(progress, done) {
        Button(enabled = p != null && progress == null, onClick = {
            val ready = p ?: return@Button
            scope.launch {
                setInstalling(true)
                progress = DownloadProgress(0, ready.items.size, 0)
                runCatching { content.installModrinth(ready, kind) { progress = it } }
                    .onSuccess { r -> done = if (r.succeeded) "ok" else r.failures.size.toString(); if (r.succeeded) onInstalled() }
                    .onFailure { error = it.message ?: it.toString() }
                progress = null
                setInstalling(false)
            }
        }) { Text(stringResource(R.string.voxelium_install_count, p?.items?.size ?: 1)) }
    }
    }
}

@Composable
private fun ColumnScope.CurseForgeDetail(
    content: VoxeliumContent,
    kind: ContentKind,
    mod: CurseForgeApi.Mod,
    alreadyInstalled: Boolean,
    setInstalling: (Boolean) -> Unit,
    onInstalled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var files by remember { mutableStateOf<List<CurseForgeApi.File>?>(null) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var plan by remember { mutableStateOf<CurseForgeInstallPlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mod.id) {
        runCatching { content.curseForgeFiles(mod.id, kind) }
            .onSuccess { files = it; chosen = CurseForgeApi.preferred(it)?.id }
            .onFailure { error = it.message ?: it.toString() }
    }
    val file = files?.firstOrNull { it.id == chosen }
    LaunchedEffect(chosen) {
        plan = null
        val f = file ?: return@LaunchedEffect
        runCatching { content.planCurseForge(f, mod) }.onSuccess { plan = it }.onFailure { error = it.message ?: it.toString() }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(mod.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        mod.websiteUrl?.let { url -> TextButton(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.voxelium_open_curseforge)) } }
    }
    if (alreadyInstalled) Text(stringResource(R.string.voxelium_reinstall_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    DetailColumns(left = {
        val list = files
        when {
            list == null && error == null -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            list != null && list.isEmpty() -> Text(noVersionText(mod.name, content))
            list != null -> {
                Text(stringResource(R.string.voxelium_file_for, content.minecraft), style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(list, key = { it.id }) { f ->
                        val type = when (f.releaseType) { 2 -> " (beta)"; 3 -> " (alpha)"; else -> "" }
                        ChoiceLine(chosen == f.id, f.displayName + type, f.fileName) { chosen = f.id }
                    }
                }
            }
        }
    }) {
    val p = plan
    if (p != null) {
        PlanSummary(
            items = p.downloadable.map { it.mod.name + ": " + it.file.displayName },
            alreadyInstalled = p.alreadyInstalled.size,
            unavailable = p.unavailable,
            conflicts = p.conflicts
        )
        if (p.blocked.isNotEmpty()) {
            Text(stringResource(R.string.voxelium_website_only), style = MaterialTheme.typography.labelLarge)
            for (item in p.blocked) {
                val url = item.mod.websiteUrl?.let { "$it/files/${item.file.id}" }
                if (url != null) TextButton(onClick = { uriHandler.openUri(url) }) { Text(item.mod.name + ": " + item.file.displayName) }
                else Text(item.mod.name + ": " + item.file.displayName)
            }
            Text(stringResource(R.string.voxelium_website_only_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (file != null && error == null) {
        Text(stringResource(R.string.voxelium_checking_dependencies), style = MaterialTheme.typography.bodySmall)
    }
    InstallFooter(progress, done) {
        Button(enabled = p != null && p.downloadable.isNotEmpty() && progress == null, onClick = {
            val ready = p ?: return@Button
            scope.launch {
                setInstalling(true)
                progress = DownloadProgress(0, ready.downloadable.size, 0)
                runCatching { content.installCurseForge(ready, kind) { progress = it } }
                    .onSuccess { r -> done = if (r.succeeded) "ok" else r.failures.size.toString(); if (r.succeeded) onInstalled() }
                    .onFailure { error = it.message ?: it.toString() }
                progress = null
                setInstalling(false)
            }
        }) { Text(stringResource(R.string.voxelium_install_count, p?.downloadable?.size ?: 1)) }
    }
    }
}

/** Versions on the left; what will be installed and the Install button on the right (fits a phone held sideways). */
@Composable
private fun ColumnScope.DetailColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp), content = left)
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = right
        )
    }
}

@Composable
private fun ChoiceLine(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun PlanSummary(items: List<String>, alreadyInstalled: Int, unavailable: List<String>, conflicts: List<String>) {
    if (items.isNotEmpty()) {
        Text(stringResource(R.string.voxelium_will_install), style = MaterialTheme.typography.labelLarge)
        Text(items.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
    }
    if (alreadyInstalled > 0) Text(pluralStringResource(R.plurals.voxelium_deps_already_installed, alreadyInstalled, alreadyInstalled), style = MaterialTheme.typography.bodySmall)
    unavailable.forEach { Text(stringResource(R.string.voxelium_dep_unavailable, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    conflicts.forEach { Text(stringResource(R.string.voxelium_dep_conflict, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

/** The install button, its progress while downloading, then the result ("ok" or the number of failed files). */
@Composable
private fun InstallFooter(progress: DownloadProgress?, done: String?, button: @Composable () -> Unit) {
    if (progress != null) {
        LinearProgressIndicator(
            progress = { if (progress.total > 0) progress.completed.toFloat() / progress.total else 0f },
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.voxelium_modpack_count, progress.completed, progress.total), style = MaterialTheme.typography.bodySmall)
    }
    when (done) {
        null -> {}
        "ok" -> Text(stringResource(R.string.voxelium_content_installed), color = MaterialTheme.colorScheme.primary)
        else -> Text(stringResource(R.string.voxelium_files_failed, done.toIntOrNull() ?: 0), color = MaterialTheme.colorScheme.error)
    }
    button()
}

@Composable
private fun noVersionText(name: String, content: VoxeliumContent): String =
    stringResource(R.string.voxelium_no_version_for, name, content.minecraft) +
        (content.loader?.let { " " + stringResource(R.string.voxelium_with_loader, it.replaceFirstChar(Char::uppercase)) } ?: "")
