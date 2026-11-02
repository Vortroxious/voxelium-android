/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Manifest/AssetIndex.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.manifest

import kotlinx.serialization.Serializable

/** An asset index such as `indexes/5.json`. */
@Serializable
data class AssetIndex(
    val objects: Map<String, Object>,
    /** Pre-1.7 indexes: objects are also copied to `assets/virtual/legacy`. */
    val virtual: Boolean? = null,
    /** Very old indexes: objects are copied into `<gameDir>/resources`. */
    val mapToResources: Boolean? = null
) {
    @Serializable
    data class Object(val hash: String, val size: Long)

    /** Unique objects by hash (several names can share one blob). */
    val uniqueObjects: List<Object>
        get() = objects.values.sortedBy { it.hash }.distinctBy { it.hash }

    val totalSize: Long
        get() = uniqueObjects.sumOf { it.size }

    companion object {
        const val RESOURCE_BASE = "https://resources.download.minecraft.net/"

        fun decode(json: String): AssetIndex = MojangJson.decodeFromString(serializer(), json)

        /** Download URL for an object: `<base>/<hash[0..2]>/<hash>`. */
        fun url(obj: Object, base: String = RESOURCE_BASE): String =
            joinUrl(base, "${obj.hash.take(2)}/${obj.hash}")
    }
}

/** Joins a URL base and a relative path with exactly one slash, as Foundation's appendingPathComponent does. */
internal fun joinUrl(base: String, path: String): String =
    base.trimEnd('/') + "/" + path.trimStart('/')
