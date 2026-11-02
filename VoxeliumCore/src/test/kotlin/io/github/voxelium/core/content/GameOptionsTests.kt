/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/ContentManagerTests.swift (IrisSettingsTests,
 * GraphicsBackendOptionTests, ShaderLaunchAdviceTests) and SystemTests.swift
 * (largeInterfaceSetsGUIScaleToAuto, fullscreenIsTurnedOff) (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.TempDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameOptionsTests {
    @Test
    fun largeInterfaceSetsGUIScaleToAuto() {
        TempDir().use { tmp ->
            File(tmp.dir, "options.txt").writeText("renderDistance:8\nguiScale:2\nfov:0.0\n")
            val options = GameOptions(tmp.dir)
            assertEquals(2, options.useAutoGuiScale())
            assertEquals("renderDistance:8\nguiScale:0\nfov:0.0\n", options.file.readText())
            assertNull(options.useAutoGuiScale()) // already Auto
            // No options.txt yet (first launch): nothing is written.
            val fresh = GameOptions(File(tmp.dir, "new"))
            assertNull(fresh.useAutoGuiScale())
            assertFalse(fresh.exists)
        }
    }

    /** F11 on a hardware keyboard turned on Minecraft's fullscreen, after which no tap hit a Minecraft button: the launcher turns it off again. */
    @Test
    fun fullscreenIsTurnedOff() {
        TempDir().use { tmp ->
            File(tmp.dir, "options.txt").writeText("fov:0.0\nfullscreen:true\nguiScale:0\n")
            val options = GameOptions(tmp.dir)
            assertTrue(options.turnOffFullscreen())
            assertEquals("fov:0.0\nfullscreen:false\nguiScale:0\n", options.file.readText())
            assertFalse(options.turnOffFullscreen()) // already off
            val fresh = GameOptions(File(tmp.dir, "new"))
            assertFalse(fresh.turnOffFullscreen())
            assertFalse(fresh.exists)
        }
    }

    @Test
    fun forcesVulkanAndKeepsTheRest() {
        TempDir().use { tmp ->
            val game = File(tmp.dir, "instance")
            val file = File(game, "options.txt")

            // No options yet (first launch): the file is created with just the key.
            GameOptions.forceVulkanBackend(game)
            assertEquals("preferredGraphicsBackend:\"vulkan\"\n", file.readText())

            // Minecraft switched itself to OpenGL after a failed start: back to Vulkan, every other line as the game wrote it.
            file.writeText("version:5023\npreferredGraphicsBackend:\"opengl\"\nrenderDistance:8\n")
            GameOptions.forceVulkanBackend(game)
            assertEquals("version:5023\npreferredGraphicsBackend:\"vulkan\"\nrenderDistance:8\n", file.readText())
            assertEquals("\"vulkan\"", GameOptions(game).values["preferredGraphicsBackend"])

            // Options from 26.1 have no such key: it is added at the end.
            file.writeText("version:4786\nrenderDistance:12\n")
            GameOptions.forceVulkanBackend(game)
            assertEquals("version:4786\nrenderDistance:12\npreferredGraphicsBackend:\"vulkan\"\n", file.readText())
        }
    }

    @Test
    fun warnsAboutHighRenderDistanceWithShadersAndLowersIt() {
        TempDir().use { tmp ->
            val game = File(tmp.dir, "instance")
            // No Iris settings or options yet: nothing to say.
            assertNull(ShaderLaunchAdvice.check(game))

            File(game, "config").mkdirs()
            File(game, "config/iris.properties").writeText("enableShaders=true\nshaderPack=BSL_v10.1.8.zip\n")
            File(game, "options.txt").writeText("version:4671\nrenderDistance:16\nsimulationDistance:12\nmaxFps:120\n")
            assertEquals(ShaderLaunchAdvice("BSL_v10.1.8.zip", 16), ShaderLaunchAdvice.check(game))

            ShaderLaunchAdvice.applySuggestion(game)
            assertEquals("version:4671\nrenderDistance:8\nsimulationDistance:8\nmaxFps:120\n", File(game, "options.txt").readText())
            assertNull(ShaderLaunchAdvice.check(game))

            // Shaders off: no advice even at 16.
            GameOptions(game).set("renderDistance", "16")
            IrisSettings(game).setShadersEnabled(false)
            assertNull(ShaderLaunchAdvice.check(game))
        }
    }
}

class IrisSettingsTests {
    @Test
    fun readsOculusOnForge() {
        TempDir().use { tmp ->
            val game = File(tmp.dir, "forge")
            File(game, "config").mkdirs()
            // As Oculus 1.8.0 wrote it on the iPhone (Forge 1.20.1, 2026-10-21).
            File(game, "config/oculus.properties").writeText(
                "#This file stores configuration options for Iris, such as the currently active shaderpack\n" +
                    "colorSpace=SRGB\nshaderPack=BSL_v10.1.8.zip\nenableShaders=true\n\n"
            )
            File(game, "options.txt").writeText("renderDistance:12\n")
            val oculus = IrisSettings(game)
            assertTrue(oculus.exists)
            assertEquals("oculus.properties", oculus.file.name)
            assertEquals("BSL_v10.1.8.zip", oculus.shaderPack)
            assertEquals(ShaderLaunchAdvice("BSL_v10.1.8.zip", 12), ShaderLaunchAdvice.check(game))
            oculus.setShadersEnabled(false)
            assertFalse(IrisSettings(game).shadersEnabled)
        }
    }

    @Test
    fun readsAndSwitchesShadersOff() {
        TempDir().use { tmp ->
            val game = File(tmp.dir, "instance")
            val none = IrisSettings(game)
            assertFalse(none.exists)
            assertTrue(none.shadersEnabled)
            assertNull(none.shaderPack)

            // As Iris 1.10.8 wrote it on the iPhone (2026-10-18), with an escaped name.
            File(game, "config").mkdirs()
            val original = "#This file stores configuration options for Iris, such as the currently active shaderpack\n" +
                "#Sun Oct 18 02:07:36 GMT 2026\nallowUnknownShaders=false\nenableShaders=true\nmaxShadowRenderDistance=32\n" +
                "shaderPack=My\\: Pack.zip\n\n"
            File(game, "config/iris.properties").writeText(original)
            val iris = IrisSettings(game)
            assertTrue(iris.exists && iris.shadersEnabled)
            assertEquals("My: Pack.zip", iris.shaderPack)

            iris.setShadersEnabled(false)
            assertEquals(original.replace("enableShaders=true", "enableShaders=false"), iris.file.readText())
            assertFalse(IrisSettings(game).shadersEnabled)

            // No file yet: the key is added.
            val fresh = File(tmp.dir, "fresh")
            IrisSettings(fresh).setShadersEnabled(false)
            assertFalse(IrisSettings(fresh).shadersEnabled)
        }
    }
}
