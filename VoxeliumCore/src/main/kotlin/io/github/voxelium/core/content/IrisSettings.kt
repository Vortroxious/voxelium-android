/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Content/IrisSettings.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import java.io.File

/**
 * Iris's `config/iris.properties` in a game folder (or `config/oculus.properties`, Oculus's copy of
 * the same format on Forge): which shader pack is active and whether shaders are on. Lets the
 * launcher switch shaders off when a pack leaves the game unusable (e.g. a black screen), without
 * opening the game.
 */
class IrisSettings(gameDirectory: File) {
    val file: File

    /** `enableShaders`; Iris defaults to on when the file says nothing. */
    var shadersEnabled: Boolean
        private set

    /** `shaderPack`, the active pack's file or folder name. */
    val shaderPack: String?

    init {
        val iris = File(gameDirectory, "config/iris.properties")
        val oculus = File(gameDirectory, "config/oculus.properties")
        file = if (!iris.exists() && oculus.exists()) oculus else iris
        val values = parse(GameOptions.readOrEmpty(file))
        shadersEnabled = values["enableShaders"]?.let { it.lowercase() == "true" } ?: true
        shaderPack = values["shaderPack"]?.ifEmpty { null }
    }

    /** Whether Iris has written its settings yet (it does on first start). */
    val exists: Boolean get() = file.exists()

    /** Writes `enableShaders`, keeping every other line as Iris wrote it. */
    fun setShadersEnabled(on: Boolean) {
        val text = GameOptions.readOrEmpty(file)
        val lines = if (text.isEmpty()) mutableListOf() else text.split("\n").toMutableList()
        if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
        val entry = "enableShaders=$on"
        val i = lines.indexOfFirst { key(it) == "enableShaders" }
        if (i >= 0) lines[i] = entry else lines.add(entry)
        GameOptions.writeAtomically(file, lines.joinToString("\n") + "\n")
        shadersEnabled = on
    }

    companion object {
        internal fun key(line: String): String? {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) return null
            val sep = t.indexOfFirst { it == '=' || it == ':' }
            return if (sep < 0) t else t.substring(0, sep).trim()
        }

        /** Java properties, enough for Iris's file: `key=value` lines, comments, and backslash escapes in values (`\:`, `\=`, `\\`). */
        internal fun parse(text: String): Map<String, String> {
            val out = mutableMapOf<String, String>()
            for (line in text.lines()) {
                val k = key(line) ?: continue
                val t = line.trim()
                val value = StringBuilder()
                val sep = t.indexOfFirst { it == '=' || it == ':' }
                if (sep >= 0) {
                    var escaped = false
                    for (c in t.substring(sep + 1).dropWhile { it == ' ' }) {
                        when {
                            escaped -> {
                                value.append(c)
                                escaped = false
                            }
                            c == '\\' -> escaped = true
                            else -> value.append(c)
                        }
                    }
                }
                out[k] = value.toString()
            }
            return out
        }
    }
}
