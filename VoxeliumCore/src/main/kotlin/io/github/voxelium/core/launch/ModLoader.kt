/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/ModLoaders.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.accounts.HttpClient
import io.github.voxelium.core.accounts.OkHttpJsonClient
import io.github.voxelium.core.accounts.get
import io.github.voxelium.core.manifest.MavenCoordinate
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.manifest.joinUrl
import io.github.voxelium.core.paths.AppPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class ModLoader(val id: String, val displayName: String) {
    VANILLA("vanilla", "Vanilla"),
    FABRIC("fabric", "Fabric"),
    QUILT("quilt", "Quilt"),
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge");

    /**
     * Whether Voxelium offers this loader for a Minecraft version. Forge is offered from 1.17
     * (older Forge is rarely played and is not tested), NeoForge from 1.20.2, its first own builds.
     */
    fun isAvailable(minecraft: String): Boolean = when (this) {
        VANILLA, FABRIC, QUILT -> true
        FORGE -> ForgeMeta.offersForge(minecraft)
        NEOFORGE -> ForgeMeta.neoForgePrefix(minecraft) != null
    }

    /** Shown when [isAvailable] is false. */
    val oldestMinecraft: String?
        get() = when (this) {
            FORGE -> "1.17"
            NEOFORGE -> "1.20.2"
            else -> null
        }

    /** Forge and NeoForge (installer + processors) rather than a plain profile. */
    val usesForgeInstaller: Boolean get() = this == FORGE || this == NEOFORGE

    companion object {
        fun fromId(id: String?): ModLoader? = entries.firstOrNull { it.id == id }
    }
}

/** A loader build offered for one Minecraft version. */
data class LoaderVersion(
    val version: String,
    /** Fabric marks builds stable; Quilt has no flag, so non-beta/rc/pre builds count. */
    val stable: Boolean,
    /** Forge's promoted "recommended" build. */
    val recommended: Boolean = false
) {
    val id: String get() = version
}

sealed class ModLoaderException(message: String) : Exception(message) {
    data class Unsupported(val loader: ModLoader) : ModLoaderException("${loader.displayName} is not supported yet.")
    data class NotOffered(val loader: ModLoader, val minecraft: String) :
        ModLoaderException("Voxelium offers ${loader.displayName} for Minecraft ${loader.oldestMinecraft ?: "?"} and newer, not $minecraft.")
    data class NoBuilds(val loader: ModLoader, val minecraft: String) :
        ModLoaderException("${loader.displayName} has no builds for Minecraft $minecraft.")
    data class Http(val what: String, val status: Int) : ModLoaderException("$what failed (HTTP $status).")
    data class BadProfile(val detail: String) : ModLoaderException("The loader profile is not usable: $detail")
}

/**
 * Fabric and Quilt metadata: the loader builds for a Minecraft version, and the launcher profile
 * (a version JSON with `inheritsFrom`) for one build.
 *   Fabric: https://meta.fabricmc.net/v2/versions/loader/<mc>[/<loader>/profile/json]
 *   Quilt:  https://meta.quiltmc.org/v3/versions/loader/<mc>[/<loader>/profile/json]
 */
class ModLoaderMeta(val http: HttpClient = OkHttpJsonClient()) {
    /**
     * Loader builds for [minecraft], newest first. Android addition: sorted here, because Quilt's
     * meta server does not list them in order (for 1.21.1 it starts with 0.20.0-beta.9, then 0.24.0,
     * while 0.30.2 is near the end), so "the first stable build" was an old one.
     */
    suspend fun versions(loader: ModLoader, minecraft: String): List<LoaderVersion> {
        if (loader.usesForgeInstaller) return ForgeMeta(http).versions(loader, minecraft)
        val r = http.get("${base(loader)}/${escape(minecraft)}")
        if (r.status != 200) throw ModLoaderException.Http("${loader.displayName} versions", r.status)
        val list = runCatching { Json.parseToJsonElement(r.data.toString(Charsets.UTF_8)) as JsonArray }.getOrNull()
            ?: throw ModLoaderException.BadProfile("unexpected ${loader.displayName} version list")
        val builds = list.mapNotNull { entry ->
            val l = (entry as? JsonObject)?.get("loader") as? JsonObject ?: return@mapNotNull null
            val v = (l["version"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val stable = (l["stable"] as? JsonPrimitive)?.booleanOrNull
                ?: listOf("beta", "rc", "pre", "alpha").none { v.lowercase().contains(it) }
            LoaderVersion(v, stable)
        }
        if (builds.isEmpty()) throw ModLoaderException.NoBuilds(loader, minecraft)
        return builds.sortedWith { a, b -> compareVersions(b.version, a.version) }
    }

    /** The raw profile JSON for one build. */
    suspend fun profile(loader: ModLoader, minecraft: String, version: String): ByteArray {
        val r = http.get("${base(loader)}/${escape(minecraft)}/${escape(version)}/profile/json")
        if (r.status != 200) throw ModLoaderException.Http("${loader.displayName} profile", r.status)
        return r.data
    }

    companion object {
        internal fun base(loader: ModLoader): String = when (loader) {
            ModLoader.FABRIC -> "https://meta.fabricmc.net/v2/versions/loader"
            ModLoader.QUILT -> "https://meta.quiltmc.org/v3/versions/loader"
            else -> throw ModLoaderException.Unsupported(loader)
        }

        /** Percent-encodes a path segment (everything but letters, digits and `-._~+`). */
        internal fun escape(s: String): String = buildString {
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xff
                val ch = c.toChar()
                if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-._~+")) append(ch) else append("%%%02X".format(c))
            }
        }

        /**
         * Orders loader versions like `0.30.2`, `0.30.0-beta.8`: numbers part by part, and a
         * pre-release (`-beta.8`, `-rc.1`) before its release. Parts that aren't numbers compare as text.
         */
        fun compareVersions(a: String, b: String): Int {
            fun split(v: String) = v.split('-', limit = 2).let { it[0] to it.getOrNull(1) }
            fun parts(cmpA: List<String>, cmpB: List<String>): Int {
                for (i in 0 until maxOf(cmpA.size, cmpB.size)) {
                    val x = cmpA.getOrNull(i) ?: return -1
                    val y = cmpB.getOrNull(i) ?: return 1
                    val xn = x.toLongOrNull()
                    val yn = y.toLongOrNull()
                    val c = if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
                    if (c != 0) return c
                }
                return 0
            }
            val (releaseA, preA) = split(a)
            val (releaseB, preB) = split(b)
            val c = parts(releaseA.split('.'), releaseB.split('.'))
            if (c != 0) return c
            return when {
                preA == null && preB == null -> 0
                preA == null -> 1
                preB == null -> -1
                else -> parts(preA.split('.'), preB.split('.'))
            }
        }

        /** The default build to offer: the recommended one (Forge), else the first stable one, else the newest. */
        fun recommended(builds: List<LoaderVersion>): LoaderVersion? =
            builds.firstOrNull { it.recommended } ?: builds.firstOrNull { it.stable } ?: builds.firstOrNull()
    }
}

/**
 * Installs a Fabric or Quilt profile next to its Minecraft version. The base version's files and the
 * loader's libraries are then downloaded by [GameInstaller] from the merged version (see [VersionResolver]).
 */
class ModLoaderInstaller(val paths: AppPaths, private val meta: ModLoaderMeta = ModLoaderMeta()) {
    /** Writes `versions/<id>/<id>.json` for the loader build and returns the profile (its id is e.g. `fabric-loader-0.19.5-1.21.11`). */
    suspend fun installProfile(loader: ModLoader, minecraft: String, version: String): VersionInfo {
        if (loader != ModLoader.FABRIC && loader != ModLoader.QUILT) throw ModLoaderException.Unsupported(loader)
        val text = completeChecksums(meta.profile(loader, minecraft, version).toString(Charsets.UTF_8))
        val profile = runCatching { VersionInfo.decode(text) }.getOrElse { throw ModLoaderException.BadProfile(it.toString()) }
        if (profile.inheritsFrom != minecraft) {
            throw ModLoaderException.BadProfile("it builds on ${profile.inheritsFrom ?: "nothing"}, not $minecraft")
        }
        val file = paths.versionJson(profile.id) // rejects ids that escape versions/
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return profile
    }

    /**
     * Fabric's profiles give SHA-1s for some libraries but not its own loader and intermediary jars,
     * and Quilt's give none. Like desktop launchers, fetch the `<jar>.sha1` file each Maven repository
     * publishes and store it in the profile, so every loader file is verified on download. Libraries
     * whose checksum cannot be fetched are left as they are.
     */
    internal suspend fun completeChecksums(text: String): String {
        val json = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return text
        val libs = json["libraries"] as? JsonArray ?: return text
        var changed = false
        val updated = libs.map { element ->
            val lib = element as? JsonObject ?: return@map element
            if (lib["sha1"] != null || lib["downloads"] != null) return@map element
            val name = (lib["name"] as? JsonPrimitive)?.content ?: return@map element
            val base = (lib["url"] as? JsonPrimitive)?.content ?: return@map element
            val coord = MavenCoordinate.parse(name) ?: return@map element
            val r = runCatching { meta.http.get(joinUrl(base, coord.path + ".sha1")) }.getOrNull() ?: return@map element
            if (r.status != 200) return@map element
            val sha1 = r.data.toString(Charsets.UTF_8).trim().split(Regex("\\s+")).firstOrNull() ?: return@map element
            if (sha1.length != 40 || !sha1.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return@map element
            changed = true
            JsonObject(lib + ("sha1" to JsonPrimitive(sha1.lowercase())))
        }
        if (!changed) return text
        return PRETTY.encodeToString(JsonObject.serializer(), JsonObject(json + ("libraries" to JsonArray(updated))))
    }

    private companion object {
        val PRETTY = Json { prettyPrint = true }
    }
}
