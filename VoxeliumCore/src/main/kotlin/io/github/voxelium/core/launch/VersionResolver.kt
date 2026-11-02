/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/VersionResolver.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.paths.AppPaths
import java.io.FileNotFoundException

/** A launchable version: its JSON with every `inheritsFrom` parent merged in. */
data class ResolvedVersion(
    /** The merged version (no `inheritsFrom` left). */
    val info: VersionInfo,
    /** The ids from the requested version down to the root, e.g. `["fabric-loader-0.20.0-1.21.11", "1.21.11"]`. */
    val chain: List<String>
) {
    /** True for mod loader profiles (anything built on another version). */
    val isModded: Boolean get() = chain.size > 1

    /** The vanilla version at the root of the chain. */
    val baseVersionId: String get() = chain.lastOrNull() ?: info.id
}

sealed class VersionResolveException(message: String) : Exception(message) {
    data class ParentMissing(val child: String, val parent: String) :
        VersionResolveException("$child builds on $parent, which is not installed. Install $parent first.")
    data class Cycle(val id: String) : VersionResolveException("Version $id inherits from itself.")
    data class TooDeep(val id: String) : VersionResolveException("Version $id has too many parent versions.")
}

/** Loads `versions/<id>/<id>.json` and merges its parents, as the vanilla launcher does. */
class VersionResolver(val paths: AppPaths) {
    fun resolve(id: String): ResolvedVersion {
        val chain = mutableListOf<String>()
        val versions = mutableListOf<VersionInfo>()
        var next: String? = id
        while (next != null) {
            val current = next
            if (current in chain) throw VersionResolveException.Cycle(current)
            if (chain.size >= 8) throw VersionResolveException.TooDeep(id)
            val file = paths.versionJson(current)
            if (!file.isFile) {
                chain.lastOrNull()?.let { child -> throw VersionResolveException.ParentMissing(child, current) }
                throw FileNotFoundException(file.path)
            }
            val v = VersionInfo.decode(file.readText())
            chain.add(current)
            versions.add(v)
            next = v.inheritsFrom
        }
        // Merge from the root up: root, then each child onto the result.
        var merged = versions.removeAt(versions.lastIndex)
        while (versions.isNotEmpty()) merged = versions.removeAt(versions.lastIndex).merged(merged)
        return ResolvedVersion(merged, chain)
    }
}
