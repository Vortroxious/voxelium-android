/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Config/SettingsStore.swift (zalithios e28b28b).
 * Not ported: LauncherSettings.effectiveRenderScale (iOS game window code).
 */

package io.github.voxelium.core.config

import io.github.voxelium.core.logging.Logger
import io.github.voxelium.core.paths.AppPaths
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Launcher settings persisted as JSON. Never stores credentials; account tokens are kept
 * encrypted by the app. Missing keys take their defaults, so older files keep loading after
 * new fields are added.
 */
@Serializable
data class LauncherSettings(
    val selectedVersion: String? = null,
    val playerName: String = "Player",
    val maxMemoryMB: Int = 2048,
    val showSnapshots: Boolean = false,
    val downloadConcurrency: Int = 8,
    /** Game render resolution as a fraction of the screen's native pixels (0.25–1). */
    val renderScale: Double = 0.5,
    /** Launch with the signed-in Microsoft account (when there is one) instead of the offline profile. */
    val useMicrosoftAccount: Boolean = true,
    /** Show Minecraft's largest interface (hotbar). On by default. */
    val largeInterface: Boolean = true
) {
    companion object {
        val RENDER_SCALE_RANGE = 0.25..1.0
    }
}

class SettingsStore(val file: File) {
    constructor(paths: AppPaths) : this(File(paths.configDir, "settings.json"))

    /**
     * Returns defaults when the file is missing. A corrupt file is moved aside
     * (settings.json.corrupt) and defaults are returned.
     */
    fun load(log: Logger = Logger.shared): LauncherSettings {
        if (!file.isFile) return LauncherSettings()
        return try {
            JSON.decodeFromString(LauncherSettings.serializer(), file.readText())
        } catch (e: Exception) {
            log.error("settings", "Corrupt settings file, resetting: $e")
            val aside = File(file.path + ".corrupt")
            aside.delete()
            file.renameTo(aside)
            LauncherSettings()
        }
    }

    fun save(settings: LauncherSettings) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(JSON.encodeToString(LauncherSettings.serializer(), settings))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
