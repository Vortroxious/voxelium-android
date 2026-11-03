/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from the plan reader in JavaRuntime/voxelium-boot/src/io/github/voxelium/boot/Boot.java
 * (zalithios e28b28b). On iOS the plan runs in the game's JVM; on Android the app runs each
 * processor in its own Java process at install time.
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.download.FileHash
import java.io.File

/**
 * The Forge/NeoForge processor plan [ForgeInstaller] writes (`voxelium-processors.txt`):
 *
 *   processor <name>      start of a processor
 *   jar <path>            its jar (Main-Class is read from the manifest)
 *   cp <path>             a classpath entry (repeatable)
 *   arg <value>           an argument (repeatable, in order)
 *   out <path> <sha1>     an output to check afterwards (sha1 may be "-")
 *   end
 *
 * A `<plan>.done` file is written once every processor succeeded.
 */
object ProcessorPlan {
    data class Processor(
        val name: String,
        val jar: String,
        val classpath: List<String>,
        val args: List<String>,
        /** Output path → expected SHA-1, or null when the plan says "-". */
        val outputs: Map<String, String?>
    ) {
        /** The installer tool's task (`--task <name>`), e.g. PROCESS_MINECRAFT_JAR or DOWNLOAD_MOJMAPS. */
        val task: String? get() = arg("--task")

        /** The value after an `--option` argument. */
        fun arg(option: String): String? {
            val i = args.indexOf(option)
            return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
        }
    }

    fun read(plan: File): List<Processor> = parse(plan.readText())

    fun parse(text: String): List<Processor> {
        val list = mutableListOf<Processor>()
        var name: String? = null
        var jar = ""
        var classpath = mutableListOf<String>()
        var args = mutableListOf<String>()
        var outputs = linkedMapOf<String, String?>()
        for (line in text.split("\n")) {
            if (line.isEmpty()) continue
            val sp = line.indexOf(' ')
            val key = if (sp < 0) line else line.substring(0, sp)
            val value = if (sp < 0) "" else line.substring(sp + 1)
            when {
                key == "processor" -> {
                    name = value
                    jar = ""
                    classpath = mutableListOf()
                    args = mutableListOf()
                    outputs = linkedMapOf()
                }
                name == null -> throw IllegalArgumentException("plan: '$key' outside a processor")
                key == "jar" -> jar = value
                key == "cp" -> classpath.add(value)
                key == "arg" -> args.add(value)
                key == "out" -> {
                    val s = value.lastIndexOf(' ')
                    require(s > 0) { "plan: bad out line '$value'" }
                    val sha = value.substring(s + 1)
                    outputs[value.substring(0, s)] = if (sha == "-") null else sha
                }
                key == "end" -> {
                    list.add(Processor(name, jar, classpath, args, outputs))
                    name = null
                }
                else -> throw IllegalArgumentException("plan: unknown key '$key'")
            }
        }
        return list
    }

    /** True when every output exists and matches its SHA-1 (outputs without one only need to exist). */
    fun outputsValid(p: Processor): Boolean = p.outputs.all { (path, sha) ->
        val f = File(path)
        f.isFile && (sha == null || FileHash.sha1(f).equals(sha, ignoreCase = true))
    }

    fun doneMarker(plan: File): File = File(plan.path + ".done")
}
