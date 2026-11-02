/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Content/Modrinth.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.accounts.HttpClient
import io.github.voxelium.core.accounts.OkHttpJsonClient
import io.github.voxelium.core.accounts.get
import io.github.voxelium.core.accounts.json
import io.github.voxelium.core.download.DownloadItem
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.DownloadProgress
import io.github.voxelium.core.download.DownloadReport
import io.github.voxelium.core.download.FileHash
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

/**
 * Modrinth API v2 (https://docs.modrinth.com/api/), the open platform ZalithLauncher2 also
 * browses. No key is needed; requests carry a Voxelium User-Agent.
 */
class ModrinthApi(private val http: HttpClient = OkHttpJsonClient()) {
    @Serializable
    data class SearchHit(
        @SerialName("project_id") val projectId: String,
        val slug: String,
        val title: String,
        val description: String,
        val author: String? = null,
        val downloads: Long = 0,
        @SerialName("icon_url") val iconUrl: String? = null,
        @SerialName("project_type") val projectType: String,
        val categories: List<String>? = null
    ) {
        val id: String get() = projectId
    }

    @Serializable
    data class SearchPage(
        val hits: List<SearchHit>,
        val offset: Int,
        val limit: Int,
        @SerialName("total_hits") val totalHits: Int
    )

    @Serializable
    data class Project(
        val id: String,
        val slug: String,
        val title: String,
        @SerialName("project_type") val projectType: String,
        @SerialName("icon_url") val iconUrl: String? = null
    )

    @Serializable
    data class Version(
        val id: String,
        @SerialName("project_id") val projectId: String,
        val name: String,
        @SerialName("version_number") val versionNumber: String,
        /** "release", "beta", "alpha". */
        @SerialName("version_type") val versionType: String,
        val files: List<File>,
        val dependencies: List<Dependency>,
        val loaders: List<String>,
        @SerialName("game_versions") val gameVersions: List<String>
    ) {
        @Serializable
        data class File(
            val url: String,
            val filename: String,
            val primary: Boolean,
            val size: Long,
            val hashes: Hashes
        ) {
            @Serializable
            data class Hashes(val sha1: String? = null)
        }

        @Serializable
        data class Dependency(
            @SerialName("project_id") val projectId: String? = null,
            @SerialName("version_id") val versionId: String? = null,
            /** "required", "optional", "incompatible", "embedded". */
            @SerialName("dependency_type") val dependencyType: String
        )

        /** The file to install: the primary one, else the first. */
        val primaryFile: File? get() = files.firstOrNull { it.primary } ?: files.firstOrNull()
    }

    enum class Sort(val id: String) { RELEVANCE("relevance"), DOWNLOADS("downloads"), FOLLOWS("follows"), NEWEST("newest"), UPDATED("updated") }

    // Requests

    suspend fun search(
        query: String, kind: ContentKind, gameVersion: String, gameLoader: String?,
        sort: Sort = Sort.RELEVANCE, offset: Int = 0, limit: Int = 20
    ): SearchPage {
        val facets = mutableListOf(listOf("project_type:${projectType(kind)}"), listOf("versions:$gameVersion"))
        val loaders = loaders(kind, gameLoader).filter { it != "minecraft" }
        if (loaders.isNotEmpty()) facets.add(loaders.map { "categories:$it" })
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("facets", jsonArray(facets.map { group -> jsonArray(group.map { quoted(it) }) }))
            .addQueryParameter("index", sort.id)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .build().toString()
        return getDecoded(url, SearchPage.serializer(), "Modrinth search")
    }

    /** A project's versions for these loaders and game version, newest first. */
    suspend fun versions(project: String, loaders: List<String>, gameVersion: String): List<Version> {
        val builder = "$BASE/project/$project/version".toHttpUrl().newBuilder()
            .addQueryParameter("game_versions", jsonArray(listOf(quoted(gameVersion))))
        if (loaders.isNotEmpty()) builder.addQueryParameter("loaders", jsonArray(loaders.map { quoted(it) }))
        return getDecoded(builder.build().toString(), ListSerializer(Version.serializer()), "Modrinth versions")
    }

    suspend fun version(id: String): Version = getDecoded("$BASE/version/$id", Version.serializer(), "Modrinth version")

    /**
     * Modrinth modpacks for a loader Voxelium can run (Fabric, Quilt, Forge, NeoForge), optionally for
     * one Minecraft version. (Swift: extension ModrinthAPI in Modpack.swift.)
     */
    suspend fun searchModpacks(
        query: String, gameVersion: String? = null, sort: Sort = Sort.RELEVANCE, offset: Int = 0, limit: Int = 20
    ): SearchPage {
        val facets = mutableListOf(
            listOf("project_type:modpack"),
            listOf("categories:fabric", "categories:quilt", "categories:forge", "categories:neoforge")
        )
        if (gameVersion != null) facets.add(listOf("versions:$gameVersion"))
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("facets", jsonArray(facets.map { group -> jsonArray(group.map { quoted(it) }) }))
            .addQueryParameter("index", sort.id)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .build().toString()
        return getDecoded(url, SearchPage.serializer(), "Modrinth search")
    }

    /** A modpack's versions for a loader Voxelium can run, newest first. */
    suspend fun modpackVersions(project: String): List<Version> {
        val url = "$BASE/project/$project/version".toHttpUrl().newBuilder()
            .addQueryParameter("loaders", jsonArray(listOf("fabric", "quilt", "forge", "neoforge").map { quoted(it) }))
            .build().toString()
        return getDecoded(url, ListSerializer(Version.serializer()), "Modrinth versions")
    }

    suspend fun project(id: String): Project = getDecoded("$BASE/project/$id", Project.serializer(), "Modrinth project")

    /** Which Modrinth versions these files (by SHA-1) are; unknown hashes are absent. */
    suspend fun versionsForSha1(hashes: List<String>): Map<String, Version> {
        if (hashes.isEmpty()) return emptyMap()
        val r = http.json("$BASE/version_files", buildJsonObject {
            put("hashes", JsonArray(hashes.map { JsonPrimitive(it) }))
            put("algorithm", "sha1")
        })
        if (r.status != 200) throw ModrinthException.Http("Modrinth file lookup", r.status)
        return decode(r.data, VERSION_MAP, "Modrinth file lookup")
    }

    /**
     * The newest version of each file's project for these loaders and game version
     * (POST /version_files/update), by SHA-1. Unknown hashes are absent. Modrinth answers with the
     * newest of any type, betas included.
     */
    suspend fun latestVersions(hashes: List<String>, loaders: List<String>, gameVersion: String): Map<String, Version> {
        if (hashes.isEmpty()) return emptyMap()
        val r = http.json("$BASE/version_files/update", buildJsonObject {
            put("hashes", JsonArray(hashes.map { JsonPrimitive(it) }))
            put("algorithm", "sha1")
            put("game_versions", JsonArray(listOf(JsonPrimitive(gameVersion))))
            if (loaders.isNotEmpty()) put("loaders", JsonArray(loaders.map { JsonPrimitive(it) }))
        })
        if (r.status != 200) throw ModrinthException.Http("Modrinth update check", r.status)
        return decode(r.data, VERSION_MAP, "Modrinth update check")
    }

    private suspend fun <T> getDecoded(url: String, serializer: KSerializer<T>, what: String): T {
        val r = http.get(url)
        if (r.status != 200) throw ModrinthException.Http(what, r.status)
        return decode(r.data, serializer, what)
    }

    private fun <T> decode(data: ByteArray, serializer: KSerializer<T>, what: String): T =
        runCatching { JSON.decodeFromString(serializer, data.toString(Charsets.UTF_8)) }
            .getOrElse { throw ModrinthException.BadResponse(what) }

    companion object {
        const val BASE = "https://api.modrinth.com/v2"

        internal val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val VERSION_MAP = MapSerializer(String.serializer(), Version.serializer())

        /** Modrinth's project type for a kind of content. */
        fun projectType(kind: ContentKind): String = when (kind) {
            ContentKind.MODS -> "mod"
            ContentKind.RESOURCE_PACKS -> "resourcepack"
            ContentKind.SHADER_PACKS -> "shader"
        }

        /**
         * The loaders a version of this kind must support on a game version's loader: mods match
         * the loader (Quilt also runs Fabric mods); resource packs are "minecraft"; shader packs
         * need Iris on Fabric/Quilt.
         */
        fun loaders(kind: ContentKind, gameLoader: String?): List<String> = when (kind) {
            ContentKind.MODS -> if (gameLoader == "quilt") listOf("quilt", "fabric") else listOfNotNull(gameLoader)
            ContentKind.RESOURCE_PACKS -> listOf("minecraft")
            ContentKind.SHADER_PACKS -> listOf("iris")
        }

        /** The version to install: the newest release, else the newest of any type. */
        fun preferred(versions: List<Version>): Version? = versions.firstOrNull { it.versionType == "release" } ?: versions.firstOrNull()

        private fun quoted(s: String) = JsonPrimitive(s).toString()
        private fun jsonArray(items: List<String>) = items.joinToString(",", "[", "]")
    }
}

sealed class ModrinthException(message: String) : Exception(message) {
    data class Http(val what: String, val status: Int) : ModrinthException("$what failed (HTTP $status).")
    data class BadResponse(val what: String) : ModrinthException("Unexpected response from $what.")
    data class NoCompatibleVersion(val name: String) : ModrinthException("$name has no version for this Minecraft version and loader.")
}

/** What installing one Modrinth version brings in: the file plus its required dependencies, minus what the game folder already has. */
data class ModrinthInstallPlan(
    val items: MutableList<Item> = mutableListOf(),
    /** Required dependencies that are already installed (project ids). */
    val alreadyInstalled: MutableList<String> = mutableListOf(),
    /** Required dependencies with no compatible version (titles). */
    val unavailable: MutableList<String> = mutableListOf(),
    /** Installed projects the new version declares incompatible (titles). */
    val conflicts: MutableList<String> = mutableListOf()
) {
    data class Item(val projectId: String, val title: String, val version: ModrinthApi.Version, val isDependency: Boolean)
}

/** A newer Modrinth version of an installed file. */
data class ModUpdate(
    val projectId: String,
    /** The installed item's name (from its metadata). */
    val name: String,
    /** The installed file, removed once the update is in place. */
    val file: File,
    val current: ModrinthApi.Version,
    val latest: ModrinthApi.Version
) {
    val id: String get() = projectId
}

/** Plans and installs Modrinth content into a game folder. */
class ModrinthInstaller(
    val api: ModrinthApi = ModrinthApi(),
    val downloader: DownloadManager = DownloadManager()
) {
    /** Modrinth project ids of the files installed for [kind] (by SHA-1 lookup), mapped to the files on disk. */
    suspend fun installedProjects(manager: ContentManager, kind: ContentKind): Map<String, List<File>> {
        val byHash = manager.list(kind).filter { !it.isFolder }
            .mapNotNull { item -> runCatching { FileHash.sha1(item.file) }.getOrNull()?.let { it to item.file } }.toMap()
        val found = api.versionsForSha1(byHash.keys.toList())
        val out = mutableMapOf<String, MutableList<File>>()
        for ((hash, version) in found) byHash[hash]?.let { out.getOrPut(version.projectId) { mutableListOf() }.add(it) }
        return out
    }

    /**
     * The chosen version plus every required dependency (recursively), using the exact dependency
     * version when the author pinned one, else the preferred compatible version. Dependencies
     * already installed are skipped.
     */
    suspend fun plan(
        version: ModrinthApi.Version, title: String, kind: ContentKind, gameVersion: String,
        gameLoader: String?, installed: Set<String>
    ): ModrinthInstallPlan {
        val plan = ModrinthInstallPlan()
        val seen = mutableSetOf(version.projectId)
        val queue = ArrayDeque(listOf(Triple(version, title, false)))
        val depLoaders = ModrinthApi.loaders(ContentKind.MODS, gameLoader)
        while (queue.isNotEmpty()) {
            val (v, t, isDep) = queue.removeFirst()
            plan.items.add(ModrinthInstallPlan.Item(v.projectId, t, v, isDep))
            for (dep in v.dependencies) {
                if (dep.dependencyType != "required" && dep.dependencyType != "incompatible") continue
                // A pinned dependency may give only a version id.
                val pinned = dep.versionId?.let { runCatching { api.version(it) }.getOrNull() }
                val pid = dep.projectId ?: pinned?.projectId ?: continue
                if (dep.dependencyType == "incompatible") {
                    if (pid in installed) plan.conflicts.add(projectTitle(pid))
                    continue
                }
                if (pid in seen) continue
                seen.add(pid)
                if (pid in installed) {
                    plan.alreadyInstalled.add(pid)
                    continue
                }
                val depTitle = projectTitle(pid)
                // Shader packs and resource packs can depend on mods (e.g. Iris); those resolve as mods.
                val chosen = pinned ?: ModrinthApi.preferred(
                    runCatching { api.versions(pid, depLoaders, gameVersion) }.getOrDefault(emptyList())
                )
                if (chosen == null) {
                    plan.unavailable.add(depTitle)
                    continue
                }
                queue.addLast(Triple(chosen, depTitle, true))
            }
        }
        return plan
    }

    /**
     * Installed files of [kind] that have a newer Modrinth version for this game version and loader.
     * Files are matched by SHA-1, so only files that came from Modrinth (any launcher) are checked;
     * switched-off files are skipped. An installed release is only offered a newer release, never a beta.
     */
    suspend fun checkUpdates(manager: ContentManager, kind: ContentKind, gameVersion: String, gameLoader: String?): List<ModUpdate> {
        val byHash = manager.list(kind).filter { !it.isFolder && it.isEnabled }
            .mapNotNull { item -> runCatching { FileHash.sha1(item.file) }.getOrNull()?.let { it to item } }.toMap()
        if (byHash.isEmpty()) return emptyList()
        val hashes = byHash.keys.toList()
        val loaders = ModrinthApi.loaders(kind, gameLoader)
        val current = api.versionsForSha1(hashes)
        val latest = api.latestVersions(hashes, loaders, gameVersion)
        val updates = mutableListOf<ModUpdate>()
        for ((hash, item) in byHash) {
            val now = current[hash] ?: continue
            var newest = latest[hash] ?: continue
            if (newest.id == now.id) continue
            if (now.versionType == "release" && newest.versionType != "release") {
                val list = runCatching { api.versions(now.projectId, loaders, gameVersion) }.getOrDefault(emptyList())
                val release = list.firstOrNull { it.versionType == "release" } ?: continue
                if (release.id == now.id) continue
                val releaseIndex = list.indexOfFirst { it.id == release.id }
                val nowIndex = list.indexOfFirst { it.id == now.id }
                if (nowIndex >= 0 && releaseIndex >= nowIndex) continue
                newest = release
            }
            updates.add(ModUpdate(now.projectId, item.name, item.file, now, newest))
        }
        return updates.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private suspend fun projectTitle(projectId: String): String =
        runCatching { api.project(projectId).title }.getOrDefault(projectId)

    /**
     * Downloads every planned file into the folder of its kind (the chosen item into [kind],
     * dependencies, which are mods, into mods/), verifying SHA-1s. A project that is already
     * installed in another version is replaced.
     */
    suspend fun install(
        plan: ModrinthInstallPlan, kind: ContentKind, manager: ContentManager,
        replacing: Map<String, List<File>> = emptyMap(),
        progress: ((DownloadProgress) -> Unit)? = null
    ): DownloadReport {
        val items = mutableListOf<DownloadItem>()
        val replaced = mutableListOf<File>()
        for (entry in plan.items) {
            val file = entry.version.primaryFile ?: continue
            val folder = manager.folder(if (entry.isDependency) ContentKind.MODS else kind)
            folder.mkdirs()
            val dest = File(folder, file.filename.replace("/", "_"))
            items.add(DownloadItem(file.url, dest, file.hashes.sha1, file.size))
            replaced += (replacing[entry.projectId] ?: emptyList()).filter { it.canonicalPath != dest.canonicalPath }
        }
        val report = downloader.run(items, concurrency = 4, progress = progress)
        if (report.succeeded) replaced.forEach { it.delete() }
        return report
    }
}
