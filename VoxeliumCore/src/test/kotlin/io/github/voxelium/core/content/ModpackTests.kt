/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/ModpackTests.swift (zalithios e28b28b).
 * installsAVanillaPackWithOverrides comes with phase 6 (it needs GameInstaller).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.launch.GameInstaller
import io.github.voxelium.core.launch.ModLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URI

class ModpackTests {
    private fun fixture(name: String) = Fixture.file("modpack/$name")

    @Test
    fun readsARealModrinthIndex() {
        // Fabulously Optimized 12.3.0's modrinth.index.json, trimmed to 3 files (2026-10-18).
        val index = ModpackIndex.decode(fixture("fabulously-optimized-12.3.0-index.json").readText())
        assertEquals(1, index.formatVersion)
        assertEquals("Fabulously Optimized", index.name)
        assertEquals("12.3.0", index.versionId)
        assertEquals("1.21.11", index.minecraft)
        assertEquals(ModLoader.FABRIC, index.loader?.loader)
        assertEquals("0.19.3", index.loader?.version)
        assertEquals(3, index.files.size)
        assertTrue(index.files.all { it.isForClient })
        assertEquals(40, index.files[0].hashes["sha1"]?.length)
        assertEquals("cdn.modrinth.com", URI(index.files[0].downloads.first()).host)
        assertEquals("Fabulously Optimized 12.3.0", ModpackInstaller.versionId(index))
        assertEquals(3, index.clientModCount)
    }

    @Test
    fun refusesUnsafePathsAndHosts() {
        TempDir().use { tmp ->
            val installer = ModpackInstaller(GameInstaller(tmp.paths))
            val instance = tmp.paths.instanceDir("Pack 1")
            fun index(path: String, url: String, client: String = "required") = ModpackIndex(
                formatVersion = 1, game = "minecraft", versionId = "1", name = "Pack", summary = null,
                files = listOf(
                    ModpackIndex.File(
                        path, mapOf("sha1" to "a".repeat(40)), ModpackIndex.File.Env(client, "required"), listOf(url), 1
                    )
                ),
                dependencies = mapOf("minecraft" to "1.21.11")
            )
            fun expect(expected: ModpackException, block: () -> Unit) {
                try {
                    block()
                    fail("expected $expected")
                } catch (e: ModpackException) {
                    assertEquals(expected, e)
                }
            }
            expect(ModpackException.UnsafePath("../evil.jar")) {
                installer.plannedDownloads(index("../evil.jar", "https://cdn.modrinth.com/a.jar"), instance)
            }
            expect(ModpackException.UnsafePath("/etc/evil")) {
                installer.plannedDownloads(index("/etc/evil", "https://cdn.modrinth.com/a.jar"), instance)
            }
            expect(ModpackException.UntrustedDownload("evil.example")) {
                installer.plannedDownloads(index("mods/a.jar", "https://evil.example/a.jar"), instance)
            }
            // Server-only files are skipped; others land inside the instance.
            assertTrue(installer.plannedDownloads(index("mods/s.jar", "https://cdn.modrinth.com/s.jar", client = "unsupported"), instance).isEmpty())
            val ok = installer.plannedDownloads(index("mods/a.jar", "https://github.com/x/a.jar"), instance)
            assertEquals(listOf(java.io.File(instance, "mods/a.jar").absoluteFile.normalize()), ok.map { it.destination })
            assertEquals("Pack 1", ModpackInstaller.versionId(index("", "https://x")))
            // A name that already ends with the version is not repeated.
            val named = ModpackIndex(1, "minecraft", "3.2", "Genesis 3.2", null, emptyList(), mapOf("minecraft" to "1.20.1"))
            assertEquals("Genesis 3.2", ModpackInstaller.versionId(named))
            // Characters unsafe in a folder name or on the command line become "-".
            val colon = ModpackIndex(1, "minecraft", "1.0.0", "Test Pack: Vanilla+", null, emptyList(), mapOf("minecraft" to "1.21.11"))
            assertEquals("Test Pack- Vanilla+ 1.0.0", ModpackInstaller.versionId(colon))
        }
    }

    @Test
    fun readsForgeAndNeoForgePacksAndRefusesOtherZips() {
        val (neo, zip) = ModpackInstaller.read(fixture("test-neoforge.mrpack"))
        zip.close()
        assertEquals(ModLoader.NEOFORGE, neo.loader?.loader)
        assertEquals("21.1.77", neo.loader?.version)
        // Forge packs write the bare build or "<minecraft>-<build>".
        for (raw in listOf("47.4.10", "1.20.1-47.4.10")) {
            val index = ModpackIndex.decode(
                """{"formatVersion":1,"game":"minecraft","versionId":"1","name":"F","files":[],"dependencies":{"minecraft":"1.20.1","forge":"$raw"}}"""
            )
            assertEquals(ModLoader.FORGE, index.loader?.loader)
            assertEquals("47.4.10", index.loader?.version)
        }
        try {
            ModpackInstaller.read(Fixture.file("content/gold-pack.zip"))
            fail("a resource pack was read as a modpack")
        } catch (e: ModpackException) {
            assertEquals(ModpackException.NotAModpack, e)
        }
    }

    @Test
    fun installsAVanillaPackWithOverrides() = kotlinx.coroutines.runBlocking {
        TempDir().use { tmp ->
            // Minecraft 1.21.11 is already installed: a minimal version whose only file (an empty
            // asset index) is present and valid, so nothing needs downloading.
            val assetIndex = """{"objects":{}}""".toByteArray()
            val indexFile = tmp.paths.assetIndex("29")
            indexFile.parentFile.mkdirs()
            indexFile.writeBytes(assetIndex)
            val base = tmp.paths.versionJson("1.21.11")
            base.parentFile.mkdirs()
            base.writeText(
                """{"id":"1.21.11","type":"release","mainClass":"net.minecraft.client.main.Main","libraries":[],"assets":"29",
                   "assetIndex":{"id":"29","sha1":"${io.github.voxelium.core.download.FileHash.sha1(assetIndex)}","size":${assetIndex.size},"totalSize":0,
                                 "url":"https://piston-meta.mojang.com/v1/packages/x/29.json"}}"""
            )
            val transport = io.github.voxelium.core.MockTransport()
            val game = GameInstaller(
                tmp.paths,
                io.github.voxelium.core.download.DownloadManager(
                    transport = transport,
                    retry = io.github.voxelium.core.download.RetryPolicy(maxAttempts = 1, baseDelay = kotlin.time.Duration.ZERO),
                    log = io.github.voxelium.core.logging.Logger(), sleep = {}
                )
            )
            game.completionMarker("1.21.11").writeBytes(ByteArray(0))
            transport.enqueue("https://cdn.modrinth.com/data/TEST/versions/1/test-pack.zip", io.github.voxelium.core.MockTransport.Response.Ok("pack bytes".toByteArray()))

            val installer = ModpackInstaller(game)
            val (id, report) = installer.install(fixture("test-vanilla.mrpack"), source = "PROJ" to "VER")
            assertTrue(report.succeeded)
            assertEquals("Test Pack- Vanilla+ 1.0.0", id)
            assertTrue(game.isInstalled(id))

            // Its own version, building on Minecraft, with its own game folder.
            val resolved = io.github.voxelium.core.launch.VersionResolver(tmp.paths).resolve(id)
            assertTrue(resolved.isModded)
            assertEquals("1.21.11", resolved.baseVersionId)
            assertEquals("net.minecraft.client.main.Main", resolved.info.mainClass)
            val instance = tmp.paths.instanceDir(id)
            assertEquals("pack bytes", java.io.File(instance, "resourcepacks/test-pack.zip").readText())
            assertTrue(!java.io.File(instance, "mods/server-only.jar").exists())
            assertEquals(1, transport.requests.size) // the server-only file was not downloaded
            assertEquals("from overrides", java.io.File(instance, "config/test.txt").readText())
            assertEquals("lang:id_id\n", java.io.File(instance, "options.txt").readText()) // client-overrides win
            assertEquals(
                InstalledModpack("Test Pack: Vanilla+", "1.0.0", "1.21.11", null, "PROJ", "VER"),
                ModpackInstaller.installedModpack(instance)
            )

            // Installing the same pack again is refused rather than overwriting the instance.
            try {
                installer.install(fixture("test-vanilla.mrpack"))
                fail("expected AlreadyInstalled")
            } catch (e: ModpackException) {
                assertEquals(ModpackException.AlreadyInstalled(id), e)
            }
        }
    }

    /** From the vanilla pack fixture: overrides then client-overrides (which win), all inside the instance. */
    @Test
    fun plansOverridesInsideTheInstance() {
        TempDir().use { tmp ->
            val (index, zip) = ModpackInstaller.read(fixture("test-vanilla.mrpack"))
            zip.use {
                val instance = tmp.paths.instanceDir(ModpackInstaller.versionId(index))
                val overrides = ModpackInstaller(GameInstaller(tmp.paths)).plannedOverrides(zip, instance)
                assertTrue(overrides.isNotEmpty())
                assertTrue(overrides.all { (_, dest) -> dest.path.startsWith(instance.path + java.io.File.separator) })
                assertEquals("client-overrides/options.txt", overrides.last { it.second.name == "options.txt" }.first)
            }
        }
    }
}
