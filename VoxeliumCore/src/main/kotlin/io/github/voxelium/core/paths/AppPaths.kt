/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Paths/AppPaths.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.paths

import java.io.File

sealed class PathException(message: String) : Exception(message) {
    /** A relative path from untrusted metadata tried to escape its base directory. */
    data class EscapesBase(val path: String) : PathException("Path escapes its base directory: $path")
    data object Empty : PathException("Empty relative path") {
        private fun readResolve(): Any = Empty
    }
}

/**
 * The launcher's on-disk layout. Everything lives below [root]; in tests a temp directory.
 *
 * The game folder follows the vanilla `.minecraft` structure so version JSONs and library
 * paths from Mojang can be used unchanged:
 *   gameDir/{versions,libraries,assets/{indexes,objects},saves,resourcepacks,…}
 *   gameDir/versions/<id>/   (also the game folder of a modded version or modpack)
 *   root/runtimes/<name>/, root/logs/, root/config/, root/controls/
 *
 * On iOS [gameDir] is `root/minecraft`. On Android the app passes its existing
 * `.minecraft` folder, so versions installed before the core was used keep working.
 */
class AppPaths(root: File, gameDir: File? = null) {
    val root: File = root.absoluteFile.normalize()
    val gameDir: File = (gameDir ?: File(this.root, "minecraft")).absoluteFile.normalize()

    val versionsDir: File get() = File(gameDir, "versions")
    val librariesDir: File get() = File(gameDir, "libraries")
    val assetsDir: File get() = File(gameDir, "assets")
    val assetIndexesDir: File get() = File(assetsDir, "indexes")
    val assetObjectsDir: File get() = File(assetsDir, "objects")
    val runtimesDir: File get() = File(root, "runtimes")
    val logsDir: File get() = File(root, "logs")
    val configDir: File get() = File(root, "config")
    /** The on-screen control layout (controls/layout.json), shared in format with the iOS app. */
    val controlsDir: File get() = File(root, "controls")

    fun versionDir(id: String): File = resolve(id, versionsDir)

    /**
     * A modded version's own game folder (mods, config, saves, options), as ZalithLauncher2's
     * version isolation does; vanilla versions share [gameDir].
     */
    fun instanceDir(id: String): File = versionDir(id)
    fun versionJson(id: String): File = File(versionDir(id), "$id.json")
    fun clientJar(id: String): File = File(versionDir(id), "$id.jar")
    fun nativesDir(id: String): File = File(versionDir(id), "natives")
    fun library(relativePath: String): File = resolve(relativePath, librariesDir)
    fun assetIndex(id: String): File = resolve("$id.json", assetIndexesDir)

    fun assetObject(hash: String): File {
        if (hash.length < 2) throw PathException.EscapesBase(hash)
        return resolve("${hash.take(2)}/$hash", assetObjectsDir)
    }

    /** Creates every top-level directory of the layout. */
    fun createLayout() {
        for (dir in listOf(versionsDir, librariesDir, assetIndexesDir, assetObjectsDir, runtimesDir, logsDir, configDir)) {
            if (!dir.isDirectory && !dir.mkdirs()) throw java.io.IOException("Can't create $dir")
        }
    }

    /**
     * Joins a relative path that came from downloaded metadata onto [base],
     * rejecting absolute paths and `..` traversal.
     */
    fun resolve(relative: String, base: File): File {
        if (relative.isEmpty()) throw PathException.Empty
        if (relative.startsWith("/") || relative.startsWith("~")) throw PathException.EscapesBase(relative)
        val components = relative.split("/").filter { it.isNotEmpty() }
        if (components.any { it == ".." || it == "." } || relative.contains("\\") || relative.contains("\u0000")) {
            throw PathException.EscapesBase(relative)
        }
        val normalizedBase = base.absoluteFile.normalize()
        val file = components.fold(normalizedBase) { dir, part -> File(dir, part) }.normalize()
        if (!file.path.startsWith(normalizedBase.path + File.separator)) throw PathException.EscapesBase(relative)
        return file
    }
}
