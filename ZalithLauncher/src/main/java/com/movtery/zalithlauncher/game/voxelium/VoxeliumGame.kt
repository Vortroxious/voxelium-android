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
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.path.getGameHome
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.utils.logging.Logger
import io.github.voxelium.core.content.WorldManager
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.DownloadProgress
import io.github.voxelium.core.download.DownloadReport
import io.github.voxelium.core.launch.ForgeInstaller
import io.github.voxelium.core.launch.GameInstaller
import io.github.voxelium.core.launch.LoaderVersion
import io.github.voxelium.core.launch.ModLoader
import io.github.voxelium.core.launch.ModLoaderMeta
import io.github.voxelium.core.launch.ResolvedVersion
import io.github.voxelium.core.launch.VersionDeleter
import io.github.voxelium.core.launch.VersionResolver
import io.github.voxelium.core.manifest.VersionManifest
import io.github.voxelium.core.manifest.VersionType
import io.github.voxelium.core.paths.AppPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

private const val TAG = "VoxeliumGame"

/**
 * Voxelium: installs, repairs and deletes versions through VoxeliumCore, for the Versions screen.
 * Mirrors the iOS app's LauncherModel. Versions go into ZalithLauncher2's current game folder,
 * so they share libraries and assets with its versions and its launcher plays them.
 */
object VoxeliumGame {
    /** The core's view of ZalithLauncher2's current `.minecraft`; Voxelium's own files go next to it. */
    fun paths(): AppPaths = AppPaths(root = PathManager.DIR_GAME, gameDir = File(getGameHome()))

    private val downloader by lazy { DownloadManager() }
    private val meta by lazy { ModLoaderMeta() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun installer(paths: AppPaths = paths()) = GameInstaller(paths, downloader)

    sealed interface ManifestState {
        data object Loading : ManifestState
        data class Loaded(val manifest: VersionManifest) : ManifestState
        data class Failed(val message: String) : ManifestState
    }

    /** What a row shows below itself while (and after) installing. */
    sealed interface InstallState {
        data object Preparing : InstallState
        data class Downloading(val stage: GameInstaller.Stage, val done: Int, val total: Int, val failed: Int) : InstallState
        data class SettingUp(val loader: String) : InstallState
        data class Finished(val ok: Int, val failed: Int) : InstallState
        data class Failed(val message: String) : InstallState
    }

    /** A row in the Installed section. */
    data class InstalledVersion(
        val id: String,
        /** The Minecraft version it runs (itself for vanilla). */
        val base: String,
        val isModded: Boolean,
        /** "Fabric", "Quilt", … for modded versions. */
        val loader: String?,
        val releaseTime: Instant?
    ) {
        val title: String get() = if (isModded) "${loader ?: "Modded"} $base" else id
        val subtitle: String get() = if (isModded) id else "Vanilla"
    }

    private val _manifest = MutableStateFlow<ManifestState>(ManifestState.Loading)
    val manifest = _manifest.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledVersion>>(emptyList())
    val installed = _installed.asStateFlow()

    /** Keyed by the Minecraft version of the row (loader installs show on their Minecraft row) or the installed id. */
    private val _installState = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val installState = _installState.asStateFlow()

    private fun setState(key: String, state: InstallState?) = _installState.update {
        if (state == null) it - key else it + (key to state)
    }

    fun loadManifest() {
        _manifest.update { ManifestState.Loading }
        scope.launch {
            _manifest.update {
                runCatching { ManifestState.Loaded(installer().fetchManifest()) }.getOrElse { e ->
                    Logger.warning(TAG, "Couldn't load the version list", e)
                    ManifestState.Failed(e.message ?: e.toString())
                }
            }
        }
    }

    fun visibleVersions(manifest: VersionManifest, snapshots: Boolean): List<VersionManifest.Entry> =
        manifest.versions(if (snapshots) setOf(VersionType.RELEASE, VersionType.SNAPSHOT) else setOf(VersionType.RELEASE))

    /** Everything the core installed (it has `.voxelium-install-complete`), newest first. */
    fun refreshInstalled() {
        scope.launch {
            val paths = paths()
            val game = installer(paths)
            val resolver = VersionResolver(paths)
            val list = (paths.versionsDir.list() ?: emptyArray()).filter { game.isInstalled(it) }.mapNotNull { id ->
                val r = runCatching { resolver.resolve(id) }.getOrNull() ?: return@mapNotNull null
                InstalledVersion(id, r.baseVersionId, r.isModded, loaderName(r), r.info.releaseTime)
            }.sortedWith(compareByDescending<InstalledVersion> { it.releaseTime ?: Instant.EPOCH }.thenByDescending { it.id })
            _installed.update { list }
        }
    }

    suspend fun loaderBuilds(loader: ModLoader, minecraft: String): List<LoaderVersion> =
        withContext(Dispatchers.IO) { meta.versions(loader, minecraft) }

    fun isBusy(key: String) = TaskSystem.containsTask(taskId(key))

    private fun taskId(key: String) = "voxelium-install-$key"

    /** Installs Minecraft [entry] with [loader] ([build] is ignored for vanilla) as a background task. */
    fun install(entry: VersionManifest.Entry, loader: ModLoader, build: String) {
        val title = if (loader == ModLoader.VANILLA) entry.id else "${loader.displayName} ${entry.id}"
        runInstallTask(entry.id, title) { task, game, progress ->
            val result = if (loader == ModLoader.VANILLA) {
                entry.id to game.install(game.fetchVersion(entry), progress = progress)
            } else {
                val r = game.installLoader(loader, entry.id, build, meta, progress = progress)
                r.id to r.report
            }
            if (result.second.succeeded && loader.usesForgeInstaller) setUpForge(result.first, game.paths, task)
            result
        }
    }

    /** Verifies every file of an installed version, downloading what is missing or corrupt. */
    fun repair(id: String) {
        val title = _installed.value.firstOrNull { it.id == id }?.title ?: id
        runInstallTask(id, title) { task, game, progress ->
            val report = game.install(VersionResolver(game.paths).resolve(id).info, progress = progress)
            if (report.succeeded) setUpForge(id, game.paths, task)
            id to report
        }
    }

    private fun runInstallTask(
        key: String,
        title: String,
        body: suspend (Task, GameInstaller, (GameInstaller.Stage, DownloadProgress) -> Unit) -> Pair<String, DownloadReport>
    ) {
        if (isBusy(key)) return
        setState(key, InstallState.Preparing)
        val task = Task.runTask(
            id = taskId(key),
            dispatcher = Dispatchers.IO,
            task = { task ->
                task.updateMessage(androidText(R.string.voxelium_installing, title))
                val game = installer()
                val (id, report) = body(task, game) { stage, p ->
                    setState(key, InstallState.Downloading(stage, p.completed, p.total, p.failed))
                    task.updateProgress(if (p.total > 0) p.completed.toFloat() / p.total else -1f)
                }
                report.failures.entries.take(20).forEach { (item, e) -> Logger.warning(TAG, "$id: ${item.destination.name}: ${e.message}") }
                Logger.info(TAG, "$id: ${report.outcomes.size} files verified, ${report.failures.size} failed")
                setState(key, InstallState.Finished(report.outcomes.size, report.failures.size))
            },
            onError = { e ->
                Logger.error(TAG, "Installing $title failed", e)
                setState(key, InstallState.Failed(e.message ?: e.toString()))
            },
            onFinally = {
                refreshInstalled()
                VersionsManager.refresh(TAG)
            }
        )
        TaskSystem.submitTask(task)
    }

    /** Runs the Forge/NeoForge processor plan of [id] if it has one that hasn't finished. */
    private suspend fun setUpForge(id: String, paths: AppPaths, task: Task?) {
        val plan = ForgeInstaller.processorPlan(id, paths) ?: return
        if (ForgeInstaller.processorsDone(plan)) return
        val name = loaderName(VersionResolver(paths).resolve(id)) ?: "the mod loader"
        setState(VersionResolver(paths).resolve(id).baseVersionId, InstallState.SettingUp(name))
        setState(id, InstallState.SettingUp(name))
        ForgeProcessorRunner.run(plan, paths, downloader, name, task)
    }

    /** True when [id] still needs its Forge/NeoForge set-up before it can start. */
    fun needsSetUp(id: String): Boolean {
        val plan = ForgeInstaller.processorPlan(id, paths()) ?: return false
        return !ForgeInstaller.processorsDone(plan)
    }

    /** Sets up Forge/NeoForge (if needed), then hands the version to ZalithLauncher2's launcher. */
    fun play(id: String, launch: (Version) -> Unit, onError: (String) -> Unit) {
        if (needsSetUp(id)) {
            repair(id)
            onError("Setting up the mod loader first. Press Play again when it is done.")
            return
        }
        val version = VersionsManager.loadVersion(getGameHome(), id)
        if (version == null || !version.isValid()) {
            onError("$id can't be read by the launcher.")
            return
        }
        launch(version)
    }

    sealed interface DeleteCheck {
        data class NeededBy(val others: List<String>) : DeleteCheck
        /** [worlds] are lost with it (its game folder goes with the version). */
        data class Confirm(val worlds: Int, val ownFolder: Boolean) : DeleteCheck
    }

    fun deleteCheck(id: String): DeleteCheck {
        val paths = paths()
        val others = VersionDeleter(paths).dependents(id)
        if (others.isNotEmpty()) return DeleteCheck.NeededBy(others)
        // ZalithLauncher2 gives each version its own game folder by default (vanilla too), so
        // count the worlds where its launcher actually keeps them.
        val gameDir = VersionsManager.loadVersion(getGameHome(), id)?.getGameDir()
        val versionDir = paths.versionDir(id)
        val ownFolder = gameDir != null && gameDir.absoluteFile.normalize().startsWith(versionDir)
        val worlds = if (ownFolder) runCatching { WorldManager(gameDir!!).list().size }.getOrDefault(0) else 0
        return DeleteCheck.Confirm(worlds, ownFolder)
    }

    fun delete(id: String, onError: (String) -> Unit) {
        scope.launch {
            runCatching { VersionDeleter(paths()).delete(id) }
                .onSuccess { Logger.info(TAG, "Deleted $id") }
                .onFailure { e ->
                    Logger.error(TAG, "Couldn't delete $id", e)
                    onError(e.message ?: e.toString())
                }
            setState(id, null)
            refreshInstalled()
            VersionsManager.refresh(TAG)
        }
    }

    fun loaderName(r: ResolvedVersion): String? {
        if (!r.isModded) return null
        val main = r.info.mainClass
        if (main.startsWith("net.fabricmc")) return "Fabric"
        if (main.startsWith("org.quiltmc")) return "Quilt"
        // Forge and NeoForge share main classes; their installers name versions
        // "neoforge-21.1.264" and "1.20.1-forge-47.4.10".
        if (r.chain.any { it.startsWith("neoforge-") }) return "NeoForge"
        if (r.chain.any { it.contains("-forge-") }) return "Forge"
        return "Modded"
    }
}
