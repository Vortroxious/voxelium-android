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

package com.movtery.zalithlauncher.game.voxelium

import com.movtery.zalithlauncher.game.path.getGameHome
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.file.zipDirectory
import com.movtery.zalithlauncher.utils.logging.Logger
import io.github.voxelium.core.content.ContentKind
import io.github.voxelium.core.content.ContentManager
import io.github.voxelium.core.content.CurseForgeApi
import io.github.voxelium.core.content.CurseForgeIndex
import io.github.voxelium.core.content.CurseForgeInstallPlan
import io.github.voxelium.core.content.CurseForgeInstaller
import io.github.voxelium.core.content.IrisSettings
import io.github.voxelium.core.content.ModUpdate
import io.github.voxelium.core.content.ModrinthApi
import io.github.voxelium.core.content.ModrinthInstallPlan
import io.github.voxelium.core.content.ModrinthInstaller
import io.github.voxelium.core.content.WorldItem
import io.github.voxelium.core.content.WorldManager
import io.github.voxelium.core.download.DownloadProgress
import io.github.voxelium.core.download.DownloadReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "VoxeliumContent"

/**
 * Voxelium: one installed version's mods, resource packs, shader packs and worlds, through
 * VoxeliumCore (mirrors the iOS app's ContentLibraryView, ModrinthBrowseView, CurseForgeBrowseView
 * and WorldsView). The folder is the one ZalithLauncher2's launcher runs the version in, so what is
 * shown here is what the game loads.
 */
class VoxeliumContent(val version: VoxeliumGame.InstalledVersion) {
    /** ZalithLauncher2's game folder for the version (its own folder with version isolation, the default). */
    val gameDirectory: File =
        VersionsManager.loadVersion(getGameHome(), version.id)?.getGameDir()
            ?: VoxeliumGame.paths().instanceDir(version.id)

    val manager = ContentManager(gameDirectory)
    val worlds = WorldManager(gameDirectory)
    val iris = IrisSettings(gameDirectory)

    /** The core's loader id ("fabric", "quilt", "forge", "neoforge"), or null for vanilla. */
    val loader: String? = version.loader?.lowercase()?.takeIf { it in setOf("fabric", "quilt", "forge", "neoforge") }

    /** The Minecraft version, for Modrinth and CurseForge searches. */
    val minecraft: String get() = version.base

    /** Mods need a loader; vanilla versions have resource and shader packs only (shaders still need Iris/Oculus). */
    val kinds: List<ContentKind> =
        if (loader != null) ContentKind.entries.toList() else listOf(ContentKind.RESOURCE_PACKS)

    suspend fun list(kind: ContentKind) = withContext(Dispatchers.IO) { manager.list(kind) }

    /**
     * Ids of the mods in the folder (from their metadata, e.g. "fabric-api"). Modrinth and CurseForge
     * slugs usually match them, which keeps a dependency from being installed twice in two versions
     * (Fabric refuses to start with duplicate mods).
     */
    private fun installedModIds(): Set<String> =
        manager.list(ContentKind.MODS).filter { it.isEnabled }.mapNotNull { it.mod?.id?.lowercase() }.toSet()

    // Modrinth

    private val modrinth = ModrinthInstaller(modrinthApi, VoxeliumGame.downloader)

    suspend fun searchModrinth(query: String, kind: ContentKind, offset: Int) =
        withContext(Dispatchers.IO) { modrinthApi.search(query, kind, minecraft, loader, offset = offset) }

    suspend fun modrinthVersions(projectId: String, kind: ContentKind) =
        withContext(Dispatchers.IO) { modrinthApi.versions(projectId, ModrinthApi.loaders(kind, loader), minecraft) }

    /** Modrinth project ids installed for [kind] (matched by SHA-1), with their files. */
    suspend fun installedModrinth(kind: ContentKind) =
        withContext(Dispatchers.IO) { runCatching { modrinth.installedProjects(manager, kind) }.getOrDefault(emptyMap()) }

    suspend fun planModrinth(version: ModrinthApi.Version, title: String, kind: ContentKind): ModrinthInstallPlan =
        withContext(Dispatchers.IO) {
            val installed = installedModrinth(ContentKind.MODS).keys + installedModrinth(kind).keys
            modrinth.plan(version, title, kind, minecraft, loader, installed).also { plan ->
                // Dependencies installed from elsewhere (CurseForge, a file) aren't found by SHA-1.
                val ids = installedModIds()
                plan.items.filter { it.isDependency }.forEach { item ->
                    val slug = runCatching { modrinthApi.project(item.projectId).slug }.getOrNull()
                    if (slug != null && slug in ids) {
                        plan.items.remove(item)
                        plan.alreadyInstalled.add(item.projectId)
                    }
                }
            }
        }

    suspend fun installModrinth(plan: ModrinthInstallPlan, kind: ContentKind, progress: (DownloadProgress) -> Unit): DownloadReport =
        withContext(Dispatchers.IO) {
            modrinth.install(plan, kind, manager, installedModrinth(kind), progress).also { log("Modrinth", it) }
        }

    suspend fun checkUpdates(kind: ContentKind): List<ModUpdate> =
        withContext(Dispatchers.IO) { modrinth.checkUpdates(manager, kind, minecraft, loader) }

    /** Downloads the newer files, then removes the old ones. */
    suspend fun installUpdates(updates: List<ModUpdate>, kind: ContentKind): DownloadReport =
        withContext(Dispatchers.IO) {
            val plan = ModrinthInstallPlan()
            updates.forEach { plan.items.add(ModrinthInstallPlan.Item(it.projectId, it.name, it.latest, false)) }
            modrinth.install(plan, kind, manager, updates.associate { it.projectId to listOf(it.file) }).also { log("Modrinth updates", it) }
        }

    // CurseForge (files whose authors don't allow other apps to download them are linked, never fetched)

    val curseForgeAvailable: Boolean get() = BuildKeys.CURSEFORGE_API.isNotBlank()

    private val curseForgeApi by lazy { CurseForgeApi(BuildKeys.CURSEFORGE_API) }
    private val curseForge by lazy { CurseForgeInstaller(curseForgeApi, VoxeliumGame.downloader) }

    suspend fun searchCurseForge(query: String, kind: ContentKind, index: Int) =
        withContext(Dispatchers.IO) { curseForgeApi.search(query, kind, minecraft, loader, index = index) }

    suspend fun curseForgeFiles(modId: Int, kind: ContentKind) =
        withContext(Dispatchers.IO) { curseForgeApi.files(modId, kind, minecraft, loader) }

    /** CurseForge mod ids Voxelium installed into this folder. */
    suspend fun installedCurseForge(): Set<Int> =
        withContext(Dispatchers.IO) { CurseForgeIndex.load(manager).installed(manager).keys }

    suspend fun planCurseForge(file: CurseForgeApi.File, mod: CurseForgeApi.Mod): CurseForgeInstallPlan =
        withContext(Dispatchers.IO) {
            curseForge.plan(file, mod, minecraft, loader, installedCurseForge()).also { plan ->
                // Dependencies installed from Modrinth or a file aren't in the CurseForge index.
                val ids = installedModIds()
                plan.items.filter { it.isDependency && it.mod.slug in ids }.forEach { item ->
                    plan.items.remove(item)
                    plan.alreadyInstalled.add(item.mod.id)
                }
            }
        }

    suspend fun installCurseForge(plan: CurseForgeInstallPlan, kind: ContentKind, progress: (DownloadProgress) -> Unit): DownloadReport =
        withContext(Dispatchers.IO) { curseForge.install(plan, kind, manager, progress).also { log("CurseForge", it) } }

    // Worlds

    suspend fun listWorlds(): List<WorldItem> = withContext(Dispatchers.IO) { worlds.list() }

    suspend fun importWorldZip(zip: File): WorldItem = withContext(Dispatchers.IO) { worlds.importZip(zip) }

    suspend fun deleteWorld(world: WorldItem) = withContext(Dispatchers.IO) { worlds.delete(world) }

    /** The world zipped into the app's cache, to share; the previous export is replaced. */
    suspend fun exportWorld(world: WorldItem): File = withContext(Dispatchers.IO) {
        val dir = File(PathManager.DIR_CACHE, "world-export")
        dir.deleteRecursively()
        dir.mkdirs()
        val zip = File(dir, world.name.replace('/', '_') + ".zip")
        zipDirectory(world.folder, zip)
        if (!zip.isFile) throw IOException("Couldn't zip ${world.name}")
        zip
    }

    private fun log(what: String, report: DownloadReport) {
        report.failures.entries.take(20).forEach { (item, e) -> Logger.warning(TAG, "$what ${version.id}: ${item.destination.name}: ${e.message}") }
        Logger.info(TAG, "$what ${version.id}: ${report.outcomes.size} files, ${report.failures.size} failed")
    }

    companion object {
        private val modrinthApi by lazy { ModrinthApi() }
    }
}
