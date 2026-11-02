/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/WorldManagerTests.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.content

import io.github.voxelium.core.TempDir
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class WorldManagerTests {
    /** A small Java world: level.dat and one region file. */
    private fun makeWorld(name: String, base: File, level: String = "level"): File {
        val world = File(base, name)
        File(world, "region").mkdirs()
        File(world, "level.dat").writeText(level)
        File(world, "region/r.0.0.mca").writeBytes(ByteArray(10_000) { 7 })
        return world
    }

    /** Zips [items] (relative to [dir]) with /usr/bin/zip (deflated, with folder entries). */
    private fun zip(items: List<String>, dir: File, out: File): File {
        val p = ProcessBuilder(listOf("/usr/bin/zip", "-qr", out.path) + items).directory(dir).inheritIO().start()
        assertEquals(0, p.waitFor())
        return out
    }

    /** A stored (uncompressed) zip with arbitrary entry names, for paths `zip` won't write. */
    private fun storedZip(entries: List<Pair<String, ByteArray>>, out: File): File {
        val body = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        fun put(d: ByteArrayOutputStream, values: List<Pair<Int, Int>>) {
            for ((v, size) in values) for (i in 0 until size) d.write((v shr (8 * i)) and 0xFF)
        }
        for ((name, data) in entries) {
            val n = name.toByteArray()
            val offset = body.size()
            put(body, listOf(0x04034b50 to 4, 20 to 2, 0 to 2, 0 to 2, 0 to 2, 0 to 2, 0 to 4, data.size to 4, data.size to 4, n.size to 2, 0 to 2))
            body.write(n)
            body.write(data)
            put(
                central,
                listOf(
                    0x02014b50 to 4, 20 to 2, 20 to 2, 0 to 2, 0 to 2, 0 to 2, 0 to 2, 0 to 4, data.size to 4, data.size to 4,
                    n.size to 2, 0 to 2, 0 to 2, 0 to 2, 0 to 2, 0 to 4, offset to 4
                )
            )
            central.write(n)
        }
        val eocd = ByteArrayOutputStream()
        put(eocd, listOf(0x06054b50 to 4, 0 to 2, 0 to 2, entries.size to 2, entries.size to 2, central.size() to 4, body.size() to 4, 0 to 2))
        out.writeBytes(body.toByteArray() + central.toByteArray() + eocd.toByteArray())
        return out
    }

    private fun expect(expected: WorldException, block: () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: WorldException) {
            assertEquals(expected, e)
        }
    }

    @Test
    fun importsAFolder() {
        TempDir().use { tmp ->
            val source = makeWorld("Survival", File(tmp.dir, "src"))
            val manager = WorldManager(File(tmp.dir, "game"))
            val world = manager.importFolder(source)
            assertEquals("Survival", world.name)
            assertEquals(10_005L, world.size)
            assertTrue(File(manager.saves, "Survival/region/r.0.0.mca").exists())
            assertTrue(source.exists())
            assertEquals(listOf("Survival"), manager.list().map { it.name })
            assertEquals(listOf("Survival"), manager.saves.list()!!.toList())
        }
    }

    @Test
    fun importsZipsWithTheWorldAtTheTopOrInASubfolder() {
        TempDir().use { tmp ->
            val src = File(tmp.dir, "src")
            makeWorld("Creative", src, level = "creative")
            File(src, "Backups").mkdirs()
            makeWorld("Backups/Island", src, level = "island")
            File(src, "Backups/README.txt").writeText("readme")
            val manager = WorldManager(File(tmp.dir, "game"))

            // level.dat at the top: the world is named after the zip.
            val top = zip(listOf("."), File(src, "Creative"), File(tmp.dir, "My Creative.zip"))
            val a = manager.importZip(top)
            assertEquals("My Creative", a.name)
            assertEquals("creative", File(a.folder, "level.dat").readText())
            assertArrayEquals(ByteArray(10_000) { 7 }, File(a.folder, "region/r.0.0.mca").readBytes())

            // Inside a subfolder: named after that folder, files beside it are left out.
            val nested = zip(listOf("Backups"), src, File(tmp.dir, "backup.zip"))
            val b = manager.importZip(nested)
            assertEquals("Island", b.name)
            assertEquals("island", File(b.folder, "level.dat").readText())
            assertFalse(File(b.folder, "README.txt").exists())
            assertEquals(setOf("My Creative", "Island"), manager.list().map { it.name }.toSet())
        }
    }

    @Test
    fun nameClashGetsANumber() {
        TempDir().use { tmp ->
            val source = makeWorld("World", File(tmp.dir, "src"))
            val manager = WorldManager(File(tmp.dir, "game"))
            val first = manager.importFolder(source)
            File(first.folder, "level.dat").writeText("played")
            assertEquals("World (2)", manager.importFolder(source).name)
            assertEquals("World (3)", manager.importFolder(source).name)
            // The first copy was not touched.
            assertEquals("played", File(first.folder, "level.dat").readText())
            assertEquals("-x", manager.uniqueName("../x"))
            assertEquals("World (4)", manager.uniqueName(" "))
        }
    }

    @Test
    fun refusesNonWorldsAndUnsafeZips() {
        TempDir().use { tmp ->
            val manager = WorldManager(File(tmp.dir, "game"))
            val noLevel = storedZip(listOf("World/region/r.0.0.mca" to "x".toByteArray()), File(tmp.dir, "a.zip"))
            expect(WorldException.NotAJavaWorld("a.zip")) { manager.importZip(noLevel) }

            val evil = storedZip(listOf("level.dat" to "l".toByteArray(), "../evil" to "e".toByteArray()), File(tmp.dir, "b.zip"))
            expect(WorldException.UnsafePath("../evil")) { manager.importZip(evil) }
            val absolute = storedZip(listOf("level.dat" to "l".toByteArray(), "/tmp/evil" to "e".toByteArray()), File(tmp.dir, "c.zip"))
            expect(WorldException.UnsafePath("/tmp/evil")) { manager.importZip(absolute) }
            assertFalse(File(tmp.dir, "evil").exists())

            val bedrock = storedZip(listOf("level.dat" to "l".toByteArray(), "db/CURRENT" to "c".toByteArray()), File(tmp.dir, "d.zip"))
            expect(WorldException.BedrockWorld("d.zip")) { manager.importZip(bedrock) }
            val notZip = File(tmp.dir, "e.zip").apply { writeText("not a zip") }
            expect(WorldException.UnsupportedZip("it is not a zip file")) { manager.importZip(notZip) }

            val folder = File(tmp.dir, "Empty").apply { mkdirs() }
            expect(WorldException.NotAJavaWorld("Empty")) { manager.importFolder(folder) }
            // Nothing half-imported is left behind.
            assertTrue(manager.list().isEmpty())
            assertTrue((manager.saves.list() ?: emptyArray()).isEmpty())
        }
    }

    @Test
    fun listsNewestFirstAndDeletes() {
        TempDir().use { tmp ->
            val game = File(tmp.dir, "game")
            val saves = File(game, "saves")
            val now = System.currentTimeMillis()
            for ((name, age) in listOf("Old" to 300_000L, "New" to 10_000L, "Middle" to 100_000L)) {
                val w = makeWorld(name, saves)
                File(w, "level.dat").setLastModified(now - age)
            }
            File(saves, "NotAWorld").mkdirs()
            val manager = WorldManager(game)
            val worlds = manager.list()
            assertEquals(listOf("New", "Middle", "Old"), worlds.map { it.name })

            manager.delete(worlds[1])
            assertEquals(listOf("New", "Old"), manager.list().map { it.name })
            val outside = WorldItem("x", File(tmp.dir, "x"), null, 0)
            assertThrows(WorldException::class.java) { manager.delete(outside) }
        }
    }
}
