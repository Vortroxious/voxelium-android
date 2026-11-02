/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/VersionDeleter.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.content.WorldManager
import io.github.voxelium.core.paths.AppPaths
import java.io.File

sealed class VersionDeleteException(message: String) : Exception(message) {
    data class NeededBy(val id: String, val others: List<String>) : VersionDeleteException(
        "$id can't be deleted while ${others.joinToString(", ")} ${if (others.size == 1) "builds" else "build"} on it. " +
            "Delete ${if (others.size == 1) "that" else "those"} first."
    )
    data class NotInstalled(val id: String) : VersionDeleteException("$id is not installed.")
}

/**
 * Deletes an installed version: its `versions/<id>/` folder. For a modded version or modpack that
 * folder is also its game folder (worlds, mods, config, options), so all of that goes with it;
 * vanilla versions share the game folder (worlds in `saves`), which is left alone. Libraries and
 * assets are shared between versions and stay, so reinstalling is quick.
 */
class VersionDeleter(val paths: AppPaths) {
    /** Other versions whose inheritance chain includes [id] (a loader on its Minecraft version, a modpack on its loader), sorted. */
    fun dependents(id: String): List<String> {
        val resolver = VersionResolver(paths)
        return (paths.versionsDir.list() ?: emptyArray()).filter { other ->
            other != id && (runCatching { resolver.resolve(other) }.getOrNull()?.chain?.drop(1)?.contains(id) ?: false)
        }.sorted()
    }

    /** What would be lost with a modded version: its worlds (folders with a level.dat). */
    fun worldCount(id: String): Int {
        val r = runCatching { VersionResolver(paths).resolve(id) }.getOrNull() ?: return 0
        if (!r.isModded) return 0
        val dir = runCatching { paths.instanceDir(id) }.getOrNull() ?: return 0
        return WorldManager(dir).list().size
    }

    /** Refuses while another version builds on [id]. The install marker goes first, so an interrupted delete never leaves a half-deleted version listed as installed. */
    fun delete(id: String) {
        val dir = paths.versionDir(id) // rejects ids that escape versions/
        if (!dir.exists()) throw VersionDeleteException.NotInstalled(id)
        val others = dependents(id)
        if (others.isNotEmpty()) throw VersionDeleteException.NeededBy(id, others)
        File(dir, GameInstaller.MARKER).delete()
        if (!dir.deleteRecursively()) throw java.io.IOException("Couldn't delete $dir")
    }
}
