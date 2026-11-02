/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/SystemTests.swift (SettingsTests) (zalithios e28b28b).
 * Not ported: largeInterfaceSnapsToMinecraftsGUISteps (iOS game window code).
 */

package io.github.voxelium.core.config

import io.github.voxelium.core.TempDir
import io.github.voxelium.core.logging.LogLevel
import io.github.voxelium.core.logging.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsTests {
    @Test
    fun roundTrip() {
        TempDir().use { tmp ->
            val store = SettingsStore(tmp.paths)
            assertEquals(LauncherSettings(), store.load(Logger()))
            val s = LauncherSettings(selectedVersion = "1.20.1", playerName = "Alex", maxMemoryMB = 3072)
            store.save(s)
            assertEquals(s, store.load(Logger()))
        }
    }

    @Test
    fun missingKeysUseDefaults() {
        TempDir().use { tmp ->
            val store = SettingsStore(tmp.paths)
            tmp.paths.configDir.mkdirs()
            store.file.writeText("""{"playerName":"Old"}""")
            val s = store.load(Logger())
            assertEquals("Old", s.playerName)
            assertEquals(LauncherSettings().maxMemoryMB, s.maxMemoryMB)
            assertTrue(s.largeInterface) // on for settings saved before the switch existed
        }
    }

    @Test
    fun corruptFileIsMovedAside() {
        TempDir().use { tmp ->
            val store = SettingsStore(tmp.paths)
            tmp.paths.configDir.mkdirs()
            store.file.writeText("{not json")
            val log = Logger()
            assertEquals(LauncherSettings(), store.load(log))
            assertTrue(File(store.file.path + ".corrupt").exists())
            assertTrue(log.snapshot.any { it.level == LogLevel.ERROR && it.category == "settings" })
        }
    }
}
