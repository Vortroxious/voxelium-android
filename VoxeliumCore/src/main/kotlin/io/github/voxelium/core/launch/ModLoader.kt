/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/ModLoaders.swift (ModLoader) (zalithios e28b28b).
 * isAvailable(minecraft:) and the installers come with phase 6.
 */

package io.github.voxelium.core.launch

enum class ModLoader(val id: String, val displayName: String) {
    VANILLA("vanilla", "Vanilla"),
    FABRIC("fabric", "Fabric"),
    QUILT("quilt", "Quilt"),
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge");

    companion object {
        fun fromId(id: String?): ModLoader? = entries.firstOrNull { it.id == id }
    }
}
