/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/ControlLayoutTests.swift (zalithios e28b28b).
 * Android additions: the byte-compatibility tests against files written by the Swift
 * encoder (fixtures/controls, see the README there) and the number format test.
 */

package io.github.voxelium.core.controls

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.logging.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

class ControlLayoutTests {
    // Landscape iPhone sizes in points (15 Pro Max, 16 Pro Max, 15 Pro, SE).
    private val screens = listOf(932.0 to 430.0, 956.0 to 440.0, 852.0 to 393.0, 667.0 to 375.0)

    @Test
    fun defaultFitsOnScreen() {
        for ((w, h) in screens) {
            val layout = ControlLayout.amethystDefault(w, h)
            assertEquals(18, layout.buttons.size)
            for (b in layout.buttons) {
                assertTrue("${b.name} x on $w", b.x >= 0 && b.x * w + b.width <= w + 0.001)
                assertTrue("${b.name} y on $h", b.y >= 0 && b.y * h + b.height <= h + 0.001)
            }
            val j = layout.joystick
            assertTrue(j.x * w + j.size <= w && j.y * h + j.size <= h + 0.001)
        }
    }

    @Test
    fun defaultMatchesTheOverlayPositions() {
        // Same numbers the built-in VXGLFW layout produced on the user's phone
        // (jvm.log, build 202610170113: Jump 815,205  Shift 815,134  Right Click 746,282).
        val (w, h) = 932.0 to 430.0
        val layout = ControlLayout.amethystDefault(w, h)
        fun origin(name: String): Pair<Int, Int> {
            val b = layout.buttons.firstOrNull { it.name == name }
            assertNotNull(name, b)
            b!!
            return (b.x * w).roundToInt() to (b.y * h).roundToInt()
        }
        assertEquals(815 to 205, origin("Jump"))
        assertEquals(815 to 134, origin("Shift"))
        assertEquals(743 to 134, origin("Sprint"))
        assertEquals(746 to 282, origin("Right Click"))
        assertEquals(818 to 282, origin("Inventory"))
        assertEquals(862 to 0, origin("Hide GUI"))
    }

    @Test
    fun storeRoundTripAndReset() {
        TempDir().use { tmp ->
            val store = ControlLayoutStore(tmp.paths)
            assertNull(store.load(Logger()))
            var layout = ControlLayout.amethystDefault(932.0, 430.0)
            layout = layout.copy(
                buttons = layout.buttons + ControlButton(
                    name = "Drop", keys = listOf(ControlKeys.all.first { it.name == "Q" }.code), x = 0.5, y = 0.5
                )
            )
            store.save(layout)
            assertEquals(layout, store.load(Logger()))
            store.reset()
            assertNull(store.load(Logger()))
            assertEquals(ControlLayout.amethystDefault(932.0, 430.0), store.effective(932.0, 430.0, Logger()))
        }
    }

    @Test
    fun corruptFileFallsBackToDefault() {
        TempDir().use { tmp ->
            val store = ControlLayoutStore(tmp.paths)
            tmp.paths.controlsDir.mkdirs()
            store.file.writeText("{not json")
            assertNull(store.load(Logger()))
            assertTrue(File(store.file.path + ".corrupt").exists())
        }
    }

    @Test
    fun toleratesMissingFields() {
        val json = """{"version":1,"joystick":{"x":0,"y":0.5,"size":200},"buttons":[{"name":"Drop","keys":[81]}]}"""
        val layout = ControlLayoutStore.decode(json)
        val b = layout.buttons.first()
        assertTrue(b.keys == listOf(81) && b.mouse == -1 && b.special == ControlSpecial.NONE && b.showInGame && !b.showInMenu)
        assertEquals("Q", b.actionSummary)
    }

    @Test
    fun olderFilesWithoutOpacityStillLoad() {
        val json = """{"version":1,"joystick":{"x":0.9,"y":0.9,"size":900},"buttons":[]}"""
        val layout = ControlLayoutStore.decode(json).clamped(932.0, 430.0)
        assertEquals(1.0, layout.opacity, 0.0)
        assertEquals(300.0, layout.joystick.size, 0.0)
        assertTrue(layout.joystick.x * 932 + 300 <= 932.001 && layout.joystick.y * 430 + 300 <= 430.001)
        val faint = layout.copy(opacity = 0.0)
        assertEquals(ControlLayout.OPACITY_RANGE.start, faint.clamped(932.0, 430.0).opacity, 0.0)
    }

    @Test
    fun clampingKeepsButtonsOnScreen() {
        val b = ControlButton(name = "X", keys = listOf(1, 2, 3, 4, 5), x = 1.5, y = -1.0, width = 5.0, height = 900.0)
            .clamped(932.0, 430.0)
        assertTrue(b.width == 20.0 && b.height == 400.0 && b.keys.size == ControlButton.MAX_KEYS)
        assertTrue(b.x * 932 + b.width <= 932.001 && b.y == 0.0)
    }

    @Test
    fun newButtonsGoToFreeSpots() {
        val (w, h) = 932.0 to 430.0
        val toolbar = ControlLayout.Rect(w / 2 - 170, h * 0.45 - 65, 340.0, 130.0)
        var layout = ControlLayout.amethystDefault(w, h)
        val added = mutableListOf<ControlLayout.Rect>()
        for (i in 0 until 4) {
            val (x, y) = layout.freeSpot(69.6, 69.6, w, h, avoiding = listOf(toolbar))
            val r = ControlLayout.Rect(x * w, y * h, 69.6, 69.6)
            assertFalse("button $i under the toolbar", r.intersects(toolbar))
            assertTrue(r.x >= 0 && r.y >= 0 && r.x + r.width <= w && r.y + r.height <= h)
            for (b in layout.buttons.filter { !it.inDrawer }) {
                assertFalse("button $i covers ${b.name}", r.intersects(ControlLayout.Rect(b.x * w, b.y * h, b.width, b.height)))
            }
            added += r
            layout = layout.copy(buttons = layout.buttons + ControlButton(name = "New $i", x = x, y = y))
        }
        assertEquals(4, added.map { "${it.x},${it.y}" }.toSet().size)
    }

    @Test
    fun keyTableIsUnique() {
        assertEquals(ControlKeys.all.size, ControlKeys.all.map { it.code }.toSet().size)
        assertTrue(ControlKeys.name(32) == "Space" && ControlKeys.name(294) == "F5")
    }

    // Android: files move between the apps unchanged.

    @Test
    fun defaultIsWrittenInTheSameBytesAsSwift() {
        val swift = Fixture.file("controls/default-932x430.json").readText()
        assertEquals(swift, ControlLayoutStore.encode(ControlLayout.amethystDefault(932.0, 430.0)))
    }

    @Test
    fun iosFileLoadsAndIsWrittenBackUnchanged() {
        val swift = Fixture.file("controls/custom-667x375.json").readText()
        val layout = ControlLayoutStore.decode(swift)
        assertEquals(0.65, layout.opacity, 0.0)
        assertEquals(180.0, layout.joystick.size, 0.0)
        val drop = layout.buttons.last()
        assertEquals("Drop / \"Q\" ü", drop.name)
        assertEquals("11111111-2222-4333-8444-555555555555", drop.id.toString())
        assertEquals(listOf(81, 340), drop.keys)
        assertTrue(drop.toggle && drop.showInMenu)
        assertEquals(ControlSpecial.RUNTIME_MENU, layout.buttons.first { it.name == "Runtime Menu" }.special)
        assertEquals(swift, ControlLayoutStore.encode(layout))
        // The store reads a file copied from the iOS app as it is.
        TempDir().use { tmp ->
            val store = ControlLayoutStore(tmp.paths)
            tmp.paths.controlsDir.mkdirs()
            store.file.writeText(swift)
            assertEquals(layout, store.load(Logger()))
        }
    }

    @Test
    fun unknownSpecialAndNullsReadAsDefaults() {
        val json = """{"buttons":[{"name":"X","special":"teleport","mouse":null,"id":"00000000-0000-4000-8000-00000000000a"}]}"""
        val b = ControlLayoutStore.decode(json).buttons.single()
        assertEquals(ControlSpecial.NONE, b.special)
        assertEquals(-1, b.mouse)
        assertTrue(ControlLayoutStore.encode(ControlLayout(buttons = listOf(b))).contains("\"00000000-0000-4000-8000-00000000000A\""))
    }

    @Test
    fun numbersUseSwiftsShortestForm() {
        val cases = mapOf(
            "96.0" to "96", "0.5" to "0.5", "0.001" to "0.001", "1.0E-4" to "0.0001", "1.0E-5" to "1e-05",
            "1.2345E-5" to "1.2345e-05", "1.0E15" to "1000000000000000", "1.0E16" to "1e+16",
            "1.5E17" to "1.5e+17", "-0.0" to "-0", "0.30000000000000004" to "0.30000000000000004",
            "-2.5" to "-2.5", "32" to "32", "-1" to "-1"
        )
        for ((kotlin, swift) in cases) assertEquals(kotlin, swift, SwiftJson.number(kotlin))
    }
}
