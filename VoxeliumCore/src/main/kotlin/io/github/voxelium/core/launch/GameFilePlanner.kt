/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/GameFilePlanner.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.download.DownloadItem
import io.github.voxelium.core.manifest.AssetIndex
import io.github.voxelium.core.manifest.Library
import io.github.voxelium.core.manifest.RuleEvaluator
import io.github.voxelium.core.manifest.RulePlatform
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.paths.AppPaths
import java.io.File

/** Decides which libraries a version needs on this launcher and which files must be downloaded to install it. */
class GameFilePlanner(val paths: AppPaths, val platform: RulePlatform = RulePlatform.DEFAULT) {
    /** Java (non-native) libraries for the classpath, de-duplicated by group:artifact:classifier with the later entry winning (mod loader convention), in manifest order. */
    fun classpathLibraries(version: VersionInfo): List<Library> {
        val byKey = LinkedHashMap<String, Library>()
        for (lib in version.libraries) {
            if (!RuleEvaluator.allows(lib.rules, platform) || lib.isNative || isReplacedByLauncher(lib)) continue
            byKey[lib.coordinate?.key ?: lib.name] = lib
        }
        return byKey.values.toList()
    }

    /** Everything needed to install [version] except assets objects (which require the asset index to be downloaded first; see [assetItems]). */
    fun installItems(version: VersionInfo): List<DownloadItem> {
        val items = mutableListOf<DownloadItem>()
        version.downloads?.client?.let { client ->
            items.add(DownloadItem(client.url, paths.clientJar(version.clientJarId), client.sha1, client.size))
        }
        for (lib in classpathLibraries(version)) {
            // Forge/NeoForge files their installer's processors create on first launch.
            val a = lib.artifact ?: continue
            if (a.isProducedByInstaller) continue
            val path = a.path ?: lib.coordinate?.path ?: continue
            items.add(DownloadItem(a.url, paths.library(path), a.sha1, a.size))
        }
        version.assetIndex?.let { idx ->
            items.add(DownloadItem(idx.url, paths.assetIndex(idx.id), idx.sha1, idx.size))
        }
        version.logging?.client?.let { cfg ->
            val id = cfg.file.id ?: return@let
            items.add(DownloadItem(cfg.file.url, paths.resolve("log_configs/$id", paths.assetsDir), cfg.file.sha1, cfg.file.size))
        }
        return items
    }

    fun assetItems(index: AssetIndex, base: String = AssetIndex.RESOURCE_BASE): List<DownloadItem> =
        index.uniqueObjects.map { DownloadItem(AssetIndex.url(it, base), paths.assetObject(it.hash), it.hash, it.size) }

    /** Where a library lives on disk, or null when it has no artifact path. */
    fun libraryFile(lib: Library): File? = (lib.artifact?.path ?: lib.coordinate?.path)?.let { paths.library(it) }

    companion object {
        /**
         * Mirrors ZalithLauncher2's `filterLibrary()`: LWJGL and old platform-native helper jars are
         * replaced by launcher-bundled builds, because Mojang ships no Android/iOS natives.
         */
        fun isReplacedByLauncher(library: Library): Boolean {
            val n = library.name
            return n.contains("org.lwjgl") || n.contains("jinput-platform") || n.contains("twitch-platform")
        }
    }
}
