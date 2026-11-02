/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/LaunchPlanner.swift (zalithios e28b28b). LaunchIdentity is in
 * LaunchIdentity.kt.
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.manifest.RulePlatform
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.paths.AppPaths
import java.io.File

/** A fully resolved command line for the JVM. The engine (JRE, LWJGL natives, renderer) still has to start it. */
data class LaunchPlan(
    val mainClass: String,
    val classpath: List<File>,
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    val requiredJavaMajor: Int?,
    /** Classpath entries that are not on disk yet. */
    val missingFiles: List<File>
)

sealed class LaunchPlanException(message: String) : Exception(message) {
    /** A mod loader profile (`inheritsFrom`) was passed unmerged; resolve it with [VersionResolver] first. */
    data class InheritanceNotSupported(val id: String) : LaunchPlanException("$id must be resolved with its parent versions first.")
}

class LaunchPlanner(
    val paths: AppPaths,
    val platform: RulePlatform = RulePlatform.DEFAULT,
    val launcherName: String = "Voxelium",
    val launcherVersion: String = "0.1.0"
) {
    data class Resolution(val width: Int, val height: Int)

    fun plan(
        version: VersionInfo,
        identity: LaunchIdentity,
        resolution: Resolution? = null,
        extraJvmArguments: List<String> = emptyList(),
        gameDirectory: File? = null,
        includeClientJar: Boolean = true
    ): LaunchPlan {
        // Mod loader profiles must be merged with their parent first (VersionResolver).
        if (version.inheritsFrom != null) throw LaunchPlanException.InheritanceNotSupported(version.id)
        val platform = if (resolution != null) platform.copy(features = platform.features + ("has_custom_resolution" to true)) else platform

        val files = GameFilePlanner(paths, platform)
        val libraries = files.classpathLibraries(version)
        val classpath = libraries.mapNotNull { files.libraryFile(it) }.toMutableList()
        // Created by the Forge/NeoForge processors on the first launch (ForgeInstaller), so not "missing" before it.
        val produced = libraries.filter { it.artifact?.isProducedByInstaller == true }.mapNotNull { files.libraryFile(it) }.toSet()
        // Forge and NeoForge run a patched copy made by their processors; the vanilla jar on the
        // classpath is a second Minecraft that Forge 1.21's bootstrap trips over.
        if (includeClientJar) classpath.add(paths.clientJar(version.clientJarId))

        val separator = ":"
        val vars = mutableMapOf(
            "auth_player_name" to identity.playerName,
            "auth_uuid" to identity.uuid,
            "auth_access_token" to identity.accessToken,
            "auth_session" to identity.accessToken,
            "auth_xuid" to identity.xuid,
            "clientid" to "",
            "user_type" to identity.userType,
            "user_properties" to "{}",
            "version_name" to version.id,
            "version_type" to (version.type?.rawValue ?: "release"),
            "game_directory" to (gameDirectory ?: paths.gameDir).path,
            "assets_root" to paths.assetsDir.path,
            "game_assets" to paths.assetsDir.path,
            "assets_index_name" to (version.assetIndex?.id ?: version.assets ?: "legacy"),
            "natives_directory" to paths.nativesDir(version.id).path,
            "launcher_name" to launcherName,
            "launcher_version" to launcherVersion,
            "classpath" to classpath.joinToString(separator) { it.path },
            "classpath_separator" to separator,
            "library_directory" to paths.librariesDir.path
        )
        if (resolution != null) {
            vars["resolution_width"] = resolution.width.toString()
            vars["resolution_height"] = resolution.height.toString()
        }

        val args = version.arguments
        val rawGame: List<String>
        val rawJvm: List<String>
        if (args != null) {
            rawGame = (args.game ?: emptyList()).flatMap { it.resolve(platform) }
            rawJvm = (args.jvm ?: emptyList()).flatMap { it.resolve(platform) }
        } else {
            rawGame = (version.minecraftArguments ?: "").split(" ").filter { it.isNotEmpty() }
            // Pre-1.13 versions have no JVM arguments in their JSON; these are the standard ones every launcher adds.
            rawJvm = listOf("-Djava.library.path=\${natives_directory}", "-cp", "\${classpath}")
        }

        val jvm = (extraJvmArguments + rawJvm.map { substitute(it, vars) }).toMutableList()
        version.logging?.client?.let { log ->
            val id = log.file.id ?: return@let
            val cfg = paths.resolve("log_configs/$id", paths.assetsDir)
            jvm.add(log.argument.replace("\${path}", cfg.path))
        }

        val missing = classpath.filter { it !in produced && !it.exists() }
        return LaunchPlan(
            mainClass = version.mainClass,
            classpath = classpath,
            jvmArguments = jvm,
            gameArguments = rawGame.map { substitute(it, vars) },
            requiredJavaMajor = version.javaVersion?.majorVersion,
            missingFiles = missing
        )
    }

    companion object {
        /** Replaces `${name}` placeholders. Unknown placeholders are left as-is so they are visible in diagnostics instead of silently disappearing. */
        fun substitute(s: String, vars: Map<String, String>): String {
            if (!s.contains("\${")) return s
            val out = StringBuilder()
            var rest = s
            while (true) {
                val open = rest.indexOf("\${")
                if (open < 0) break
                out.append(rest, 0, open)
                val close = rest.indexOf('}', open + 2)
                if (close < 0) {
                    out.append(rest, open, rest.length)
                    return out.toString()
                }
                val key = rest.substring(open + 2, close)
                out.append(vars[key] ?: "\${$key}")
                rest = rest.substring(close + 1)
            }
            return out.append(rest).toString()
        }
    }
}
