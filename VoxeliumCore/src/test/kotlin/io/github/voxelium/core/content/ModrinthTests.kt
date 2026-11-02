/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/ModrinthTests.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.MockTransport
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.accounts.HttpHeaders
import io.github.voxelium.core.accounts.MockHttp
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.FileHash
import io.github.voxelium.core.download.RetryPolicy
import io.github.voxelium.core.logging.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.time.Duration

class ModrinthTests {
    companion object {
        const val API = "https://api.modrinth.com/v2"
    }

    private fun text(name: String) = Fixture.file("modrinth/$name.json").readText()
    private fun json(name: String): JsonElement = Json.parseToJsonElement(text(name))
    private fun versions(name: String) = ModrinthApi.JSON.decodeFromString(ListSerializer(ModrinthApi.Version.serializer()), text(name))

    @Test
    fun searchBuildsFacetsAndDecodes() = runBlocking {
        HttpHeaders.userAgent = "Voxelium/test"
        val http = MockHttp()
        http.on("$API/search", 200, text("search-iris-fabric-1.21.11"))
        val page = ModrinthApi(http).search("iris", ContentKind.MODS, "1.21.11", "quilt")
        assertEquals("Iris Shaders", page.hits.first().title)
        assertEquals("iris", page.hits.first().slug)
        assertEquals(21, page.totalHits)
        val url = http.requests.first().url.toHttpUrl()
        // Quilt also runs Fabric mods.
        assertEquals("""[["project_type:mod"],["versions:1.21.11"],["categories:quilt","categories:fabric"]]""", url.queryParameter("facets"))
        assertTrue(http.requests.first().headers["User-Agent"]?.startsWith("Voxelium/") == true)

        // Shader packs ask for Iris-compatible ones; resource packs have no loader facet.
        assertEquals(listOf("iris"), ModrinthApi.loaders(ContentKind.SHADER_PACKS, "fabric"))
        assertEquals(listOf("minecraft"), ModrinthApi.loaders(ContentKind.RESOURCE_PACKS, "fabric"))
    }

    /** Iris 1.10.8 requires Sodium pinned to one build (0.8.14), although a newer beta (0.8.15-beta.1) exists: the pinned build must win. */
    private fun irisHttp() = MockHttp().apply {
        on("$API/version/rkdTcxoT", 200, text("version-rkdTcxoT"))
        on("$API/project/AANobbMI", 200, text("project-sodium"))
        on("$API/project/AANobbMI/version", 200, text("dep-sodium-versions"))
    }

    @Test
    fun plansPinnedRequiredDependencies() = runBlocking {
        val iris = versions("iris-versions-fabric-1.21.11").first()
        assertEquals("1.10.8+1.21.11-fabric", iris.versionNumber)
        assertEquals("acfbe6d065b6c35d85c14da04d35e073363f3acf", iris.primaryFile?.hashes?.sha1)
        val installer = ModrinthInstaller(ModrinthApi(irisHttp()))
        val plan = installer.plan(iris, "Iris Shaders", ContentKind.MODS, "1.21.11", "fabric", emptySet())
        assertEquals(listOf("Iris Shaders", "Sodium"), plan.items.map { it.title })
        assertEquals(listOf(false, true), plan.items.map { it.isDependency })
        assertEquals("mc1.21.11-0.8.14-fabric", plan.items[1].version.versionNumber) // pinned, not the newer beta
        assertTrue(plan.alreadyInstalled.isEmpty() && plan.unavailable.isEmpty() && plan.conflicts.isEmpty())

        // Already-installed dependencies are skipped.
        val again = installer.plan(iris, "Iris Shaders", ContentKind.MODS, "1.21.11", "fabric", setOf("AANobbMI"))
        assertEquals(listOf("Iris Shaders"), again.items.map { it.title })
        assertEquals(listOf("AANobbMI"), again.alreadyInstalled)
        assertEquals("mc1.21.11-0.8.14-fabric", ModrinthApi.preferred(versions("dep-sodium-versions"))?.versionNumber)
    }

    @Test
    fun findsInstalledProjectsByHash() = runBlocking {
        TempDir().use { tmp ->
            val cm = ContentManager(tmp.dir)
            val installed = cm.add(Fixture.file("content/fabric-example-1.2.3.jar"), ContentKind.MODS)
            val sha1 = FileHash.sha1(installed.file)
            // The lookup answers only for known hashes; map ours to Iris's version.
            val iris = (json("version-files-lookup") as JsonObject).values.first()
            val http = MockHttp()
            http.on("$API/version_files", 200, JsonObject(mapOf(sha1 to iris)).toString())
            val found = ModrinthInstaller(ModrinthApi(http)).installedProjects(cm, ContentKind.MODS)
            assertEquals(listOf("fabric-example-1.2.3.jar"), found["YL57xq9U"]?.map { it.name })
            val body = http.requests.first().bodyText
            assertTrue(body.contains(sha1) && body.contains("\"algorithm\":\"sha1\""))
        }
    }

    @Test
    fun installsVerifiedFilesAndReplacesOldOnes() = runBlocking {
        TempDir().use { tmp ->
            val cm = ContentManager(tmp.dir)
            val modBytes = "new mod".toByteArray()
            val depBytes = "dependency".toByteArray()
            val packBytes = "pack".toByteArray()
            fun version(id: String, project: String, file: String, bytes: ByteArray) = ModrinthApi.Version(
                id = id, projectId = project, name = id, versionNumber = "1", versionType = "release",
                files = listOf(
                    ModrinthApi.Version.File(
                        "https://cdn.example/$file", file, true, bytes.size.toLong(),
                        ModrinthApi.Version.File.Hashes(FileHash.sha1(bytes))
                    )
                ),
                dependencies = emptyList(), loaders = listOf("iris"), gameVersions = listOf("1.21.11")
            )
            fun downloader(t: MockTransport) =
                DownloadManager(transport = t, retry = RetryPolicy(maxAttempts = 1, baseDelay = Duration.ZERO), log = Logger(), sleep = {})

            // An older copy of the shader pack project is installed and must be replaced.
            val old = File(cm.folder(ContentKind.SHADER_PACKS), "old-pack.zip")
            old.parentFile.mkdirs()
            old.writeText("old")

            val transport = MockTransport()
            transport.enqueue("https://cdn.example/new-pack.zip", MockTransport.Response.Ok(packBytes))
            transport.enqueue("https://cdn.example/iris.jar", MockTransport.Response.Ok(depBytes))
            val plan = ModrinthInstallPlan()
            plan.items += ModrinthInstallPlan.Item("PACK", "Pack", version("v1", "PACK", "new-pack.zip", packBytes), false)
            plan.items += ModrinthInstallPlan.Item("IRIS", "Iris", version("v2", "IRIS", "iris.jar", depBytes), true)
            val installer = ModrinthInstaller(ModrinthApi(MockHttp()), downloader(transport))
            val report = installer.install(plan, ContentKind.SHADER_PACKS, cm, replacing = mapOf("PACK" to listOf(old)))
            assertTrue(report.succeeded)
            assertArrayEquals(packBytes, File(cm.folder(ContentKind.SHADER_PACKS), "new-pack.zip").readBytes())
            assertArrayEquals(depBytes, File(cm.folder(ContentKind.MODS), "iris.jar").readBytes()) // dependency → mods/
            assertFalse(old.exists())

            // A bad checksum fails the install and keeps what was there.
            val keep = File(cm.folder(ContentKind.MODS), "keep.jar").apply { writeText("keep") }
            val bad = MockTransport()
            bad.enqueue("https://cdn.example/mod.jar", MockTransport.Response.Ok("tampered".toByteArray()))
            val badPlan = ModrinthInstallPlan()
            badPlan.items += ModrinthInstallPlan.Item("MOD", "Mod", version("v3", "MOD", "mod.jar", modBytes), false)
            val failed = ModrinthInstaller(ModrinthApi(MockHttp()), downloader(bad))
                .install(badPlan, ContentKind.MODS, cm, replacing = mapOf("MOD" to listOf(keep)))
            assertFalse(failed.succeeded)
            assertTrue(keep.exists())
        }
    }

    @Test
    fun offersUpdatesButNotBetasToReleaseUsers() = runBlocking {
        TempDir().use { tmp ->
            val cm = ContentManager(tmp.dir)
            val installed = cm.add(Fixture.file("content/fabric-example-1.2.3.jar"), ContentKind.MODS)
            val sha1 = FileHash.sha1(installed.file)
            // Treat the installed jar as Sodium 0.8.14 (release). Modrinth's update answer for
            // 1.21.11 is 0.8.15-beta.1 (real response, 2026-10-18).
            val versions = json("dep-sodium-versions") as JsonArray
            val release = versions.first { (it as JsonObject)["id"]?.jsonPrimitive?.content == "rkdTcxoT" }
            val beta = (json("update-sodium-1.21.11") as JsonObject).values.first() as JsonObject
            val http = MockHttp()
            http.on("$API/version_files", 200, JsonObject(mapOf(sha1 to release)).toString())
            http.on("$API/version_files/update", 200, JsonObject(mapOf(sha1 to beta)).toString())
            http.on("$API/project/AANobbMI/version", 200, versions.toString())
            val installer = ModrinthInstaller(ModrinthApi(http))
            // Only a beta is newer, so a release user is offered nothing.
            assertTrue(installer.checkUpdates(cm, ContentKind.MODS, "1.21.11", "fabric").isEmpty())
            val update = http.requests.first { it.url.endsWith("/version_files/update") }
            assertTrue(update.bodyText.contains(sha1) && update.bodyText.contains("\"1.21.11\"") && update.bodyText.contains("\"fabric\""))

            // A newer release is offered. (MockHttp queues per URL, so use a fresh one.)
            val newer = JsonObject(
                beta + mapOf(
                    "id" to JsonPrimitive("NEWREL"),
                    "version_type" to JsonPrimitive("release"),
                    "version_number" to JsonPrimitive("mc1.21.11-0.8.16-fabric")
                )
            )
            val http2 = MockHttp()
            http2.on("$API/version_files", 200, JsonObject(mapOf(sha1 to release)).toString())
            http2.on("$API/version_files/update", 200, JsonObject(mapOf(sha1 to newer)).toString())
            val updates = ModrinthInstaller(ModrinthApi(http2)).checkUpdates(cm, ContentKind.MODS, "1.21.11", "fabric")
            assertEquals(listOf("NEWREL"), updates.map { it.latest.id })
            assertEquals("rkdTcxoT", updates.first().current.id)
            assertEquals("AANobbMI", updates.first().projectId)
            assertEquals(installed.file, updates.first().file)
            assertEquals("Example Mod", updates.first().name)

            // Switched-off files are not checked.
            cm.setEnabled(installed, false)
            assertTrue(installer.checkUpdates(cm, ContentKind.MODS, "1.21.11", "fabric").isEmpty())
        }
    }
}
