/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/ForgeLaunch.swift (zalithios e28b28b). The iOS boot jar names
 * (voxelium-boot.jar, its main class) are not ported: on Android the plan runs at install time.
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.content.GameOptions
import io.github.voxelium.core.paths.AppPaths
import java.io.File

/** What a Forge or NeoForge version needs at launch, on top of the plan [LaunchPlanner] makes. */
object ForgeLaunch {
    /** The processor plan of the first version in the chain that has one (a modpack builds on the Forge/NeoForge version that owns it). */
    fun processorPlan(resolved: ResolvedVersion, paths: AppPaths): File? =
        resolved.chain.asSequence().mapNotNull { ForgeInstaller.processorPlan(it, paths) }.firstOrNull()

    /**
     * The java launcher accepts `--add-opens X`, `-p X` etc., but JNI_CreateJavaVM only takes
     * HotSpot's own `--add-opens=X` / `--module-path=X` forms, so convert them. Everything else
     * passes through unchanged.
     */
    fun jniOptions(args: List<String>): List<String> {
        val joined = mapOf(
            "--add-opens" to "--add-opens=", "--add-exports" to "--add-exports=", "--add-reads" to "--add-reads=",
            "--add-modules" to "--add-modules=", "--patch-module" to "--patch-module=", "--limit-modules" to "--limit-modules=",
            "--enable-native-access" to "--enable-native-access=",
            "-p" to "--module-path=", "--module-path" to "--module-path="
        )
        val out = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val prefix = joined[args[i]]
            if (prefix != null && i + 1 < args.size) {
                out.add(prefix + args[i + 1])
                i += 2
            } else {
                out.add(args[i])
                i += 1
            }
        }
        return out
    }

    /**
     * BootstrapLauncher (Forge 1.17 – 1.20.x, NeoForge before 21.9) turns every classpath jar not
     * named in `-DignoreList` into a module; add launcher-only jars to it.
     */
    fun extendIgnoreList(args: List<String>, names: List<String>): List<String> =
        args.map { a -> if (a.startsWith("-DignoreList=")) (listOf(a) + names).joinToString(",") else a }

    /**
     * FML's early loading screen opens its own GLFW window and GL context on another thread, which a
     * phone's single game window cannot provide. Turn it off in `config/fml.toml` (Forge 1.17+ and
     * NeoForge read the same key); other settings in the file are kept, and FML fills in the rest
     * when the file is new.
     */
    fun disableEarlyWindow(gameDirectory: File) {
        val file = File(gameDirectory, "config/fml.toml")
        val text = runCatching { file.readText() }.getOrDefault("")
        val lines = if (text.isEmpty()) mutableListOf() else text.split("\n").toMutableList()
        fun isKey(line: String): Boolean {
            val t = line.trim()
            return t.startsWith("earlyWindowControl") && t.removePrefix("earlyWindowControl").trim().startsWith("=")
        }
        val i = lines.indexOfFirst { isKey(it) }
        if (i >= 0) {
            if (lines[i].replace(" ", "") == "earlyWindowControl=false") return
            lines[i] = "earlyWindowControl = false"
        } else {
            if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
            lines.add("earlyWindowControl = false")
            lines.add("")
        }
        GameOptions.writeAtomically(file, lines.joinToString("\n"))
    }
}
