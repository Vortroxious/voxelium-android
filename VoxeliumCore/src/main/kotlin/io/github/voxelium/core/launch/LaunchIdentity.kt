/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/LaunchPlanner.swift (LaunchIdentity) (zalithios e28b28b).
 * The rest of LaunchPlanner is ported in phase 6.
 */

package io.github.voxelium.core.launch

/** Who the game runs as: `--username`, `--uuid`, `--accessToken`, `--userType`, `--xuid`. */
data class LaunchIdentity(
    val playerName: String,
    val uuid: String,
    val accessToken: String,
    val userType: String,
    val xuid: String = "0"
)
