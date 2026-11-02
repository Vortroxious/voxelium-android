/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from VoxeliumCore/Launch/ForgeInstaller.swift (ForgeMeta) (zalithios e28b28b).
 * The installer itself (ForgeInstaller) is not ported yet: on iOS its processors run in the game's
 * JVM on first launch; Android can run them at install time instead.
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.accounts.HttpClient
import io.github.voxelium.core.accounts.OkHttpJsonClient
import io.github.voxelium.core.accounts.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Forge and NeoForge builds for a Minecraft version.
 *   NeoForge: https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge
 *             (builds are numbered after Minecraft: 1.21.1 → 21.1.x, 26.1.2 → 26.1.2.x)
 *   Forge:    https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
 *             (versions "1.20.1-47.4.10"), recommended builds from promotions_slim.json
 */
class ForgeMeta(private val http: HttpClient = OkHttpJsonClient()) {
    suspend fun versions(loader: ModLoader, minecraft: String): List<LoaderVersion> {
        if (!loader.isAvailable(minecraft)) throw ModLoaderException.NotOffered(loader, minecraft)
        val builds = when (loader) {
            ModLoader.NEOFORGE -> {
                val r = http.get(NEOFORGE_VERSIONS)
                if (r.status != 200) throw ModLoaderException.Http("NeoForge versions", r.status)
                neoForgeBuilds(r.data.toString(Charsets.UTF_8), minecraft)
            }
            ModLoader.FORGE -> {
                val r = http.get(FORGE_METADATA)
                if (r.status != 200) throw ModLoaderException.Http("Forge versions", r.status)
                // Promotions only mark the recommended build; the list works without them.
                val promos = runCatching { http.get(FORGE_PROMOTIONS) }.getOrNull()
                forgeBuilds(
                    r.data.toString(Charsets.UTF_8),
                    promos?.takeIf { it.status == 200 }?.data?.toString(Charsets.UTF_8),
                    minecraft
                )
            }
            else -> throw ModLoaderException.Unsupported(loader)
        }
        if (builds.isEmpty()) throw ModLoaderException.NoBuilds(loader, minecraft)
        return builds
    }

    companion object {
        const val NEOFORGE_VERSIONS = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"
        const val FORGE_METADATA = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
        const val FORGE_PROMOTIONS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"

        /** Forge is offered for Minecraft 1.17 and newer (26.x included). */
        fun offersForge(minecraft: String): Boolean {
            val p = minecraft.split(".").map { it.toIntOrNull() }
            if (p.size < 2) return false
            val major = p[0] ?: return false
            val minor = p[1] ?: return false
            return major > 1 || minor >= 17
        }

        /**
         * The build-number prefix NeoForge uses for a Minecraft version, or null when NeoForge has no
         * builds of its own for it (before 1.20.2; 1.20.1's NeoForge was a Forge fork with Forge's
         * installer and is not offered).
         */
        fun neoForgePrefix(minecraft: String): String? {
            val p = minecraft.split(".")
            if (p.size < 2 || p.any { it.toIntOrNull() == null }) return null
            if (p[0] == "1") {
                val minor = p[1].toInt()
                val patch = if (p.size > 2) p[2].toInt() else 0
                if (!(minor > 20 || (minor == 20 && patch >= 2))) return null
                return "$minor.$patch."
            }
            return "${p[0]}.${p[1]}.${if (p.size > 2) p[2] else "0"}."
        }

        /** Newest first; "-beta"/"-alpha" builds are not stable. */
        fun neoForgeBuilds(json: String, minecraft: String): List<LoaderVersion> {
            val all = runCatching { (Json.parseToJsonElement(json) as JsonObject)["versions"] as JsonArray }.getOrNull()
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?: throw ModLoaderException.BadProfile("unexpected NeoForge version list")
            val prefix = neoForgePrefix(minecraft) ?: return emptyList()
            return all.filter { it.startsWith(prefix) }
                .sortedWith { a, b -> compareVersions(b, a) }
                .map { v -> LoaderVersion(v, stable = listOf("beta", "alpha").none { v.lowercase().contains(it) }) }
        }

        /**
         * Builds for [minecraft] as "47.4.10", newest first. All Forge builds count as stable; the
         * promoted recommended (else latest) one is marked recommended.
         */
        fun forgeBuilds(metadata: String, promotions: String?, minecraft: String): List<LoaderVersion> {
            val builds = Regex("<version>(.*?)</version>").findAll(metadata).map { it.groupValues[1] }
                .filter { it.startsWith("$minecraft-") }
                .map { it.drop(minecraft.length + 1) }
                // Pre-1.13 entries look like "10.13.4.1614-1.7.10"; never for versions we offer.
                .filter { !it.contains("-") }
                .toSet()
            val promos = promotions?.let { runCatching { (Json.parseToJsonElement(it) as JsonObject)["promos"] as JsonObject }.getOrNull() }
            val recommended = promos?.let {
                ((it["$minecraft-recommended"] ?: it["$minecraft-latest"]) as? JsonPrimitive)?.content
            }
            return builds.sortedWith { a, b -> compareVersions(b, a) }
                .map { LoaderVersion(it, stable = true, recommended = it == recommended) }
        }

        /** Compares dotted build numbers numerically ("21.1.100" > "21.1.99"), suffixes last. */
        fun compareVersions(a: String, b: String): Int {
            fun parts(s: String) = s.split("-").first().split(".").map { it.toIntOrNull() ?: 0 }
            val x = parts(a)
            val y = parts(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val l = x.getOrElse(i) { 0 }
                val r = y.getOrElse(i) { 0 }
                if (l != r) return l.compareTo(r)
            }
            // Same numbers: a release sorts above its "-beta".
            if (a.contains("-") != b.contains("-")) return if (a.contains("-")) -1 else 1
            return a.compareTo(b)
        }
    }
}
