/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/CurseForgeTests.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.MockTransport
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.accounts.HttpHeaders
import io.github.voxelium.core.accounts.MockHttp
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.FileHash
import io.github.voxelium.core.download.RetryPolicy
import io.github.voxelium.core.logging.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.time.Duration

class CurseForgeTests {
    companion object {
        const val API = CurseForgeApi.BASE
        const val KEY = "test-key-not-real"

        fun mod(id: Int, name: String, allow: Boolean = true): JsonObject = buildJsonObject {
            put("id", id); put("name", name); put("slug", name.lowercase()); put("summary", "$name summary")
            put("downloadCount", 1234567.0)
            putJsonObject("links") { put("websiteUrl", "https://www.curseforge.com/minecraft/mc-mods/${name.lowercase()}") }
            putJsonArray("authors") { addJsonObject { put("name", "Author of $name") } }
            putJsonObject("logo") { put("thumbnailUrl", "https://media.forgecdn.net/$id.png") }
            put("classId", 6); put("allowModDistribution", allow)
        }

        fun file(
            id: Int, mod: Int, name: String, release: Int = 1, url: String? = "default",
            deps: List<Pair<Int, Int>> = emptyList(), sha1: String = "da39a3ee5e6b4b0d3255bfef95601890afd80709", length: Long = 2048
        ): JsonObject = buildJsonObject {
            put("id", id); put("modId", mod); put("displayName", name); put("fileName", "$name.jar")
            put("releaseType", release); put("fileLength", length)
            putJsonArray("hashes") {
                addJsonObject { put("value", sha1); put("algo", 1) }
                addJsonObject { put("value", "x"); put("algo", 2) }
            }
            putJsonArray("gameVersions") { add(JsonPrimitive("1.21.11")); add(JsonPrimitive("Fabric")) }
            putJsonArray("dependencies") { deps.forEach { (m, r) -> addJsonObject { put("modId", m); put("relationType", r) } } }
            if (url != null) put("downloadUrl", if (url == "default") "https://edge.forgecdn.net/files/$id/$name.jar" else url)
        }

        fun page(data: List<JsonObject>, total: Int? = null): String = buildJsonObject {
            putJsonArray("data") { data.forEach { add(it) } }
            putJsonObject("pagination") {
                put("index", 0); put("pageSize", 20); put("resultCount", data.size); put("totalCount", total ?: data.size)
            }
        }.toString()

        fun one(data: JsonObject): String = JsonObject(mapOf("data" to data)).toString()
    }

    private inline fun <reified T : Throwable> expectThrows(expected: T, crossinline block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected $expected")
        } catch (e: Throwable) {
            assertEquals(expected, e)
        }
    }

    @Test
    fun searchSendsTheKeyAndFilters() = runBlocking {
        HttpHeaders.userAgent = "Voxelium/test"
        val http = MockHttp()
        http.on("$API/mods/search", 200, page(listOf(mod(1001, "Sodium")), total = 57))
        val api = CurseForgeApi(KEY, http)
        val result = api.search("sodium", ContentKind.MODS, "1.21.11", "quilt", sort = CurseForgeApi.Sort.DOWNLOADS)
        assertEquals("Sodium", result.mods.first().name)
        assertEquals(57, result.totalCount)
        assertEquals("www.curseforge.com", result.mods.first().websiteUrl?.toHttpUrl()?.host)

        val request = http.requests.first()
        assertEquals(KEY, request.headers["x-api-key"])
        assertTrue(request.headers["User-Agent"]?.startsWith("Voxelium/") == true)
        val url = request.url.toHttpUrl()
        fun q(n: String) = url.queryParameter(n)
        assertEquals("432", q("gameId")); assertEquals("6", q("classId")); assertEquals("1.21.11", q("gameVersion"))
        assertEquals("sodium", q("searchFilter")); assertEquals("6", q("sortField")); assertEquals("desc", q("sortOrder"))
        // Quilt searches Fabric mods.
        assertEquals("4", q("modLoaderType"))
    }

    @Test
    fun classesAndLoaders() {
        assertEquals(6, CurseForgeApi.classId(ContentKind.MODS))
        assertEquals(12, CurseForgeApi.classId(ContentKind.RESOURCE_PACKS))
        assertEquals(6552, CurseForgeApi.classId(ContentKind.SHADER_PACKS))
        assertEquals(listOf(1), CurseForgeApi.loaderTypes(ContentKind.MODS, "forge"))
        assertEquals(listOf(6), CurseForgeApi.loaderTypes(ContentKind.MODS, "neoforge"))
        assertEquals(listOf(5, 4), CurseForgeApi.loaderTypes(ContentKind.MODS, "quilt"))
        assertEquals(emptyList<Int>(), CurseForgeApi.loaderTypes(ContentKind.RESOURCE_PACKS, "fabric"))
        assertEquals(emptyList<Int>(), CurseForgeApi.loaderTypes(ContentKind.SHADER_PACKS, "fabric"))
    }

    @Test
    fun rejectedKey() {
        val http = MockHttp()
        http.on("$API/games/432", 403, """{"error":"Forbidden"}""")
        expectThrows(CurseForgeException.InvalidKey) { CurseForgeApi("wrong", http).checkKey() }
    }

    /** A key CurseForge accepts everywhere but search (403 on /mods/search only). */
    @Test
    fun searchRefusedForAWorkingKey() {
        val http = MockHttp()
        http.on("$API/mods/search", 403, """{"error":"Forbidden"}""")
        http.on("$API/games/432", 200, """{"data":{"id":432,"name":"Minecraft"}}""")
        expectThrows(CurseForgeException.SearchNotAllowed) {
            CurseForgeApi(KEY, http).search("", ContentKind.MODS, "1.21.11", "fabric")
        }

        val wrong = MockHttp()
        wrong.on("$API/mods/search", 403, """{"error":"Forbidden"}""")
        wrong.on("$API/games/432", 403, """{"error":"Forbidden"}""")
        expectThrows(CurseForgeException.InvalidKey) {
            CurseForgeApi("wrong", wrong).search("", ContentKind.MODS, "1.21.11", "fabric")
        }
    }

    @Test
    fun filesNewestFirstAndPreferredRelease() = runBlocking {
        val http = MockHttp()
        http.on(
            "$API/mods/1001/files", 200, page(
                listOf(
                    file(500, mod = 1001, name = "sodium-0.8.1"),
                    file(520, mod = 1001, name = "sodium-0.8.2-beta", release = 2),
                    file(510, mod = 1001, name = "sodium-0.8.2")
                )
            )
        )
        val files = CurseForgeApi(KEY, http).files(1001, ContentKind.MODS, "1.21.11", "fabric")
        assertEquals(listOf(520, 510, 500), files.map { it.id })
        assertEquals(510, CurseForgeApi.preferred(files)?.id)
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", files[1].sha1)
    }

    /** A mod needing Fabric API (required) and incompatible with an installed mod, with one dependency whose author blocks downloads in other apps. */
    @Test
    fun planFollowsDependenciesAndKeepsBlockedFilesOut() = runBlocking {
        val http = MockHttp()
        http.on("$API/mods/2002", 200, one(mod(2002, "FabricAPI")))
        http.on("$API/mods/2002/files", 200, page(listOf(file(700, mod = 2002, name = "fabric-api-0.140"))))
        http.on("$API/mods/3003", 200, one(mod(3003, "Blocked", allow = false)))
        http.on("$API/mods/3003/files", 200, page(listOf(file(800, mod = 3003, name = "blocked-lib", url = null))))
        http.on("$API/mods/4004", 200, one(mod(4004, "OldThing")))
        val api = CurseForgeApi(KEY, http)
        val root = CurseForgeApi.JSON.decodeFromString(CurseForgeApi.Mod.serializer(), mod(1001, "Cool").toString())
        val rootFile = CurseForgeApi.JSON.decodeFromString(
            CurseForgeApi.File.serializer(),
            file(600, mod = 1001, name = "cool-1.0", deps = listOf(2002 to 3, 3003 to 3, 4004 to 5, 5005 to 2)).toString()
        )

        val plan = CurseForgeInstaller(api).plan(rootFile, root, "1.21.11", "fabric", installed = setOf(4004))
        assertEquals(listOf("Cool", "FabricAPI", "Blocked"), plan.items.map { it.mod.name })
        assertEquals(listOf(false, true, true), plan.items.map { it.isDependency })
        assertEquals(listOf("OldThing"), plan.conflicts)
        assertEquals(listOf("Blocked"), plan.blocked.map { it.mod.name })
        assertEquals(2, plan.downloadable.size)
        // The optional dependency (5005) is not asked for.
        assertTrue(http.requests.none { it.url.contains("5005") })
    }

    @Test
    fun installRecordsFilesAndReplacesOldOnes() = runBlocking {
        TempDir().use { tmp ->
            val manager = ContentManager(tmp.dir)
            val mods = manager.folder(ContentKind.MODS)
            mods.mkdirs()
            // An older Cool from CurseForge, switched off.
            File(mods, "cool-0.9.jar.disabled").writeText("old")
            val index = CurseForgeIndex()
            index.files["cool-0.9.jar"] = CurseForgeIndex.Entry(modID = 1001, fileID = 590, kind = "mods")
            index.save(manager)
            assertEquals(1, CurseForgeIndex.load(manager).installed(manager)[1001]?.size)

            val body = "new jar".toByteArray()
            val transport = MockTransport()
            transport.enqueue("https://edge.forgecdn.net/files/600/cool-1.0.jar", MockTransport.Response.Ok(body))
            val f = CurseForgeApi.JSON.decodeFromString(
                CurseForgeApi.File.serializer(),
                file(600, mod = 1001, name = "cool-1.0", sha1 = FileHash.sha1(body), length = body.size.toLong()).toString()
            )
            val root = CurseForgeApi.JSON.decodeFromString(CurseForgeApi.Mod.serializer(), mod(1001, "Cool").toString())
            val plan = CurseForgeInstallPlan()
            plan.items += CurseForgeInstallPlan.Item(root, f, false)

            val installer = CurseForgeInstaller(
                CurseForgeApi(KEY, MockHttp()),
                DownloadManager(transport = transport, retry = RetryPolicy(maxAttempts = 1, baseDelay = Duration.ZERO), log = Logger(), sleep = {})
            )
            val report = installer.install(plan, ContentKind.MODS, manager)
            assertTrue(report.succeeded)
            assertTrue(File(mods, "cool-1.0.jar").exists())
            assertTrue(!File(mods, "cool-0.9.jar.disabled").exists())
            val saved = CurseForgeIndex.load(manager)
            assertEquals(listOf("cool-1.0.jar"), saved.files.keys.sorted())
            assertEquals(CurseForgeIndex.Entry(modID = 1001, fileID = 600, kind = "mods"), saved.files["cool-1.0.jar"])
        }
    }
}
