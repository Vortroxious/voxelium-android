/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Content/CurseForge.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.accounts.HttpClient
import io.github.voxelium.core.accounts.HttpHeaders
import io.github.voxelium.core.accounts.HttpRequest
import io.github.voxelium.core.accounts.OkHttpJsonClient
import io.github.voxelium.core.download.DownloadItem
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.DownloadProgress
import io.github.voxelium.core.download.DownloadReport
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * CurseForge's API for third parties (https://docs.curseforge.com/rest-api/). The key goes in the
 * `x-api-key` header and nowhere else.
 *
 * Authors can turn off downloads in other apps: those files come without a `downloadUrl`, and
 * Voxelium then only links to the file's CurseForge page.
 */
class CurseForgeApi(private val key: String, private val http: HttpClient = OkHttpJsonClient()) {
    @Serializable
    data class Links(val websiteUrl: String? = null)

    @Serializable
    data class Author(val name: String)

    @Serializable
    data class Logo(val thumbnailUrl: String? = null)

    @Serializable
    data class Mod(
        val id: Int,
        val name: String,
        val slug: String,
        val summary: String,
        val downloadCount: Double = 0.0,
        val links: Links? = null,
        val authors: List<Author>? = null,
        val logo: Logo? = null,
        val classId: Int? = null,
        /** False when the author doesn't allow downloads outside CurseForge. */
        val allowModDistribution: Boolean? = null
    ) {
        val websiteUrl: String? get() = links?.websiteUrl
    }

    @Serializable
    data class File(
        val id: Int,
        val modId: Int,
        val displayName: String,
        val fileName: String,
        /** 1 release, 2 beta, 3 alpha. */
        val releaseType: Int,
        val fileLength: Long,
        /** null when the author doesn't allow downloads outside CurseForge. */
        val downloadUrl: String? = null,
        val hashes: List<Hash>,
        val gameVersions: List<String>,
        val dependencies: List<Dependency>
    ) {
        @Serializable
        data class Hash(
            val value: String,
            /** 1 SHA-1, 2 MD5. */
            val algo: Int
        )

        @Serializable
        data class Dependency(
            val modId: Int,
            /** 3 required, 5 incompatible (1 embedded, 2 optional, 4 tool, 6 include). */
            val relationType: Int
        )

        val sha1: String? get() = hashes.firstOrNull { it.algo == 1 }?.value
        val releaseName: String get() = mapOf(1 to "release", 2 to "beta", 3 to "alpha")[releaseType] ?: "release"
    }

    @Serializable
    internal data class Page<T>(val data: List<T>, val pagination: Pagination? = null) {
        @Serializable
        data class Pagination(val index: Int, val resultCount: Int, val totalCount: Int)
    }

    @Serializable
    internal data class One<T>(val data: T)

    @Serializable
    internal data class Game(val id: Int)

    data class SearchPage(val mods: List<Mod>, val index: Int, val totalCount: Int)

    /** CurseForge's sort fields (ModsSearchSortField). */
    enum class Sort(val value: Int, val title: String) {
        POPULARITY(2, "Popular"), UPDATED(3, "Updated"), NAME(4, "Name"), DOWNLOADS(6, "Downloads")
    }

    // Requests

    /** One page of mods for this game version and loader. For Quilt, Fabric mods are searched (most Quilt players run them). */
    suspend fun search(
        query: String, kind: ContentKind, gameVersion: String, gameLoader: String?,
        sort: Sort = Sort.POPULARITY, index: Int = 0, pageSize: Int = 20
    ): SearchPage {
        val params = mutableListOf(
            "gameId" to "$MINECRAFT", "classId" to "${classId(kind)}", "gameVersion" to gameVersion,
            "sortField" to "${sort.value}", "sortOrder" to if (sort == Sort.NAME) "asc" else "desc",
            "index" to "$index", "pageSize" to "$pageSize"
        )
        if (query.isNotEmpty()) params.add("searchFilter" to query)
        loaderTypes(kind, gameLoader).lastOrNull()?.let { params.add("modLoaderType" to "$it") }
        val page = try {
            get("/mods/search", params, Page.serializer(Mod.serializer()), "CurseForge search")
        } catch (e: CurseForgeException.InvalidKey) {
            // Some keys work everywhere except search (seen 2026-11-01: 403 "API Key missing or
            // invalid" on /mods/search only). Tell that apart from a key that's simply wrong.
            if (runCatching { checkKey() }.isSuccess) throw CurseForgeException.SearchNotAllowed
            throw e
        }
        return SearchPage(page.data, page.pagination?.index ?: index, page.pagination?.totalCount ?: page.data.size)
    }

    /** A mod's files for this game version and loader, newest first. With several loader types (Quilt), files for the first come first. */
    suspend fun files(modId: Int, kind: ContentKind, gameVersion: String, gameLoader: String?): List<File> {
        val loaders = loaderTypes(kind, gameLoader)
        val out = mutableListOf<File>()
        for (loader in loaders.ifEmpty { listOf(null) }) {
            val params = mutableListOf("gameVersion" to gameVersion, "pageSize" to "50")
            if (loader != null) params.add("modLoaderType" to "$loader")
            val page = get("/mods/$modId/files", params, Page.serializer(File.serializer()), "CurseForge files")
            out += page.data.filter { f -> out.none { it.id == f.id } }.sortedByDescending { it.id }
        }
        return out
    }

    suspend fun mod(id: Int): Mod = get("/mods/$id", emptyList(), One.serializer(Mod.serializer()), "CurseForge mod").data

    /** Checks the key with one cheap request (the Minecraft game entry). */
    suspend fun checkKey() {
        get("/games/$MINECRAFT", emptyList(), One.serializer(Game.serializer()), "CurseForge")
    }

    private suspend fun <T> get(path: String, params: List<Pair<String, String>>, serializer: KSerializer<T>, what: String): T {
        val builder = (BASE + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        val response = http.send(
            HttpRequest(
                builder.build().toString(), "GET",
                mapOf("Accept" to "application/json", "User-Agent" to HttpHeaders.userAgent, "x-api-key" to key)
            )
        )
        when (response.status) {
            200 -> Unit
            401, 403 -> throw CurseForgeException.InvalidKey
            else -> throw CurseForgeException.Http(what, response.status)
        }
        return runCatching { JSON.decodeFromString(serializer, response.data.toString(Charsets.UTF_8)) }
            .getOrElse { throw CurseForgeException.BadResponse(what) }
    }

    companion object {
        const val BASE = "https://api.curseforge.com/v1"

        /** CurseForge's game id for Minecraft. */
        const val MINECRAFT = 432

        internal val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** CurseForge's class (category group) for a kind of content. */
        fun classId(kind: ContentKind): Int = when (kind) {
            ContentKind.MODS -> 6
            ContentKind.RESOURCE_PACKS -> 12
            ContentKind.SHADER_PACKS -> 6552
        }

        /**
         * CurseForge's loader types for mods on a game loader, in order of preference: Quilt also
         * runs Fabric mods. Resource and shader packs have no loader.
         */
        fun loaderTypes(kind: ContentKind, gameLoader: String?): List<Int> {
            if (kind != ContentKind.MODS || gameLoader == null) return emptyList()
            return when (gameLoader) {
                "forge" -> listOf(1)
                "fabric" -> listOf(4)
                "quilt" -> listOf(5, 4)
                "neoforge" -> listOf(6)
                else -> emptyList()
            }
        }

        /** The file to install: the newest release, else the newest of any type. */
        fun preferred(files: List<File>): File? = files.firstOrNull { it.releaseType == 1 } ?: files.firstOrNull()
    }
}

sealed class CurseForgeException(message: String) : Exception(message) {
    data object InvalidKey : CurseForgeException("CurseForge didn't accept the API key.")
    /** The key works, but CurseForge doesn't let it search. */
    data object SearchNotAllowed : CurseForgeException("CurseForge accepts the API key but doesn't let it search.")
    data class Http(val what: String, val status: Int) : CurseForgeException("$what failed (HTTP $status).")
    data class BadResponse(val what: String) : CurseForgeException("Unexpected response from $what.")
}

/** What installing one CurseForge file brings in: the file plus its required dependencies, minus what the game folder already has from CurseForge. */
data class CurseForgeInstallPlan(
    val items: MutableList<Item> = mutableListOf(),
    /** Required dependencies already installed from CurseForge (mod ids). */
    val alreadyInstalled: MutableList<Int> = mutableListOf(),
    /** Required dependencies with no file for this game version and loader (names). */
    val unavailable: MutableList<String> = mutableListOf(),
    /** Installed mods the new file declares incompatible (names). */
    val conflicts: MutableList<String> = mutableListOf()
) {
    data class Item(val mod: CurseForgeApi.Mod, val file: CurseForgeApi.File, val isDependency: Boolean)

    /** Items whose author doesn't allow downloads in other apps: shown with a link, never fetched. */
    val blocked: List<Item> get() = items.filter { it.file.downloadUrl == null }
    val downloadable: List<Item> get() = items.filter { it.file.downloadUrl != null }
}

/**
 * Which files in a game folder came from CurseForge (mod and file ids), kept in
 * `.voxelium-curseforge.json`. CurseForge files can't be matched by SHA-1 lookup the way Modrinth's
 * can, so Voxelium remembers what it installed.
 */
@Serializable
data class CurseForgeIndex(
    /** By file name, without ".disabled". */
    val files: MutableMap<String, Entry> = mutableMapOf()
) {
    @Serializable
    data class Entry(val modID: Int, val fileID: Int, val kind: String)

    fun save(manager: ContentManager) {
        val target = file(manager)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeText(SAVE_JSON.encodeToString(serializer(), CurseForgeIndex(files.toSortedMap())))
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Mod ids still on disk (switched on or off), mapped to their files. */
    fun installed(manager: ContentManager): Map<Int, List<File>> {
        val out = mutableMapOf<Int, MutableList<File>>()
        for (kind in ContentKind.entries) {
            for (item in manager.list(kind)) {
                val entry = files[item.id] ?: continue
                if (entry.kind != kind.id) continue
                out.getOrPut(entry.modID) { mutableListOf() }.add(item.file)
            }
        }
        return out
    }

    companion object {
        private val SAVE_JSON = Json { prettyPrint = true }

        internal fun file(manager: ContentManager) = File(manager.gameDirectory, ".voxelium-curseforge.json")

        fun load(manager: ContentManager): CurseForgeIndex =
            runCatching { CurseForgeApi.JSON.decodeFromString(serializer(), file(manager).readText()) }.getOrNull() ?: CurseForgeIndex()
    }
}

/** Plans and installs CurseForge content into a game folder. */
class CurseForgeInstaller(val api: CurseForgeApi, val downloader: DownloadManager = DownloadManager()) {
    /** The chosen file plus every required dependency (recursively), each the preferred file for this game version and loader. Dependencies are mods. */
    suspend fun plan(
        file: CurseForgeApi.File, mod: CurseForgeApi.Mod, gameVersion: String, gameLoader: String?, installed: Set<Int>
    ): CurseForgeInstallPlan {
        val plan = CurseForgeInstallPlan()
        val seen = mutableSetOf(mod.id)
        val queue = ArrayDeque(listOf(Triple(mod, file, false)))
        while (queue.isNotEmpty()) {
            val (m, f, isDep) = queue.removeFirst()
            plan.items.add(CurseForgeInstallPlan.Item(m, f, isDep))
            for (dep in f.dependencies) {
                if (dep.relationType != 3 && dep.relationType != 5) continue
                if (dep.relationType == 5) {
                    if (dep.modId in installed) {
                        plan.conflicts.add(runCatching { api.mod(dep.modId).name }.getOrDefault("mod ${dep.modId}"))
                    }
                    continue
                }
                if (dep.modId in seen) continue
                seen.add(dep.modId)
                if (dep.modId in installed) {
                    plan.alreadyInstalled.add(dep.modId)
                    continue
                }
                val depMod = runCatching { api.mod(dep.modId) }.getOrNull()
                if (depMod == null) {
                    plan.unavailable.add("mod ${dep.modId}")
                    continue
                }
                val list = runCatching { api.files(dep.modId, ContentKind.MODS, gameVersion, gameLoader) }.getOrDefault(emptyList())
                val chosen = CurseForgeApi.preferred(list)
                if (chosen == null) {
                    plan.unavailable.add(depMod.name)
                    continue
                }
                queue.addLast(Triple(depMod, chosen, true))
            }
        }
        return plan
    }

    /**
     * Downloads every downloadable planned file into the folder of its kind (the chosen item into
     * [kind], dependencies into mods/), verifying SHA-1s, and records them in the index. Blocked
     * files are skipped. An older file of the same mod is replaced.
     */
    suspend fun install(
        plan: CurseForgeInstallPlan, kind: ContentKind, manager: ContentManager,
        progress: ((DownloadProgress) -> Unit)? = null
    ): DownloadReport {
        val index = CurseForgeIndex.load(manager)
        val installed = index.installed(manager)
        val items = mutableListOf<DownloadItem>()
        val replaced = mutableListOf<File>()
        val added = mutableListOf<Pair<String, CurseForgeIndex.Entry>>()
        for (entry in plan.downloadable) {
            val url = entry.file.downloadUrl ?: continue
            val itemKind = if (entry.isDependency) ContentKind.MODS else kind
            val folder = manager.folder(itemKind)
            folder.mkdirs()
            val name = entry.file.fileName.replace("/", "_")
            val dest = File(folder, name)
            items.add(DownloadItem(url, dest, entry.file.sha1, entry.file.fileLength))
            replaced += (installed[entry.mod.id] ?: emptyList()).filter { it.canonicalPath != dest.canonicalPath }
            added.add(name to CurseForgeIndex.Entry(entry.mod.id, entry.file.id, itemKind.id))
        }
        val report = downloader.run(items, concurrency = 4, progress = progress)
        if (report.succeeded) {
            for (old in replaced) {
                index.files.remove(ContentManager.baseName(old.name))
                old.delete()
            }
            for ((name, entry) in added) index.files[name] = entry
            index.save(manager)
        }
        return report
    }
}
