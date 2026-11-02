/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/GameRuntimeTests.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.launch.GameRuntime.LwjglBuild
import io.github.voxelium.core.launch.GameRuntime.LwjglVersion
import io.github.voxelium.core.manifest.VersionInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRuntimeTests {
    @Test
    fun javaSelection() {
        assertEquals(8, GameRuntime.java(8))
        assertEquals(17, GameRuntime.java(16)) // 1.17
        assertEquals(17, GameRuntime.java(17))
        assertEquals(21, GameRuntime.java(21)) // 1.20.5 – 1.21.x
        assertEquals(25, GameRuntime.java(25)) // 26.x
        assertNull(GameRuntime.java(26))
        assertNull(GameRuntime.java(21, available = listOf(17)))
    }

    @Test
    fun modernVersion() {
        val v = VersionInfo.decode(Fixture.text("1.20.1"))
        assertEquals(17, GameRuntime.javaMajor(v))
        val lw = GameRuntime.lwjgl(v)
        assertEquals(LwjglVersion(3, 3, 1), lw)
        assertEquals(LwjglBuild.V333, GameRuntime.lwjglBuild(lw))
        assertFalse(GameRuntime.needsLwjgl2Bridge(lw))
    }

    @Test
    fun legacyVersionUsesJava8AndLWJGL2() {
        val v = VersionInfo.decode(Fixture.text("1.12.2"))
        assertEquals(8, GameRuntime.javaMajor(v))
        val lw = GameRuntime.lwjgl(v)
        assertEquals(2, lw?.major)
        assertEquals(9, lw?.minor)
        assertTrue(GameRuntime.needsLwjgl2Bridge(lw))
    }

    @Test
    fun lwjgl34UsesTheNewBuild() {
        assertEquals(LwjglBuild.V341, GameRuntime.lwjglBuild(LwjglVersion(3, 4, 1)))
        assertEquals(LwjglBuild.V341, GameRuntime.lwjglBuild(LwjglVersion(3, 4, 2)))
        assertEquals(LwjglBuild.V343, GameRuntime.lwjglBuild(LwjglVersion(3, 4, 3))) // 26.3: SDL3 + Vulkan
        assertEquals(LwjglBuild.V343, GameRuntime.lwjglBuild(LwjglVersion(3, 5, 0)))
        assertEquals(LwjglBuild.V333, GameRuntime.lwjglBuild(LwjglVersion(3, 3, 3)))
        assertEquals(LwjglBuild.V333, GameRuntime.lwjglBuild(null))
    }

    @Test
    fun minecraft263UsesSDLAndVulkanModules() {
        val v = VersionInfo.decode(Fixture.text("26.3"))
        assertEquals(25, GameRuntime.javaMajor(v))
        val lw = GameRuntime.lwjgl(v)
        assertEquals(LwjglVersion(3, 4, 3), lw)
        assertEquals(LwjglBuild.V343, GameRuntime.lwjglBuild(lw))
        // Exactly the LWJGL modules 26.3 lists.
        val listed = GameRuntime.lwjglArtifacts(v)
        assertEquals(GameRuntime.SDL_VULKAN_LWJGL_MODULES.toSet(), listed)
        assertFalse(listed.contains("lwjgl-glfw"))
    }

    @Test
    fun rendererChoicePerVersion() {
        // 26.3: SDL3 + its own Vulkan renderer only.
        assertEquals(RendererChoice.VULKAN_ONLY, RendererChoice.of(VersionInfo.decode(Fixture.text("26.3"))))
        // 1.20.1 (Java 17, LWJGL 3.3): OpenGL or Vulkan through Zink.
        assertEquals(RendererChoice.OPENGL_OR_VULKAN, RendererChoice.of(VersionInfo.decode(Fixture.text("1.20.1"))))
        // 1.12.2 (Java 8): one launch.
        assertEquals(RendererChoice.SINGLE, RendererChoice.of(VersionInfo.decode(Fixture.text("1.12.2"))))
        // 26.2: lwjgl-vulkan without SDL, an OpenGL game on GLFW like 26.1.2: both renderers.
        val json = Json.parseToJsonElement(Fixture.text("26.3")) as JsonObject
        val libs = (json["libraries"] as JsonArray).filter {
            !(it as JsonObject)["name"]!!.jsonPrimitive.content.startsWith("org.lwjgl:lwjgl-sdl:")
        }
        val v262 = VersionInfo.decode(JsonObject(json + ("libraries" to JsonArray(libs))).toString())
        assertTrue(GameRuntime.lwjglArtifacts(v262).contains("lwjgl-vulkan"))
        assertEquals(RendererChoice.OPENGL_OR_VULKAN, RendererChoice.of(v262))
    }
}
