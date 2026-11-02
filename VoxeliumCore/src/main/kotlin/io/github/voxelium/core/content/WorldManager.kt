/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Content/WorldManager.swift (zalithios e28b28b).
 * Android: java.util.zip reads ZIP64, and entries are streamed to disk instead of read into memory.
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.paths.AppPaths
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

/** One Java Edition world: a folder in `saves` holding `level.dat`. */
data class WorldItem(
    /** The folder name, which Minecraft shows under the world's title. */
    val name: String,
    val folder: File,
    /** When `level.dat` was last written (Minecraft saves it on every exit). */
    val lastPlayed: Instant?,
    val size: Long
) {
    val id: String get() = folder.path
}

sealed class WorldException(message: String) : Exception(message) {
    data class NotAJavaWorld(val name: String) :
        WorldException("$name is not a Minecraft Java world (there is no level.dat in it).")
    data class BedrockWorld(val name: String) :
        WorldException("$name is a Bedrock Edition world. Voxelium runs Java Edition, which can't open Bedrock worlds.")
    data class UnsafePath(val entry: String) :
        WorldException("The zip was refused because one of its files points outside the world folder ($entry).")
    data class UnsupportedZip(val why: String) :
        WorldException("This zip can't be read: $why. Unzip it and import the folder instead.")
}

/**
 * The worlds of one game folder (`.minecraft/` for vanilla, `.minecraft/versions/<id>/` for a
 * modded version): list, import a folder or a .zip, delete. Imports never overwrite: a clashing
 * name gets " (2)", " (3)" …
 */
class WorldManager(gameDirectory: File) {
    val gameDirectory: File = gameDirectory.absoluteFile.normalize()
    val saves: File get() = File(gameDirectory, "saves")

    /** Worlds in `saves`, most recently played first. */
    fun list(): List<WorldItem> =
        (saves.list() ?: emptyArray()).filter { !it.startsWith(".") }
            .mapNotNull { item(File(saves, it)) }
            .sortedWith { a, b ->
                val x = a.lastPlayed
                val y = b.lastPlayed
                when {
                    x != null && y != null && x != y -> y.compareTo(x)
                    x != null && y == null -> -1
                    x == null && y != null -> 1
                    else -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
                }
            }

    /** Copies a world folder (the one holding `level.dat`) into `saves`. */
    fun importFolder(source: File): WorldItem {
        val name = source.name
        if (source.extension.lowercase() == "mcworld") throw WorldException.BedrockWorld(name)
        if (!File(source, "level.dat").exists()) throw WorldException.NotAJavaWorld(name)
        if (isBedrockLayout(File(source, "db").exists(), File(source, "region").exists())) throw WorldException.BedrockWorld(name)
        return stage(build = { staging -> source.copyRecursively(staging) }, finish = { staging -> place(staging, name) })
    }

    /**
     * Extracts the world in a .zip (at its top level or in a subfolder) into `saves`. Only entries
     * below the shallowest `level.dat` are extracted; any entry with an absolute or `..` path makes
     * the whole zip refused.
     */
    fun importZip(source: File): WorldItem {
        val zipName = source.nameWithoutExtension
        if (source.extension.lowercase() == "mcworld") throw WorldException.BedrockWorld(source.name)
        // Android 14+ may refuse to open a zip with `..` or absolute entry names; that ends here as
        // "not a zip", which still keeps the world out.
        val reader = try {
            ZipReader(source)
        } catch (e: Exception) {
            throw zipError(e)
        }
        return reader.use {
            val checker = AppPaths(saves)
            val probe = File(saves, "probe")
            for (e in reader.entries) {
                try {
                    checker.resolve(e, probe)
                } catch (_: Exception) {
                    throw WorldException.UnsafePath(e)
                }
            }

            val files = reader.entries.filter { !it.endsWith("/") && !it.startsWith("__MACOSX/") }
            val levelDat = files.filter { it == "level.dat" || it.endsWith("/level.dat") }
                .minByOrNull { it.split("/").size } ?: throw WorldException.NotAJavaWorld(source.name)
            val prefix = levelDat.dropLast("level.dat".length)
            val world = files.filter { it.startsWith(prefix) }
            val relative = world.map { it.drop(prefix.length) }
            if (isBedrockLayout(relative.any { it.startsWith("db/") }, relative.any { it.startsWith("region/") })) {
                throw WorldException.BedrockWorld(source.name)
            }
            val name = if (prefix.isEmpty()) zipName else prefix.dropLast(1).split("/").lastOrNull() ?: zipName

            stage(build = { staging ->
                staging.mkdirs()
                val writer = AppPaths(staging)
                for ((entry, rel) in world.zip(relative)) {
                    val dest = try {
                        writer.resolve(rel, staging)
                    } catch (_: Exception) {
                        throw WorldException.UnsafePath(entry)
                    }
                    dest.parentFile?.mkdirs()
                    try {
                        if (!reader.extract(entry, dest)) throw WorldException.UnsupportedZip("$entry is missing")
                    } catch (e: WorldException) {
                        throw e
                    } catch (e: Exception) {
                        throw zipError(e)
                    }
                }
            }, finish = { staging -> place(staging, name) })
        }
    }

    /** Deletes a world folder (only ones directly inside `saves`). */
    fun delete(world: WorldItem) {
        if (world.folder.absoluteFile.normalize().parentFile?.path != saves.absoluteFile.normalize().path) {
            throw WorldException.UnsafePath(world.folder.path)
        }
        if (!world.folder.deleteRecursively()) throw java.io.IOException("Couldn't delete ${world.folder}")
    }

    /** "World", or "World (2)", "World (3)" … if that name is taken in `saves`. */
    fun uniqueName(proposed: String): String {
        var base = proposed.replace("/", "-").replace(":", "-").trim()
        while (base.startsWith(".")) base = base.drop(1)
        if (base.isEmpty()) base = "World"
        var candidate = base
        var n = 2
        while (File(saves, candidate).exists()) {
            candidate = "$base ($n)"
            n += 1
        }
        return candidate
    }

    // Helpers

    /** Builds the world in a hidden folder inside `saves` first, so a failed import leaves nothing half-copied behind. */
    private fun stage(build: (File) -> Unit, finish: (File) -> WorldItem): WorldItem {
        saves.mkdirs()
        val staging = File(saves, ".importing-${UUID.randomUUID()}")
        try {
            build(staging)
            return finish(staging)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun place(staging: File, name: String): WorldItem {
        val dest = File(saves, uniqueName(name))
        Files.move(staging.toPath(), dest.toPath())
        return item(dest) ?: throw WorldException.NotAJavaWorld(name)
    }

    private fun item(folder: File): WorldItem? {
        val level = File(folder, "level.dat")
        if (!folder.isDirectory || !level.exists()) return null
        val modified = level.lastModified().takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
        return WorldItem(folder.name, folder, modified, size(folder))
    }

    companion object {
        internal fun size(folder: File): Long = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        /** Bedrock worlds also have a level.dat, but keep chunks in a LevelDB `db/` folder. */
        internal fun isBedrockLayout(hasDb: Boolean, hasRegion: Boolean): Boolean = hasDb && !hasRegion

        internal fun zipError(error: Throwable): WorldException = when (error) {
            is ZipReader.ZipError.NotAZip -> WorldException.UnsupportedZip("it is not a zip file")
            is ZipReader.ZipError.Unsupported -> WorldException.UnsupportedZip(error.what)
            is ZipReader.ZipError.Corrupt -> WorldException.UnsupportedZip("it is damaged or password-protected")
            else -> WorldException.UnsupportedZip(error.toString())
        }
    }
}
