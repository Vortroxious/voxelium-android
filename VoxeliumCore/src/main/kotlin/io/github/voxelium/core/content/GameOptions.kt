/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Content/GameOptions.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Minecraft's `options.txt` in a game folder (`key:value` lines). Only the values the launcher needs
 * are read or changed; every other line is kept as the game wrote it.
 */
class GameOptions(gameDirectory: File) {
    val file: File = File(gameDirectory, "options.txt")
    var values: Map<String, String> = parse(readOrEmpty(file))
        private set

    val exists: Boolean get() = file.exists()

    fun int(key: String): Int? = values[key]?.trim()?.toIntOrNull()

    /** Sets [key] to [value], keeping the rest of the file. */
    fun set(key: String, value: String) {
        val text = readOrEmpty(file)
        val lines = if (text.isEmpty()) mutableListOf() else text.split("\n").toMutableList()
        if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
        val i = lines.indexOfFirst { it.startsWith("$key:") }
        if (i >= 0) lines[i] = "$key:$value" else lines.add("$key:$value")
        writeAtomically(file, lines.joinToString("\n") + "\n")
        values = values + (key to value)
    }

    /**
     * For "Large interface": Minecraft's GUI Scale back to Auto (`guiScale:0`), so it uses the biggest
     * interface the resolution allows. Returns the scale that was replaced, or null when it was
     * already Auto or unset.
     */
    fun useAutoGuiScale(): Int? {
        val old = int("guiScale") ?: return null
        if (old == 0) return null
        set("guiScale", "0")
        return old
    }

    /**
     * Minecraft's fullscreen back off. In fullscreen Minecraft takes its size from the display mode
     * while touches arrive in the window's coordinates, so taps miss buttons (seen on iOS with Fabric
     * 1.21.11, 2026-11-01). Returns true when it was on.
     */
    fun turnOffFullscreen(): Boolean {
        if (values["fullscreen"]?.trim() != "true") return false
        set("fullscreen", "false")
        return true
    }

    companion object {
        /**
         * Minecraft 26.3+ picks its renderer from this key (`default`, `opengl`, `vulkan`). After a
         * start that did not finish it switches itself to `opengl`.
         */
        const val GRAPHICS_BACKEND_KEY = "preferredGraphicsBackend"

        /**
         * Sets the renderer to Vulkan before a 26.3+ launch, written as Minecraft writes its string
         * options (JSON, quoted). Minecraft's own datafix resets the key to `default` in options from
         * before data version 4892, so the launcher also passes `--graphicsBackend vulkan`.
         */
        fun forceVulkanBackend(gameDirectory: File) {
            val options = GameOptions(gameDirectory)
            if (options.values[GRAPHICS_BACKEND_KEY] == "\"vulkan\"") return
            options.set(GRAPHICS_BACKEND_KEY, "\"vulkan\"")
        }

        internal fun parse(text: String): Map<String, String> {
            val out = mutableMapOf<String, String>()
            for (line in text.lines()) {
                val colon = line.indexOf(':')
                if (colon < 0) continue
                out[line.substring(0, colon)] = line.substring(colon + 1)
            }
            return out
        }

        internal fun readOrEmpty(file: File): String = runCatching { file.readText() }.getOrDefault("")

        internal fun writeAtomically(file: File, text: String) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.writeText(text)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

/**
 * Advice before starting a version with a shader pack on: iPhones ran out of memory with shaders at
 * render distance 16 but were stable at 8, measured 2026-10-18.
 */
data class ShaderLaunchAdvice(val shaderPack: String, val renderDistance: Int) {
    companion object {
        const val COMFORTABLE_RENDER_DISTANCE = 10
        const val SUGGESTED_RENDER_DISTANCE = 8

        /** Advice for a game folder, or null when shaders are off or the distance is fine. */
        fun check(gameDirectory: File): ShaderLaunchAdvice? {
            val iris = IrisSettings(gameDirectory)
            val pack = iris.shaderPack
            if (!iris.exists || !iris.shadersEnabled || pack == null) return null
            val distance = GameOptions(gameDirectory).int("renderDistance") ?: return null
            if (distance <= COMFORTABLE_RENDER_DISTANCE) return null
            return ShaderLaunchAdvice(pack, distance)
        }

        /** Lowers the render distance to the suggested value (and the simulation distance with it when it is higher). */
        fun applySuggestion(gameDirectory: File) {
            val options = GameOptions(gameDirectory)
            options.set("renderDistance", SUGGESTED_RENDER_DISTANCE.toString())
            val sim = options.int("simulationDistance")
            if (sim != null && sim > SUGGESTED_RENDER_DISTANCE) options.set("simulationDistance", SUGGESTED_RENDER_DISTANCE.toString())
        }
    }
}
