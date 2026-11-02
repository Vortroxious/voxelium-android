/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Content/Modpack.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.download.DownloadItem
import io.github.voxelium.core.download.DownloadProgress
import io.github.voxelium.core.download.DownloadReport
import io.github.voxelium.core.launch.GameInstaller
import io.github.voxelium.core.launch.ModLoader
import io.github.voxelium.core.launch.ModLoaderException
import io.github.voxelium.core.launch.ModLoaderMeta
import io.github.voxelium.core.launch.VersionResolver
import io.github.voxelium.core.paths.AppPaths
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

/**
 * A Modrinth modpack (`.mrpack`, https://support.modrinth.com/en/articles/8802351): a ZIP with
 * `modrinth.index.json` (the files to download, with hashes, and the Minecraft and loader versions
 * it needs) plus `overrides/` and `client-overrides/` folders copied over the game folder.
 */
@Serializable
data class ModpackIndex(
    val formatVersion: Int,
    val game: String,
    val versionId: String,
    val name: String,
    val summary: String? = null,
    val files: List<File>,
    /** "minecraft", and one of "fabric-loader", "quilt-loader", "forge", "neoforge". */
    val dependencies: Map<String, String>
) {
    @Serializable
    data class File(
        val path: String,
        val hashes: Map<String, String>,
        val env: Env? = null,
        val downloads: List<String>,
        val fileSize: Long? = null
    ) {
        @Serializable
        data class Env(
            /** "required", "optional" or "unsupported". */
            val client: String? = null,
            val server: String? = null
        )

        /** Files marked unsupported on the client are for servers only. */
        val isForClient: Boolean get() = env?.client != "unsupported"
    }

    data class Loader(val loader: ModLoader, val version: String)

    val minecraft: String? get() = dependencies["minecraft"]

    /** Mods the client gets (files under mods/). */
    val clientModCount: Int get() = files.count { it.isForClient && it.path.startsWith("mods/") }

    /** The loader and its version; null for a pack without one (vanilla). */
    val loader: Loader?
        get() {
            dependencies["fabric-loader"]?.let { return Loader(ModLoader.FABRIC, it) }
            dependencies["quilt-loader"]?.let { return Loader(ModLoader.QUILT, it) }
            dependencies["neoforge"]?.let { return Loader(ModLoader.NEOFORGE, it) }
            // Usually the Forge build ("47.4.10"); some packs write "1.20.1-47.4.10".
            dependencies["forge"]?.let { v ->
                val mc = minecraft
                if (mc != null && v.startsWith("$mc-")) return Loader(ModLoader.FORGE, v.drop(mc.length + 1))
                return Loader(ModLoader.FORGE, v)
            }
            return null
        }

    companion object {
        /**
         * Packs this size did not fit in an iPhone's memory: 55 mods with a shader pack already
         * reached the 3.4 GB limit, and a 507-mod pack was tried (2026-10-18).
         */
        const val LARGE_MOD_COUNT = 150

        private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun decode(json: String): ModpackIndex =
            runCatching { JSON.decodeFromString(serializer(), json) }.getOrElse { throw ModpackException.BadIndex(it.message ?: it.toString()) }
    }
}

sealed class ModpackException(message: String) : Exception(message) {
    data object NotAModpack : ModpackException("This file is not a Modrinth modpack (no modrinth.index.json).")
    data class BadIndex(val why: String) : ModpackException("The modpack's index could not be read: $why")
    data class UnsupportedFormat(val version: Int) : ModpackException("Modpack format $version is not supported (only format 1).")
    data class NotMinecraft(val game: String) : ModpackException("This modpack is for $game, not Minecraft.")
    data object NoMinecraftVersion : ModpackException("The modpack does not say which Minecraft version it needs.")
    data class UnsafePath(val path: String) : ModpackException("The modpack tries to write outside its folder ($path), so it was not installed.")
    data class UntrustedDownload(val host: String) : ModpackException("The modpack downloads from $host, which Modrinth does not allow, so it was not installed.")
    data class NoDownload(val path: String) : ModpackException("The modpack lists $path without a download address.")
    data class AlreadyInstalled(val id: String) : ModpackException("$id is already installed.")
}

/** What a modpack install left behind, kept in the instance (for updates later). */
@Serializable
data class InstalledModpack(
    val name: String,
    val versionNumber: String,
    val minecraft: String,
    val loader: String? = null,
    /** Modrinth ids when it came from Modrinth (null for a file the player picked). */
    val projectID: String? = null,
    val versionID: String? = null
) {
    companion object {
        const val FILE_NAME = ".voxelium-modpack.json"
    }
}

/**
 * Installs `.mrpack` files as their own versions: a small version JSON that builds on the pack's
 * loader profile (or Minecraft itself), so the pack gets its own game folder like any modded version,
 * with the pack's files and overrides in it.
 */
class ModpackInstaller(val game: GameInstaller, var meta: ModLoaderMeta = ModLoaderMeta()) {
    val paths: AppPaths get() = game.paths

    enum class Stage { MINECRAFT, LOADER, MODPACK_FILES, OVERRIDES }

    data class Result(val id: String, val report: DownloadReport)

    /**
     * Installs the pack as version [id] (default: name + version). Minecraft and the loader are
     * installed first when missing. Returns the version id and the download report of the pack's own
     * files; nothing is marked installed unless every file downloaded and verified.
     */
    suspend fun install(
        mrpack: java.io.File, id: String? = null, source: Pair<String, String>? = null,
        progress: ((Stage, DownloadProgress) -> Unit)? = null
    ): Result {
        val (index, zip) = read(mrpack)
        zip.use {
            val versionId = id ?: versionId(index)
            if (game.isInstalled(versionId)) throw ModpackException.AlreadyInstalled(versionId)
            val minecraft = index.minecraft!!
            val instance = paths.instanceDir(versionId)

            // Check everything before downloading or writing anything.
            val downloads = plannedDownloads(index, instance)
            val overrides = plannedOverrides(zip, instance)

            // 1. Minecraft and the loader.
            val loader = index.loader
            val parentId = if (loader != null) {
                val result = game.installLoader(loader.loader, minecraft, loader.version, meta) { _, p -> progress?.invoke(Stage.LOADER, p) }
                if (!result.report.succeeded) return Result(versionId, result.report)
                result.id
            } else {
                if (!game.isInstalled(minecraft)) {
                    val entry = game.fetchManifest().entry(minecraft)
                        ?: throw ModLoaderException.BadProfile("unknown Minecraft version $minecraft")
                    val report = game.install(game.fetchVersion(entry)) { _, p -> progress?.invoke(Stage.MINECRAFT, p) }
                    if (!report.succeeded) return Result(versionId, report)
                }
                minecraft
            }

            // 2. The pack's version JSON, building on the loader profile.
            val parent = VersionResolver(paths).resolve(parentId).info
            instance.mkdirs()
            val json = buildJsonObject {
                put("id", versionId)
                put("inheritsFrom", parentId)
                put("libraries", JsonArray(emptyList()))
                put("mainClass", parent.mainClass)
                put("releaseTime", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
                put("type", "release")
            }
            paths.versionJson(versionId).writeText(PRETTY.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), json))

            // 3. The pack's files (mods, resource packs, shaders), verified.
            val report = game.downloader.run(downloads, concurrency = 6) { progress?.invoke(Stage.MODPACK_FILES, it) }
            if (!report.succeeded) return Result(versionId, report)

            // 4. Overrides (configs, options, bundled packs).
            overrides.forEachIndexed { n, (entry, dest) ->
                dest.parentFile?.mkdirs()
                zip.extract(entry, dest)
                progress?.invoke(Stage.OVERRIDES, DownloadProgress(n + 1, overrides.size, 0))
            }

            // 5. Remember where it came from, then mark the version installed.
            val record = InstalledModpack(
                name = index.name, versionNumber = index.versionId, minecraft = minecraft,
                loader = loader?.loader?.displayName, projectID = source?.first, versionID = source?.second
            )
            File(instance, InstalledModpack.FILE_NAME).writeText(RECORD_JSON.encodeToString(InstalledModpack.serializer(), record))
            val final = game.install(VersionResolver(paths).resolve(versionId).info)
            return Result(versionId, final)
        }
    }

    /**
     * Checks every file before anything is written: paths must stay inside the instance, downloads
     * must come from allowed hosts. Returns the download items.
     */
    fun plannedDownloads(index: ModpackIndex, instance: java.io.File): List<DownloadItem> =
        index.files.filter { it.isForClient }.map { file ->
            val dest = try {
                paths.resolve(file.path, instance)
            } catch (_: Exception) {
                throw ModpackException.UnsafePath(file.path)
            }
            val url = file.downloads.firstOrNull { host(it)?.lowercase() in ALLOWED_HOSTS }
            if (url == null) {
                file.downloads.firstOrNull()?.let { other -> throw ModpackException.UntrustedDownload(host(other) ?: other) }
                throw ModpackException.NoDownload(file.path)
            }
            DownloadItem(url, dest, file.hashes["sha1"]?.lowercase(), file.fileSize)
        }

    /** The override entries to copy (overrides/ then client-overrides/, which wins), as (entry name, destination), checked like the downloads. */
    fun plannedOverrides(zip: ZipReader, instance: java.io.File): List<Pair<String, java.io.File>> {
        val out = mutableListOf<Pair<String, java.io.File>>()
        for (prefix in listOf("overrides/", "client-overrides/")) {
            for (e in zip.entries) {
                if (!e.startsWith(prefix) || e.endsWith("/")) continue
                val dest = try {
                    paths.resolve(e.drop(prefix.length), instance)
                } catch (_: Exception) {
                    throw ModpackException.UnsafePath(e)
                }
                out.add(e to dest)
            }
        }
        return out
    }

    companion object {
        /** Hosts the modpack format allows files to be downloaded from. */
        val ALLOWED_HOSTS = setOf("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com")

        private val RECORD_JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val PRETTY = Json { prettyPrint = true }

        private fun host(url: String): String? = runCatching { URI(url).host }.getOrNull()

        /** Reads and checks a `.mrpack`. The returned zip stays open for the overrides: close it after use. */
        fun read(mrpack: java.io.File): Pair<ModpackIndex, ZipReader> {
            val zip = try {
                ZipReader(mrpack)
            } catch (_: Exception) {
                throw ModpackException.NotAModpack
            }
            try {
                val data = zip.read("modrinth.index.json", limit = 32L shl 20) ?: throw ModpackException.NotAModpack
                val index = ModpackIndex.decode(data.toString(Charsets.UTF_8))
                if (index.formatVersion != 1) throw ModpackException.UnsupportedFormat(index.formatVersion)
                if (index.game != "minecraft") throw ModpackException.NotMinecraft(index.game)
                if (index.minecraft == null) throw ModpackException.NoMinecraftVersion
                return index to zip
            } catch (e: Exception) {
                zip.close()
                throw e
            }
        }

        /** A version id for the pack: its name and version, with only characters that are safe in a folder name and on Minecraft's command line. */
        fun versionId(index: ModpackIndex): String {
            // Some packs already put the version in the name ("Genesis 3.2" + "3.2").
            val raw = if (index.name.endsWith(index.versionId)) index.name else "${index.name} ${index.versionId}"
            val cleaned = buildString {
                raw.codePoints().forEach { cp ->
                    if (Character.isLetterOrDigit(cp) || cp.toChar() in " .-_+") appendCodePoint(cp) else append('-')
                }
            }.trim()
            val limited = cleaned.take(80)
            return limited.ifEmpty { "Modpack" }
        }

        /** The modpack an instance was installed from, if any. */
        fun installedModpack(instance: java.io.File): InstalledModpack? =
            runCatching {
                RECORD_JSON.decodeFromString(InstalledModpack.serializer(), File(instance, InstalledModpack.FILE_NAME).readText())
            }.getOrNull()
    }
}
