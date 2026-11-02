/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/GameRuntime.swift and RendererChoice from GameRenderer.swift
 * (zalithios e28b28b). GameRenderer itself (MobileGlues on ANGLE/Metal, Vulkan on KosmicKrisp) is
 * iOS-only: on Android ZalithLauncher2's renderers are used.
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.manifest.VersionInfo

/** Which bundled Java and LWJGL a version needs, as ZalithLauncher2 decides it (`detectLwjglVersion`, `lwjglVersionDir`, its jre-8/17/21/25 runtimes). */
object GameRuntime {
    /** Java versions the app bundles. */
    val BUNDLED_JAVA = listOf(8, 17, 21, 25)

    /** The Java major a version asks for; versions without a `javaVersion` field (pre-1.17 JSONs) run on Java 8. */
    fun javaMajor(version: VersionInfo): Int = version.javaVersion?.majorVersion ?: 8

    /** The smallest bundled Java that is at least [major] (16 → 17), or null when the version needs a newer Java than any bundled one. */
    fun java(major: Int, available: List<Int> = BUNDLED_JAVA): Int? = available.sorted().firstOrNull { it >= major }

    data class LwjglVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<LwjglVersion> {
        override fun compareTo(other: LwjglVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        override fun toString() = "$major.$minor.$patch"

        companion object {
            /** "3.3.3", "3.2.2", "2.9.4-nightly-20150209" (suffix ignored). */
            fun parse(s: String): LwjglVersion? {
                val parts = s.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
                if (parts.size < 2) return null
                return LwjglVersion(parts[0], parts[1], parts.getOrElse(2) { 0 })
            }
        }
    }

    /** The highest `org.lwjgl:lwjgl` / `org.lwjgl.lwjgl:lwjgl` version in the libraries. */
    fun lwjgl(version: VersionInfo): LwjglVersion? = version.libraries.mapNotNull { lib ->
        val c = lib.name.split(":")
        if (c.size < 3 || c[1] != "lwjgl" || (c[0] != "org.lwjgl" && c[0] != "org.lwjgl.lwjgl")) null else LwjglVersion.parse(c[2])
    }.maxOrNull()

    /**
     * Which LWJGL build a version uses: 3.4.3 for LWJGL ≥ 3.4.3 (Minecraft 26.3, SDL3 and Vulkan),
     * 3.4.1 for LWJGL ≥ 3.4.1, otherwise 3.3.3 (LWJGL 2 versions additionally need the lwjglx bridge).
     * The Android app (ZalithLauncher2 2.7.2) bundles 3.3.3 and 3.4.1.
     */
    enum class LwjglBuild(val id: String) { V333("3.3.3"), V341("3.4.1"), V343("3.4.3") }

    fun lwjglBuild(v: LwjglVersion?): LwjglBuild = when {
        v == null -> LwjglBuild.V333
        v >= LwjglVersion(3, 4, 3) -> LwjglBuild.V343
        v >= LwjglVersion(3, 4, 1) -> LwjglBuild.V341
        else -> LwjglBuild.V333
    }

    /** Java-side LWJGL modules of Minecraft 26.3+: SDL3 and Vulkan replace GLFW, with shaderc, SPIRV-Cross (spvc) and VMA for the Vulkan renderer. */
    val SDL_VULKAN_LWJGL_MODULES = listOf(
        "lwjgl", "lwjgl-sdl", "lwjgl-vulkan", "lwjgl-shaderc", "lwjgl-spvc", "lwjgl-vma",
        "lwjgl-freetype", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb"
    )

    /** The `org.lwjgl` artifacts a version lists ("lwjgl", "lwjgl-sdl", …). */
    fun lwjglArtifacts(version: VersionInfo): Set<String> = version.libraries.mapNotNull { lib ->
        val c = lib.name.split(":")
        if (c.size >= 3 && c[0] == "org.lwjgl") c[1] else null
    }.toSet()

    fun needsLwjgl2Bridge(v: LwjglVersion?): Boolean = (v?.major ?: 3) < 3
}

/** Which renderers a version can be started with. */
enum class RendererChoice {
    /** One way to start: Minecraft up to 1.16.5 (Java 8). */
    SINGLE,
    /** Minecraft 1.17 – 26.2: an OpenGL renderer, or Vulkan through Zink. (26.2 lists lwjgl-vulkan but draws with OpenGL through GLFW.) */
    OPENGL_OR_VULKAN,
    /** Minecraft 26.3+ (SDL3): its own Vulkan renderer only. */
    VULKAN_ONLY;

    companion object {
        fun of(version: VersionInfo): RendererChoice = when {
            "lwjgl-sdl" in GameRuntime.lwjglArtifacts(version) -> VULKAN_ONLY
            GameRuntime.javaMajor(version) <= 8 -> SINGLE
            else -> OPENGL_OR_VULKAN
        }
    }
}
