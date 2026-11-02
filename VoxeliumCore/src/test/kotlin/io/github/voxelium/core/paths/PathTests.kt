/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/SystemTests.swift (PathTests, LoggerTests) (zalithios e28b28b).
 */

package io.github.voxelium.core.paths

import io.github.voxelium.core.TempDir
import io.github.voxelium.core.logging.LogLevel
import io.github.voxelium.core.logging.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PathTests {
    @Test
    fun layoutIsUnderRoot() {
        TempDir().use { tmp ->
            val p = tmp.paths
            p.createLayout()
            assertTrue(p.assetObjectsDir.isDirectory)
            assertTrue(p.library("com/mojang/x/1/x-1.jar").path.startsWith(p.librariesDir.path + File.separator))
            assertTrue(p.assetObject("abcdef").path.endsWith("objects/ab/abcdef"))
            assertTrue(p.clientJar("1.20.1").path.endsWith("versions/1.20.1/1.20.1.jar"))
        }
    }

    @Test
    fun rejectsTraversal() {
        TempDir().use { tmp ->
            for (bad in listOf("../etc/passwd", "/etc/passwd", "a/../../b", "~/x", "a\\b", "", "./a", "a/\u0000b")) {
                assertThrows("should reject '$bad'", PathException::class.java) { tmp.paths.library(bad) }
            }
        }
    }

    @Test
    fun versionIDCannotEscape() {
        TempDir().use { tmp ->
            assertThrows(PathException::class.java) { tmp.paths.versionJson("../../x") }
        }
    }

    /** Android passes its existing `.minecraft` as the game folder. */
    @Test
    fun customGameDirIsUsed() {
        TempDir().use { tmp ->
            val game = File(tmp.dir, ".minecraft")
            val p = AppPaths(tmp.dir, gameDir = game)
            assertEquals(File(game, "versions/1.21.1/1.21.1.json").path, p.versionJson("1.21.1").path)
            assertEquals(File(tmp.dir, "runtimes").path, p.runtimesDir.path)
        }
    }
}

class LoggerTests {
    @Test
    fun writesToFileAndRespectsLevel() {
        TempDir().use { tmp ->
            val log = Logger(maxEntries = 2)
            val file = File(tmp.dir, "logs/launcher.log")
            log.attachFile(file)
            log.minimumLevel = LogLevel.INFO
            log.debug("t", "hidden")
            log.info("t", "one")
            log.warning("t", "two")
            log.error("t", "three")
            assertEquals(listOf("two", "three"), log.snapshot.map { it.message })
            val text = file.readText()
            assertTrue(text.contains("[INFO] t: one"))
            assertFalse(text.contains("hidden"))
        }
    }
}
