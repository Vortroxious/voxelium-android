/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/PlannerTests.swift (PlannerTests, IdentityTests) (zalithios e28b28b).
 * Expected counts were computed independently with a Python script over the same fixtures (iOS
 * docs/ENGINEERING_LOG.md, 2026-10-15).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.accounts.OfflineIdentity
import io.github.voxelium.core.manifest.AssetIndex
import io.github.voxelium.core.manifest.Library
import io.github.voxelium.core.manifest.MojangJson
import io.github.voxelium.core.manifest.VersionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlannerTests {
    @Test
    fun classpathLibrariesForModernVersion() {
        TempDir().use { tmp ->
            val v = VersionInfo.decode(Fixture.text("1.20.1"))
            val libs = GameFilePlanner(tmp.paths).classpathLibraries(v)
            assertEquals(38, libs.size)
            assertTrue(libs.all { !it.name.contains("org.lwjgl") && !it.isNative })
            assertEquals("com.github.oshi:oshi-core:6.2.2", libs.first().name)
        }
    }

    @Test
    fun classpathLibrariesForLegacyVersion() {
        TempDir().use { tmp ->
            assertEquals(29, GameFilePlanner(tmp.paths).classpathLibraries(VersionInfo.decode(Fixture.text("1.12.2"))).size)
        }
    }

    @Test
    fun laterDuplicateLibraryWins() {
        TempDir().use { tmp ->
            val v = VersionInfo.decode("""{"id":"x","mainClass":"M","libraries":[{"name":"a:b:1"},{"name":"c:d:1"},{"name":"a:b:2"}]}""")
            assertEquals(listOf("a:b:2", "c:d:1"), GameFilePlanner(tmp.paths).classpathLibraries(v).map { it.name })
        }
    }

    @Test
    fun libraryWithoutDownloadsDerivesURLFromMavenRepo() {
        val lib = MojangJson.decodeFromString(Library.serializer(), """{"name":"net.fabricmc:fabric-loader:0.16.0","url":"https://maven.fabricmc.net/"}""")
        assertEquals("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.16.0/fabric-loader-0.16.0.jar", lib.artifact?.url)
        assertNull(lib.artifact?.sha1)
    }

    @Test
    fun installItemsIncludeClientIndexAndLogging() {
        TempDir().use { tmp ->
            val items = GameFilePlanner(tmp.paths).installItems(VersionInfo.decode(Fixture.text("1.20.1")))
            assertEquals(38 + 3, items.size)
            assertEquals("0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838", items.first { it.destination.name == "1.20.1.jar" }.sha1)
            assertTrue(items.any { it.destination.path.endsWith("assets/indexes/5.json") })
            assertTrue(items.all { it.destination.path.startsWith(tmp.paths.root.path) })
        }
    }

    @Test
    fun assetItemsUseHashLayout() {
        TempDir().use { tmp ->
            val items = GameFilePlanner(tmp.paths).assetItems(AssetIndex.decode(Fixture.text("assetindex-5")))
            assertEquals(3575, items.size)
            val hash = items.first().sha1!!
            assertTrue(items.first().destination.path.endsWith("assets/objects/${hash.take(2)}/$hash"))
        }
    }

    @Test
    fun launchPlanModern() {
        TempDir().use { tmp ->
            val plan = LaunchPlanner(tmp.paths).plan(VersionInfo.decode(Fixture.text("1.20.1")), OfflineIdentity.identity("Steve"))
            assertEquals("net.minecraft.client.main.Main", plan.mainClass)
            assertEquals(17, plan.requiredJavaMajor)
            assertEquals(39, plan.classpath.size)
            assertEquals(39, plan.missingFiles.size)
            assertFalse(plan.jvmArguments.contains("-XstartOnFirstThread"))
            val joined = (plan.jvmArguments + plan.gameArguments).joinToString(" ")
            assertFalse(joined.contains("\${"))
            assertTrue(plan.gameArguments.contains("Steve"))
            assertFalse(plan.gameArguments.contains("--demo"))
            assertFalse(plan.gameArguments.contains("--width"))
            assertTrue(plan.jvmArguments.any { it.startsWith("-Dlog4j.configurationFile=") })
        }
    }

    @Test
    fun launchPlanCustomResolution() {
        TempDir().use { tmp ->
            val plan = LaunchPlanner(tmp.paths).plan(
                VersionInfo.decode(Fixture.text("1.20.1")), OfflineIdentity.identity("Steve"), resolution = LaunchPlanner.Resolution(1280, 720)
            )
            val i = plan.gameArguments.indexOf("--width")
            assertTrue(i >= 0)
            assertEquals("1280", plan.gameArguments[i + 1])
        }
    }

    @Test
    fun launchPlanLegacy() {
        TempDir().use { tmp ->
            val plan = LaunchPlanner(tmp.paths).plan(VersionInfo.decode(Fixture.text("1.12.2")), OfflineIdentity.identity("Steve"))
            assertEquals(8, plan.requiredJavaMajor)
            assertTrue(plan.jvmArguments.contains("-cp"))
            assertFalse(plan.gameArguments.joinToString("").contains("\${"))
        }
    }

    @Test
    fun modLoaderProfilesAreRejectedExplicitly() {
        TempDir().use { tmp ->
            val v = VersionInfo.decode("""{"id":"fabric","inheritsFrom":"1.20.1","mainClass":"M","libraries":[]}""")
            try {
                LaunchPlanner(tmp.paths).plan(v, OfflineIdentity.identity("Steve"))
                fail("expected InheritanceNotSupported")
            } catch (e: LaunchPlanException) {
                assertEquals(LaunchPlanException.InheritanceNotSupported("fabric"), e)
            }
        }
    }

    @Test
    fun substitutionKeepsUnknownPlaceholders() {
        assertEquals("a1b\${y}c", LaunchPlanner.substitute("a\${x}b\${y}c", mapOf("x" to "1")))
        assertEquals("\${unterminated", LaunchPlanner.substitute("\${unterminated", emptyMap()))
        assertEquals("plain", LaunchPlanner.substitute("plain", emptyMap()))
    }
}

class IdentityTests {
    // Vectors computed with java.util.UUID.nameUUIDFromBytes — the same function vanilla Minecraft uses for offline UUIDs.
    @Test
    fun offlineUUIDMatchesJava() {
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", OfflineIdentity.uuid("Notch").toString())
        assertEquals("a01e3843-e521-3998-958a-f459800e4d11", OfflineIdentity.uuid("Player").toString())
    }

    @Test
    fun nameValidation() {
        assertTrue(OfflineIdentity.isValidName("Steve_01"))
        assertFalse(OfflineIdentity.isValidName("ab"))
        assertFalse(OfflineIdentity.isValidName("has space"))
        assertFalse(OfflineIdentity.isValidName("ñame"))
        assertFalse(OfflineIdentity.isValidName("a".repeat(17)))
    }
}
