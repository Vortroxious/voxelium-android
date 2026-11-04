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

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.utils.logging.Logger
import io.github.voxelium.core.launch.ForgeInstaller
import io.github.voxelium.core.launch.GameInstaller
import io.github.voxelium.core.launch.ResolvedVersion
import io.github.voxelium.core.launch.VersionResolver
import io.github.voxelium.core.manifest.MojangJson
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.paths.AppPaths
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException

private const val TAG = "CoreLaunch"

/**
 * Voxelium: how ZalithLauncher2's launcher plays a version VoxeliumCore installed (it has
 * `.voxelium-install-complete`). The core resolves the whole `inheritsFrom` chain (a modpack on
 * its loader on Minecraft), verifies the files and picks the client jar; ZalithLauncher2 still
 * builds the Java arguments from the merged version (decided 2026-11-03), because it carries the
 * Android pieces: its LWJGL, natives, the AWT shim, offline skins and Quick Play.
 */
object CoreLaunch {
    fun isCoreInstall(versionDir: File): Boolean = File(versionDir, GameInstaller.MARKER).isFile

    fun isCoreInstall(version: Version): Boolean = isCoreInstall(version.getVersionPath())

    private fun paths(gameHome: File) = AppPaths(root = PathManager.DIR_GAME, gameDir = gameHome)

    private fun paths(version: Version) = paths(File(version.getGameHome()))

    private fun resolve(paths: AppPaths, id: String): ResolvedVersion = VersionResolver(paths).resolve(id)

    /**
     * The merged version as Mojang-format JSON, with no `inheritsFrom` left. `clientVersion` names
     * the Minecraft version for ZalithLauncher2's version detection (a key it already reads).
     */
    fun mergedJson(gameHome: File, id: String): String {
        val resolved = resolve(paths(gameHome), id)
        val json = MojangJson.encodeToJsonElement(VersionInfo.serializer(), resolved.info).jsonObject
        return JsonObject(json + ("clientVersion" to JsonPrimitive(resolved.baseVersionId))).toString()
    }

    fun mergedJson(version: Version): String = mergedJson(File(version.getGameHome()), version.getVersionName())

    /** The client jar the merged version runs (the vanilla version's, at the root of the chain). */
    fun clientJar(version: Version): File {
        val paths = paths(version)
        return paths.clientJar(resolve(paths, version.getVersionName()).info.clientJarId)
    }

    /**
     * Before launching: checks every file of the merged version (downloading what is missing or
     * corrupt; already valid files need no network) and finishes a Forge/NeoForge set-up that
     * didn't complete. Replaces ZalithLauncher2's verify step, which follows only one parent.
     */
    suspend fun verify(version: Version, task: Task) {
        val paths = paths(version)
        val id = version.getVersionName()
        val game = VoxeliumGame.installer(paths)
        task.updateMessage(androidText(R.string.minecraft_download_stat_verify_task))
        val report = game.install(resolve(paths, id).info) { _, p ->
            task.updateProgress(if (p.total > 0) p.completed.toFloat() / p.total else -1f)
        }
        if (!report.succeeded) {
            report.failures.entries.take(20).forEach { (item, e) -> Logger.warning(TAG, "$id: ${item.destination.name}: ${e.message}") }
            throw IOException("${report.failures.size} files of $id could not be downloaded. Check your internet connection and try again.")
        }
        Logger.info(TAG, "$id: ${report.outcomes.size} files verified")
        finishForgeSetUp(paths, id, task)
    }

    /**
     * Runs the Forge/NeoForge processor plan of every version in [id]'s chain that hasn't finished
     * (a modpack's plan lives with its loader version, further down the chain).
     * @param onSetUp called with the loader's name before a plan runs
     */
    suspend fun finishForgeSetUp(paths: AppPaths, id: String, task: Task?, onSetUp: (String) -> Unit = {}) {
        for (link in resolve(paths, id).chain) {
            val plan = ForgeInstaller.processorPlan(link, paths) ?: continue
            if (ForgeInstaller.processorsDone(plan)) continue
            val name = VoxeliumGame.loaderName(resolve(paths, link)) ?: "the mod loader"
            onSetUp(name)
            ForgeProcessorRunner.run(plan, paths, VoxeliumGame.downloader, name, task)
        }
    }
}
