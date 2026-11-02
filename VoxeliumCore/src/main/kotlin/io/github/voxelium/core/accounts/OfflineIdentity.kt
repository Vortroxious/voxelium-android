/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Accounts/OfflineIdentity.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.accounts

import io.github.voxelium.core.launch.LaunchIdentity
import java.util.UUID

/**
 * Offline identity, as offered by ZalithLauncher2 ("offline" account type). Offline play does not
 * authenticate with Microsoft and cannot join online-mode servers. Microsoft accounts: see [MicrosoftAuth].
 */
object OfflineIdentity {
    /** Player names Minecraft accepts: 3–16 chars of [A-Za-z0-9_]. */
    fun isValidName(name: String): Boolean =
        name.length in 3..16 && name.all { it.code < 0x80 && (it.isLetterOrDigit() || it == '_') }

    /** Same UUID as vanilla's offline mode: UUIDv3(MD5("OfflinePlayer:<name>")). */
    fun uuid(name: String): UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

    fun identity(name: String): LaunchIdentity =
        LaunchIdentity(
            playerName = name,
            uuid = uuid(name).toString().lowercase().replace("-", ""),
            accessToken = "0",
            userType = "legacy"
        )
}
