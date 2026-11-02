/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/ForgeInstaller.swift (ForgeInstaller) (zalithios e28b28b).
 * Not ported: relocatePlan (iOS moves the app's data folder on reinstall; Android's does not move).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.accounts.HttpClient
import io.github.voxelium.core.accounts.OkHttpJsonClient
import io.github.voxelium.core.accounts.get
import io.github.voxelium.core.content.ZipReader
import io.github.voxelium.core.download.DownloadItem
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.DownloadProgress
import io.github.voxelium.core.download.DownloadReport
import io.github.voxelium.core.download.FileHash
import io.github.voxelium.core.manifest.Library
import io.github.voxelium.core.manifest.MavenCoordinate
import io.github.voxelium.core.manifest.MojangJson
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.manifest.joinUrl
import io.github.voxelium.core.paths.AppPaths
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed class ForgeInstallException(message: String) : Exception(message) {
    data class BadInstaller(val detail: String) : ForgeInstallException("The installer is not usable: $detail")
}

/**
 * Installs a Forge or NeoForge build the way their own installer's "client" mode does, except that
 * the Java processors (binary patcher, remapper, …) are not run here: they are written to a plan.
 * On iOS the plan runs in the game's JVM on first launch; on Android the app runs it at install time
 * in a separate Java process.
 *
 * Writes:
 *   libraries/…/<loader>-<build>-installer.jar   (SHA-1 checked against the Maven .sha1)
 *   libraries/…                                   the processors' jars and the loader's own files
 *                                                 bundled in the installer (maven/…)
 *   versions/<id>/installer-data/…                files the processors read (data/client.lzma)
 *   versions/<id>/voxelium-processors.txt         the processor plan
 *   versions/<id>/<id>.json                       the loader's version JSON (inheritsFrom <mc>)
 */
class ForgeInstaller(
    val paths: AppPaths,
    private val downloader: DownloadManager = DownloadManager(),
    private val http: HttpClient = OkHttpJsonClient()
) {
    data class Result(val id: String, val report: DownloadReport)

    /**
     * Downloads the installer and the processors' libraries, then writes the plan and the version
     * JSON. Returns the version id (e.g. "neoforge-21.11.48", "1.20.1-forge-47.4.10") and the
     * download report of those files; the version's own libraries are downloaded afterwards by
     * [GameInstaller] like any version's.
     */
    suspend fun installProfile(
        loader: ModLoader, minecraft: String, build: String, concurrency: Int = 8,
        progress: ((DownloadProgress) -> Unit)? = null
    ): Result {
        // Also covers modpacks, which install their loader through here.
        if (!loader.isAvailable(minecraft)) throw ModLoaderException.NotOffered(loader, minecraft)
        if (!isSafeBuild(build)) throw ModLoaderException.BadProfile("unexpected build $build")
        val coord = installerCoordinate(loader, minecraft, build)
        val installerUrl = joinUrl(repository(loader), coord.path)
        val installer = paths.library(coord.path)
        val sha1 = fetchSha1(installerUrl)
        val first = downloader.run(listOf(DownloadItem(installerUrl, installer, sha1, null)), concurrency = 1)
        if (!first.succeeded) return Result("", first)

        ZipReader(installer).use { zip ->
            val profile = zip.read("install_profile.json")?.let { runCatching { Json.parseToJsonElement(it.toString(Charsets.UTF_8)) as? JsonObject }.getOrNull() }
                ?: throw ForgeInstallException.BadInstaller("no install_profile.json")
            if (profile["install"] != null || profile["versionInfo"] != null) throw ForgeInstallException.BadInstaller("old installer format")
            val jsonPath = ((profile["json"] as? JsonPrimitive)?.content ?: "/version.json").removePrefix("/")
            val versionData = zip.read(jsonPath) ?: throw ForgeInstallException.BadInstaller("no $jsonPath")
            val version = runCatching { VersionInfo.decode(versionData.toString(Charsets.UTF_8)) }
                .getOrElse { throw ModLoaderException.BadProfile(it.toString()) }
            if (version.inheritsFrom != minecraft) {
                throw ModLoaderException.BadProfile("it builds on ${version.inheritsFrom ?: "nothing"}, not $minecraft")
            }
            val versionDir = paths.versionDir(version.id) // rejects ids that escape versions/
            versionDir.mkdirs()

            // Libraries: the processors' (install profile) and the loader's (version JSON). Those
            // without a URL are inside the installer under maven/, or are made by the processors.
            val installLibs = decodeLibraries(profile["libraries"])
            val items = LinkedHashSet<DownloadItem>()
            for (lib in installLibs + version.libraries) {
                val a = lib.artifact ?: continue
                val path = a.path ?: lib.coordinate?.path ?: continue
                val dest = paths.library(path)
                if (a.isProducedByInstaller) {
                    if (zip.hasEntry("maven/$path")) extract(zip, "maven/$path", dest, a.sha1)
                } else if (lib in installLibs) {
                    items.add(DownloadItem(a.url, dest, a.sha1, a.size))
                }
            }
            val report = downloader.run(items.toList(), concurrency, progress)
            if (!report.succeeded) return Result(version.id, report)

            val plan = processorPlan(profile, zip, installer, minecraft, versionDir)
            val planFile = File(versionDir, PLAN_FILE_NAME)
            File(planFile.path + ".done").delete()
            writeAtomically(planFile, plan.toByteArray())
            writeAtomically(paths.versionJson(version.id), versionData)
            return Result(version.id, report)
        }
    }

    /** Maven publishes `<file>.sha1` next to each file; the installer is checked against it. */
    private suspend fun fetchSha1(url: String): String {
        val r = http.get("$url.sha1")
        val text = if (r.status == 200) r.data.toString(Charsets.UTF_8).trim().split(Regex("\\s+")).firstOrNull() else null
        if (text == null || text.length != 40 || !text.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            throw ModLoaderException.Http("Installer checksum", r.status)
        }
        return text.lowercase()
    }

    private fun extract(zip: ZipReader, name: String, dest: File, sha1: String?) {
        val data = zip.read(name, limit = 512L shl 20) ?: throw ForgeInstallException.BadInstaller("no $name")
        if (!sha1.isNullOrEmpty() && FileHash.sha1(data) != sha1.lowercase()) {
            throw ForgeInstallException.BadInstaller("$name does not match its checksum")
        }
        writeAtomically(dest, data)
    }

    /**
     * Resolves the client processors into the plan format, following the installer's rules:
     * `[group:artifact:version]` is a library path, `'text'` a literal, `/path` a file extracted from
     * the installer, `{KEY}` a data value.
     */
    private fun processorPlan(profile: JsonObject, zip: ZipReader, installer: File, minecraft: String, versionDir: File): String {
        fun library(name: String): String {
            val c = MavenCoordinate.parse(name) ?: throw ForgeInstallException.BadInstaller("bad artifact $name")
            return paths.library(c.path).path
        }
        val dataDir = File(versionDir, "installer-data")
        val data = mutableMapOf(
            "SIDE" to "client",
            "MINECRAFT_JAR" to paths.clientJar(minecraft).path,
            "MINECRAFT_VERSION" to minecraft,
            "ROOT" to paths.gameDir.path,
            "INSTALLER" to installer.path,
            "LIBRARY_DIR" to paths.librariesDir.path
        )
        for ((key, sides) in (profile["data"] as? JsonObject) ?: JsonObject(emptyMap())) {
            val value = ((sides as? JsonObject)?.get("client") as? JsonPrimitive)?.content ?: continue
            if (value.isEmpty()) continue
            data[key] = when {
                value.startsWith("[") && value.endsWith("]") -> library(value.drop(1).dropLast(1))
                value.startsWith("'") && value.endsWith("'") && value.length >= 2 -> value.drop(1).dropLast(1)
                value.startsWith("/") -> {
                    val dest = paths.resolve(value.drop(1), dataDir)
                    extract(zip, value.drop(1), dest, null)
                    dest.path
                }
                else -> value
            }
        }
        fun resolve(arg: String): String {
            if (arg.startsWith("[") && arg.endsWith("]")) return library(arg.drop(1).dropLast(1))
            val out = StringBuilder()
            var rest = arg
            while (true) {
                val open = rest.indexOf('{')
                if (open < 0) break
                out.append(rest, 0, open)
                val close = rest.indexOf('}', open)
                if (close < 0) {
                    out.append(rest, open, rest.length)
                    rest = ""
                    break
                }
                val key = rest.substring(open + 1, close)
                out.append(data[key] ?: throw ForgeInstallException.BadInstaller("unknown value {$key}"))
                rest = rest.substring(close + 1)
            }
            out.append(rest)
            var result = out.toString()
            // Output checksums are written as 'sha1' literals.
            if (result.startsWith("'") && result.endsWith("'") && result.length >= 2) result = result.drop(1).dropLast(1)
            return result
        }

        val lines = mutableListOf<String>()
        for (p in (profile["processors"] as? JsonArray) ?: JsonArray(emptyList())) {
            val processor = p as? JsonObject ?: continue
            val sides = (processor["sides"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
            if (sides != null && "client" !in sides) continue
            val jar = (processor["jar"] as? JsonPrimitive)?.content ?: throw ForgeInstallException.BadInstaller("processor without a jar")
            val args = strings(processor["args"]).map { resolve(it) }
            var name = MavenCoordinate.parse(jar)?.artifact ?: jar
            val task = args.indexOf("--task")
            if (task >= 0 && task + 1 < args.size) name += " " + args[task + 1]
            lines.add("processor $name")
            lines.add("jar ${library(jar)}")
            for (cp in strings(processor["classpath"])) lines.add("cp ${library(cp)}")
            for (a in args) lines.add("arg $a")
            val outputs = (processor["outputs"] as? JsonObject)?.mapValues { (it.value as? JsonPrimitive)?.content ?: "" } ?: emptyMap()
            for ((k, v) in outputs.toSortedMap()) {
                val sha = resolve(v)
                lines.add("out ${resolve(k)} ${sha.ifEmpty { "-" }}")
            }
            lines.add("end")
        }
        lines.firstOrNull { it.contains("\n") || it.contains("\r") }?.let {
            throw ForgeInstallException.BadInstaller("line break in ${it.take(60)}")
        }
        return lines.joinToString("\n") + "\n"
    }

    companion object {
        const val PLAN_FILE_NAME = "voxelium-processors.txt"

        /** The processor plan for a version, if it is a Forge/NeoForge install. */
        fun processorPlan(id: String, paths: AppPaths): File? =
            runCatching { File(paths.versionDir(id), PLAN_FILE_NAME) }.getOrNull()?.takeIf { it.exists() }

        /** True once the processors ran successfully (the runner writes `<plan>.done`). */
        fun processorsDone(plan: File): Boolean = File(plan.path + ".done").exists()

        internal fun installerCoordinate(loader: ModLoader, minecraft: String, build: String): MavenCoordinate = when (loader) {
            ModLoader.NEOFORGE -> MavenCoordinate.parse("net.neoforged:neoforge:$build:installer")!!
            ModLoader.FORGE -> MavenCoordinate.parse("net.minecraftforge:forge:$minecraft-$build:installer")!!
            else -> throw ModLoaderException.Unsupported(loader)
        }

        internal fun repository(loader: ModLoader): String =
            if (loader == ModLoader.NEOFORGE) "https://maven.neoforged.net/releases/" else "https://maven.minecraftforge.net/"

        internal fun isSafeBuild(b: String): Boolean = b.isNotEmpty() && b.all { it.isLetterOrDigit() || it in ".-+_" }

        internal fun decodeLibraries(raw: JsonElement?): List<Library> =
            if (raw == null) emptyList() else MojangJson.decodeFromJsonElement(ListSerializer(Library.serializer()), raw)

        private fun strings(element: JsonElement?): List<String> =
            (element as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

        private fun writeAtomically(file: File, data: ByteArray) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.writeBytes(data)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
