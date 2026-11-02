/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/InstallerTests.swift and VersionDeleterTests.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.MockTransport
import io.github.voxelium.core.MockTransport.Response
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.DownloadOutcome
import io.github.voxelium.core.download.FileHash
import io.github.voxelium.core.download.RetryPolicy
import io.github.voxelium.core.logging.Logger
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.paths.AppPaths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.time.Duration

class InstallerTests {
    private val host = "https://example.invalid"
    private val client = "client-jar".toByteArray()
    private val lib = "library-jar".toByteArray()
    private val assetA = "asset-a".toByteArray()
    private val assetB = "asset-b".toByteArray()

    private val indexData: ByteArray
        get() {
            val a = FileHash.sha1(assetA)
            val b = FileHash.sha1(assetB)
            return """{"objects":{"a.png":{"hash":"$a","size":${assetA.size}},"b.ogg":{"hash":"$b","size":${assetB.size}}}}""".toByteArray()
        }

    private fun version(): VersionInfo = VersionInfo.decode(
        """
        {"id":"t1","mainClass":"M",
         "assetIndex":{"id":"t","sha1":"${FileHash.sha1(indexData)}","size":${indexData.size},"url":"$host/index.json"},
         "downloads":{"client":{"sha1":"${FileHash.sha1(client)}","size":${client.size},"url":"$host/client.jar"}},
         "libraries":[{"name":"a:b:1","downloads":{"artifact":{"path":"a/b/1/b-1.jar","sha1":"${FileHash.sha1(lib)}","size":${lib.size},"url":"$host/b-1.jar"}}}]}
        """
    )

    private fun assetUrl(d: ByteArray): String {
        val h = FileHash.sha1(d)
        return "$host/res/${h.take(2)}/$h"
    }

    private fun installer(paths: AppPaths, t: MockTransport) = GameInstaller(
        paths,
        DownloadManager(transport = t, retry = RetryPolicy(maxAttempts = 2, baseDelay = Duration.ZERO), log = Logger(), sleep = {}),
        assetBase = "$host/res/"
    )

    private fun enqueueGameFiles(t: MockTransport) {
        t.enqueue("$host/client.jar", Response.Ok(client))
        t.enqueue("$host/b-1.jar", Response.Ok(lib))
        t.enqueue("$host/index.json", Response.Ok(indexData))
    }

    @Test
    fun interruptedInstallIsNotMarkedAndResumes() = runBlocking {
        TempDir().use { tmp ->
            val v = version()
            // First run: asset B keeps failing, like an install cut short.
            val t1 = MockTransport()
            enqueueGameFiles(t1)
            t1.enqueue(assetUrl(assetA), Response.Ok(assetA))
            t1.enqueue(assetUrl(assetB), Response.Status(503))
            val i1 = installer(tmp.paths, t1)
            assertFalse(i1.install(v, concurrency = 2).succeeded)
            assertFalse(i1.isInstalled("t1"))
            // Game files are on disk, but without the marker it is not "installed".
            assertTrue(tmp.paths.clientJar("t1").exists())

            // Second run: only the missing asset is fetched.
            val t2 = MockTransport()
            t2.enqueue(assetUrl(assetB), Response.Ok(assetB))
            val i2 = installer(tmp.paths, t2)
            val r2 = i2.install(v, concurrency = 2)
            assertTrue(r2.succeeded)
            assertTrue(i2.isInstalled("t1"))
            assertEquals(listOf(assetUrl(assetB)), t2.requests)
            assertEquals(4, r2.outcomes.values.count { it == DownloadOutcome.AlreadyValid })
        }
    }

    @Test
    fun reinstallClearsMarkerUntilFinished() = runBlocking {
        TempDir().use { tmp ->
            val v = version()
            val t = MockTransport()
            enqueueGameFiles(t)
            t.enqueue(assetUrl(assetA), Response.Ok(assetA))
            t.enqueue(assetUrl(assetB), Response.Ok(assetB))
            val i = installer(tmp.paths, t)
            assertTrue(i.install(v).succeeded)
            assertTrue(i.isInstalled("t1"))

            // Corrupt a library; the repair run fails, so the marker must go away.
            tmp.paths.library("a/b/1/b-1.jar").writeText("bad")
            val broken = MockTransport()
            broken.enqueue("$host/b-1.jar", Response.Status(500))
            assertFalse(installer(tmp.paths, broken).install(v).succeeded)
            assertFalse(i.isInstalled("t1"))
        }
    }
}

class VersionDeleterTests {
    /** 1.20.1 (vanilla) ← loader ← pack, each marked installed; the loader has a world. */
    private fun setUp(paths: AppPaths) {
        fun version(id: String, json: String) {
            paths.versionDir(id).mkdirs()
            paths.versionJson(id).writeText(json)
            File(paths.versionDir(id), GameInstaller.MARKER).writeBytes(ByteArray(0))
        }
        fun child(id: String, parent: String) = """{"id":"$id","inheritsFrom":"$parent","mainClass":"x.Main","libraries":[]}"""
        version("1.20.1", Fixture.text("1.20.1"))
        version("1.20.1-forge-47.4.10", child("1.20.1-forge-47.4.10", "1.20.1"))
        version("My Pack", child("My Pack", "1.20.1-forge-47.4.10"))
        val world = File(paths.versionDir("1.20.1-forge-47.4.10"), "saves/Base").apply { mkdirs() }
        File(world, "level.dat").writeText("nbt")
        // A vanilla world in the shared folder.
        val vanilla = File(paths.gameDir, "saves/Shared").apply { mkdirs() }
        File(vanilla, "level.dat").writeText("nbt")
    }

    @Test
    fun refusesWhileOthersBuildOnItThenDeletesInOrder() {
        TempDir().use { tmp ->
            val paths = tmp.paths
            setUp(paths)
            val deleter = VersionDeleter(paths)
            val installer = GameInstaller(paths)

            assertEquals(listOf("1.20.1-forge-47.4.10", "My Pack"), deleter.dependents("1.20.1"))
            assertEquals(listOf("My Pack"), deleter.dependents("1.20.1-forge-47.4.10"))
            assertEquals(1, deleter.worldCount("1.20.1-forge-47.4.10"))
            assertEquals(0, deleter.worldCount("1.20.1")) // vanilla worlds are shared, not the version's

            try {
                deleter.delete("1.20.1")
                fail("expected NeededBy")
            } catch (e: VersionDeleteException) {
                assertEquals(VersionDeleteException.NeededBy("1.20.1", listOf("1.20.1-forge-47.4.10", "My Pack")), e)
            }
            assertTrue(installer.isInstalled("1.20.1"))

            deleter.delete("My Pack")
            deleter.delete("1.20.1-forge-47.4.10")
            assertFalse(paths.versionDir("1.20.1-forge-47.4.10").exists())
            deleter.delete("1.20.1")
            assertFalse(installer.isInstalled("1.20.1"))
            // Shared vanilla worlds stay.
            assertTrue(File(paths.gameDir, "saves/Shared/level.dat").exists())

            try {
                deleter.delete("1.20.1")
                fail("expected NotInstalled")
            } catch (e: VersionDeleteException) {
                assertEquals(VersionDeleteException.NotInstalled("1.20.1"), e)
            }
            assertThrows(Exception::class.java) { deleter.delete("../config") }
            assertEquals(
                "1.20.1 can't be deleted while neoforge-21.1.264 builds on it. Delete that first.",
                VersionDeleteException.NeededBy("1.20.1", listOf("neoforge-21.1.264")).message
            )
        }
    }
}
