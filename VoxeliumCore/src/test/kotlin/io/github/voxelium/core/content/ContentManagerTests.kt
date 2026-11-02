/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/ContentManagerTests.swift (ContentManagerTests)
 * (zalithios e28b28b). IrisSettings, GameOptions and ShaderLaunchAdvice tests come with phase 6.
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.TempDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ContentManagerTests {
    private fun fixture(name: String) = Fixture.file("content/$name")

    @Test
    fun zipReaderHandlesDeflatedAndStoredEntries() {
        ZipReader(fixture("fabric-example-1.2.3.jar")).use { deflated ->
            assertTrue(deflated.read("fabric.mod.json")!!.toString(Charsets.UTF_8).contains("\"examplemod\""))
        }
        ZipReader(fixture("test-shaders.zip")).use { stored ->
            assertEquals("void main(){}\n", stored.read("shaders/composite.fsh")!!.toString(Charsets.UTF_8))
            assertNull(stored.read("missing"))
        }
        assertThrows(ZipReader.ZipError.NotAZip::class.java) { ZipReader(fixture("broken.jar")) }
    }

    @Test
    fun readsModMetadataForEveryLoader() {
        val fabric = ContentManager.inspect(fixture("fabric-example-1.2.3.jar"), ContentKind.MODS, false)
        assertEquals("Example Mod", fabric.name)
        assertEquals("1.2.3", fabric.version)
        assertNull(fabric.problem)
        assertEquals(listOf("fabric"), fabric.mod?.loaders)
        assertEquals(listOf("Alice", "Bob"), fabric.mod?.authors)
        assertEquals("examplemod", fabric.mod?.id)

        val quilt = ContentManager.inspect(fixture("quilt-example-0.4.0.jar"), ContentKind.MODS, false)
        assertEquals("Quilt Example", quilt.name)
        assertEquals("0.4.0", quilt.version)
        assertEquals(listOf("quilt"), quilt.mod?.loaders)
        assertEquals(listOf("Carol"), quilt.mod?.authors)

        // [[mods]] with a trailing comment, ${file.jarVersion} from the manifest, a multi-line
        // description and the top-level authors key.
        val forge = ContentManager.inspect(fixture("forge-example-2.0.1.jar"), ContentKind.MODS, false)
        assertEquals("Forge Example", forge.name)
        assertEquals("2.0.1", forge.version)
        assertEquals("forgeexample", forge.mod?.id)
        assertEquals("A Forge mod\non two lines.", forge.description)
        assertEquals(listOf("Dave"), forge.mod?.authors)
        assertEquals(listOf("forge"), forge.mod?.loaders)

        val broken = ContentManager.inspect(fixture("broken.jar"), ContentKind.MODS, false)
        assertNotNull(broken.problem)
        assertEquals("broken", broken.name)
    }

    @Test
    fun readsPacks() {
        val rp = ContentManager.inspect(fixture("gold-pack.zip"), ContentKind.RESOURCE_PACKS, false)
        assertEquals("Gold Pack v1", rp.description) // components joined, § codes stripped
        assertNull(rp.problem)
        val sp = ContentManager.inspect(fixture("test-shaders.zip"), ContentKind.SHADER_PACKS, false)
        assertEquals("test-shaders", sp.name)
        assertNull(sp.problem)
    }

    @Test
    fun addEnableDisableRemove() {
        TempDir().use { tmp ->
            val game = File(tmp.dir, "instance")
            val cm = ContentManager(game)
            assertTrue(cm.list(ContentKind.MODS).isEmpty())

            val added = cm.add(fixture("fabric-example-1.2.3.jar"), ContentKind.MODS)
            cm.add(fixture("quilt-example-0.4.0.jar"), ContentKind.MODS)
            assertTrue(File(game, "mods/fabric-example-1.2.3.jar").exists())
            assertEquals(listOf("Example Mod", "Quilt Example"), cm.list(ContentKind.MODS).map { it.name })

            // Disabling renames to .disabled; the id stays the same.
            val off = cm.setEnabled(added, false)
            assertFalse(off.isEnabled)
            assertEquals("fabric-example-1.2.3.jar.disabled", off.fileName)
            assertEquals(added.id, off.id)
            assertEquals(false, cm.list(ContentKind.MODS).first { it.id == added.id }.isEnabled)
            val on = cm.setEnabled(off, true)
            assertTrue(on.isEnabled)
            assertEquals("fabric-example-1.2.3.jar", on.fileName)

            // Re-adding replaces the file, enabled or disabled.
            cm.setEnabled(on, false)
            cm.add(fixture("fabric-example-1.2.3.jar"), ContentKind.MODS)
            assertEquals(listOf(true), cm.list(ContentKind.MODS).filter { it.id == added.id }.map { it.isEnabled })

            cm.remove(cm.list(ContentKind.MODS).first { it.id == added.id })
            assertEquals(listOf("Quilt Example"), cm.list(ContentKind.MODS).map { it.name })

            cm.add(fixture("gold-pack.zip"), ContentKind.RESOURCE_PACKS)
            cm.add(fixture("test-shaders.zip"), ContentKind.SHADER_PACKS)
            assertEquals(1, cm.list(ContentKind.RESOURCE_PACKS).size)
            assertEquals(1, cm.list(ContentKind.SHADER_PACKS).size)
        }
    }

    @Test
    fun refusesTheWrongKind() {
        TempDir().use { tmp ->
            val cm = ContentManager(tmp.dir)
            assertThrows(ContentException::class.java) { cm.add(fixture("gold-pack.zip"), ContentKind.MODS) } // not a jar
            assertThrows(ContentException::class.java) { cm.add(fixture("broken.jar"), ContentKind.MODS) } // not a zip
            try {
                cm.add(fixture("gold-pack.zip"), ContentKind.SHADER_PACKS)
                fail("a resource pack was accepted as a shader pack")
            } catch (e: ContentException) {
                assertTrue(e.message!!.contains("looks like a resource pack"))
            }
            try {
                cm.add(fixture("test-shaders.zip"), ContentKind.RESOURCE_PACKS)
                fail("a shader pack was accepted as a resource pack")
            } catch (e: ContentException) {
                assertTrue(e.message!!.contains("looks like a shader pack"))
            }
            assertTrue(cm.list(ContentKind.MODS).isEmpty() && cm.list(ContentKind.SHADER_PACKS).isEmpty())
        }
    }

    @Test
    fun compatibilityWarnings() {
        val fabric = ContentManager.inspect(fixture("fabric-example-1.2.3.jar"), ContentKind.MODS, false)
        val quilt = ContentManager.inspect(fixture("quilt-example-0.4.0.jar"), ContentKind.MODS, false)
        val forge = ContentManager.inspect(fixture("forge-example-2.0.1.jar"), ContentKind.MODS, false)
        assertNull(ContentManager.compatibilityWarning(fabric, "fabric"))
        assertNull(ContentManager.compatibilityWarning(fabric, "quilt")) // Quilt loads Fabric mods
        assertEquals("Made for Quilt, so Fabric won't load it.", ContentManager.compatibilityWarning(quilt, "fabric"))
        assertEquals("Made for Forge, so Fabric won't load it.", ContentManager.compatibilityWarning(forge, "fabric"))
        assertNull(ContentManager.compatibilityWarning(forge, null))
    }

    @Test
    fun refusesModsForAnotherLoader() {
        TempDir().use { tmp ->
            val cm = ContentManager(File(tmp.dir, "game"))
            // A Fabric mod on NeoForge and a Forge mod on Fabric are not copied in.
            try {
                cm.add(fixture("fabric-example-1.2.3.jar"), ContentKind.MODS, loader = "neoforge")
                fail("a Fabric mod was added to NeoForge")
            } catch (e: ContentException) {
                assertEquals(
                    ContentException.WrongLoader("fabric-example-1.2.3.jar: Made for Fabric, so NeoForge won't load it. Look for its NeoForge version under Browse."),
                    e
                )
            }
            assertThrows(ContentException::class.java) { cm.add(fixture("forge-example-2.0.1.jar"), ContentKind.MODS, loader = "fabric") }
            assertTrue(cm.list(ContentKind.MODS).isEmpty())
            // Matching loaders (and Fabric mods on Quilt) are added.
            cm.add(fixture("forge-example-2.0.1.jar"), ContentKind.MODS, loader = "forge")
            cm.add(fixture("fabric-example-1.2.3.jar"), ContentKind.MODS, loader = "quilt")
            assertEquals(2, cm.list(ContentKind.MODS).size)
        }
    }
}
