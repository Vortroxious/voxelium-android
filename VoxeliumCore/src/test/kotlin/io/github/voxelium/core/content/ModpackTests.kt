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
            val installer = ModpackInstaller(tmp.paths)
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

    /** From the vanilla pack fixture: overrides then client-overrides (which win), all inside the instance. */
    @Test
    fun plansOverridesInsideTheInstance() {
        TempDir().use { tmp ->
            val (index, zip) = ModpackInstaller.read(fixture("test-vanilla.mrpack"))
            zip.use {
                val instance = tmp.paths.instanceDir(ModpackInstaller.versionId(index))
                val overrides = ModpackInstaller(tmp.paths).plannedOverrides(zip, instance)
                assertTrue(overrides.isNotEmpty())
                assertTrue(overrides.all { (_, dest) -> dest.path.startsWith(instance.path + java.io.File.separator) })
                assertEquals("client-overrides/options.txt", overrides.last { it.second.name == "options.txt" }.first)
            }
        }
    }
}
