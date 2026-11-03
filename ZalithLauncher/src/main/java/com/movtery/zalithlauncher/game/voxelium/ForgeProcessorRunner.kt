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
import com.movtery.zalithlauncher.components.jre.Jre
import com.movtery.zalithlauncher.context.GlobalContext
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.game.download.jvm_server.runJvmRetryRuntimes
import com.movtery.zalithlauncher.game.download.jvm_server.stopAllNonMainProcesses
import com.movtery.zalithlauncher.notification.NoticeProgress
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.utils.logging.Logger
import io.github.voxelium.core.download.DownloadItem
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.FileHash
import io.github.voxelium.core.launch.ProcessorPlan
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.paths.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.jar.Attributes
import java.util.jar.JarFile

private const val TAG = "ForgeProcessorRunner"
private const val LOG_ID = "VOXELIUM_FORGE_PROCESSORS"

/**
 * Voxelium: runs the Forge/NeoForge processor plan VoxeliumCore's ForgeInstaller writes
 * (`voxelium-processors.txt`), at install time, each processor in its own Java process through
 * ZalithLauncher2's JVM service, as its own Forge installer does (Install.ForgeLike.kt). On iOS the
 * same plan runs in the game's JVM on first launch.
 *
 * `DOWNLOAD_MOJMAPS` is not run: like ZalithLauncher2, the launcher downloads Mojang's
 * `client_mappings` itself (verified by SHA-1). Every output is checked against its SHA-1, and
 * `<plan>.done` is written only when all processors succeeded.
 */
object ForgeProcessorRunner {
    suspend fun run(
        plan: File,
        paths: AppPaths,
        downloader: DownloadManager,
        loaderName: String,
        task: Task? = null
    ) = withContext(Dispatchers.IO) {
        val processors = ProcessorPlan.read(plan)
        Logger.info(TAG, "Setting up $loaderName: ${processors.size} steps")
        stopAllNonMainProcesses(GlobalContext)
        processors.forEachIndexed { index, p ->
            val step = index + 1
            if (p.outputs.isNotEmpty() && ProcessorPlan.outputsValid(p)) {
                Logger.info(TAG, "Step $step/${processors.size} ${p.name}: already done")
                return@forEachIndexed
            }
            task?.updateProgress(step.toFloat() / processors.size)
            task?.updateMessage(androidText(R.string.download_game_install_base_installing, "$loaderName ($step/${processors.size})"))
            if (p.task == "DOWNLOAD_MOJMAPS") {
                // Verified against Mojang's own SHA-1. Newer Forge (--sanitize) expects a converted
                // .tsrg here, whose SHA-1 the raw mappings can't match; like ZalithLauncher2 and HMCL,
                // the raw file is used (the renamer reads both) and the next step's output is checked.
                downloadMappings(p, paths, downloader)
                Logger.info(TAG, "Step $step/${processors.size} ${p.name}: mappings downloaded")
                return@forEachIndexed
            }
            runInJvm(p, paths, loaderName, step, processors.size)
            for ((path, sha) in p.outputs) {
                val f = File(path)
                if (!f.isFile) throw IOException("${p.name} did not write $f")
                if (sha != null) {
                    val actual = FileHash.sha1(f)
                    if (!actual.equals(sha, ignoreCase = true)) {
                        f.delete()
                        throw IOException("${p.name} wrote $f with SHA-1 $actual, expected $sha")
                    }
                }
            }
            Logger.info(TAG, "Step $step/${processors.size} ${p.name}: done")
        }
        ProcessorPlan.doneMarker(plan).writeBytes(ByteArray(0))
        Logger.info(TAG, "$loaderName is set up")
    }

    /** Mojang's client mappings for the processor's `--version`, saved to its `--output`. */
    private suspend fun downloadMappings(p: ProcessorPlan.Processor, paths: AppPaths, downloader: DownloadManager) {
        val minecraft = p.arg("--version") ?: throw IOException("${p.name}: no --version")
        val output = p.arg("--output") ?: throw IOException("${p.name}: no --output")
        val mappings = VersionInfo.decode(paths.versionJson(minecraft).readText()).downloads?.clientMappings
            ?: throw IOException("Minecraft $minecraft has no client_mappings download")
        downloader.ensure(DownloadItem(mappings.url, File(output), mappings.sha1, mappings.size))
    }

    private suspend fun runInJvm(p: ProcessorPlan.Processor, paths: AppPaths, loaderName: String, step: Int, total: Int) {
        val mainClass = JarFile(p.jar).use { it.manifest?.mainAttributes?.getValue(Attributes.Name.MAIN_CLASS) }
            ?.takeIf { it.isNotBlank() } ?: throw IOException("${p.jar} has no Main-Class")
        val classpath = (p.classpath + p.jar).onEach { require(File(it).isFile) { "Missing processor file: $it" } }
        val args = listOf("-cp", classpath.joinToString(File.pathSeparator), mainClass) + p.args
        val jvmArgs = args.joinToString(" ") { quote(it) }
        Logger.info(TAG, "Step $step/$total ${p.name}: $mainClass")
        runJvmRetryRuntimes(
            logId = LOG_ID,
            jvmArgs = jvmArgs,
            prefixArgs = { null },
            jre = Jre.JRE_8,
            userHome = paths.gameDir.absolutePath,
            postSummary = "$loaderName ${p.name} ($step/$total)",
            postProgress = NoticeProgress(total, step)
        )
    }

    /** ZalithLauncher2 splits the argument string with splitPreservingQuotes: quote anything with whitespace. */
    private fun quote(arg: String): String {
        require(!arg.contains('"')) { "A processor argument contains a quote: $arg" }
        return if (arg.any { it.isWhitespace() } || arg.isEmpty()) "\"$arg\"" else arg
    }
}
