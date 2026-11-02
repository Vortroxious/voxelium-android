/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/GameInstaller.swift (zalithios e28b28b).
 * installLoader comes with the loader installers.
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.download.DownloadItem
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.DownloadProgress
import io.github.voxelium.core.download.DownloadReport
import io.github.voxelium.core.manifest.AssetIndex
import io.github.voxelium.core.manifest.RulePlatform
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.manifest.VersionManifest
import io.github.voxelium.core.paths.AppPaths
import java.io.File

/**
 * Installs a vanilla version: manifest → version JSON → client, libraries, asset index → asset
 * objects. Each file is hash-verified by [DownloadManager].
 */
class GameInstaller(
    val paths: AppPaths,
    val downloader: DownloadManager = DownloadManager(),
    platform: RulePlatform = RulePlatform.DEFAULT,
    var manifestUrl: String = VersionManifest.OFFICIAL_URL,
    var assetBase: String = AssetIndex.RESOURCE_BASE
) {
    val planner = GameFilePlanner(paths, platform)

    enum class Stage { GAME_FILES, ASSETS }

    /** Always re-fetches the manifest (it has no hash of its own) and caches it. */
    suspend fun fetchManifest(): VersionManifest {
        val cache = File(paths.configDir, "version_manifest_v2.json")
        cache.delete()
        downloader.ensure(DownloadItem(manifestUrl, cache, null, null))
        return VersionManifest.decode(cache.readText())
    }

    suspend fun fetchVersion(entry: VersionManifest.Entry): VersionInfo {
        val dest = paths.versionJson(entry.id)
        downloader.ensure(DownloadItem(entry.url, dest, entry.sha1, null))
        return VersionInfo.decode(dest.readText())
    }

    /**
     * Marker written only after every file of a version downloaded and verified. A version JSON
     * alone does not mean the install finished (the app can be killed mid-download).
     */
    fun completionMarker(id: String): File = File(paths.versionDir(id), MARKER)

    fun isInstalled(id: String): Boolean = runCatching { completionMarker(id).exists() }.getOrDefault(false)

    /** Downloads everything; files that are already valid are skipped, so re-running resumes an interrupted install. */
    suspend fun install(
        version: VersionInfo,
        concurrency: Int = 8,
        progress: ((Stage, DownloadProgress) -> Unit)? = null
    ): DownloadReport {
        val marker = completionMarker(version.id)
        marker.delete()
        val report = downloader.run(planner.installItems(version), concurrency) { progress?.invoke(Stage.GAME_FILES, it) }
        val idx = version.assetIndex
        if (!report.succeeded || idx == null) return report

        val index = AssetIndex.decode(paths.assetIndex(idx.id).readText())
        val assets = downloader.run(planner.assetItems(index, assetBase), concurrency) { progress?.invoke(Stage.ASSETS, it) }
        report.outcomes.putAll(assets.outcomes)
        report.failures.putAll(assets.failures)
        if (report.succeeded) {
            marker.parentFile?.mkdirs()
            marker.writeBytes(ByteArray(0))
        }
        return report
    }

    data class LoaderResult(val id: String, val report: DownloadReport)

    /**
     * Installs a Fabric, Quilt, Forge or NeoForge build: the vanilla base version first (if it is
     * not installed yet), then the loader profile, then the merged version, whose already-present
     * files are verified and skipped. Returns the new version id; the report is the base install's
     * if that failed, else the loader's. For Forge/NeoForge the processor plan still has to run
     * (see [ForgeInstaller]).
     */
    suspend fun installLoader(
        loader: ModLoader, minecraft: String, version: String,
        meta: ModLoaderMeta = ModLoaderMeta(), concurrency: Int = 8,
        progress: ((Stage, DownloadProgress) -> Unit)? = null
    ): LoaderResult {
        if (!isInstalled(minecraft)) {
            val entry = fetchManifest().entry(minecraft)
                ?: throw ModLoaderException.BadProfile("unknown Minecraft version $minecraft")
            val base = install(fetchVersion(entry), concurrency, progress)
            if (!base.succeeded) return LoaderResult(minecraft, base)
        }
        val id = if (loader.usesForgeInstaller) {
            val forge = ForgeInstaller(paths, downloader, meta.http)
                .installProfile(loader, minecraft, version, concurrency) { progress?.invoke(Stage.GAME_FILES, it) }
            if (!forge.report.succeeded) return LoaderResult(forge.id, forge.report)
            forge.id
        } else {
            ModLoaderInstaller(paths, meta).installProfile(loader, minecraft, version).id
        }
        val resolved = VersionResolver(paths).resolve(id)
        return LoaderResult(id, install(resolved.info, concurrency, progress))
    }

    companion object {
        const val MARKER = ".voxelium-install-complete"
    }
}
