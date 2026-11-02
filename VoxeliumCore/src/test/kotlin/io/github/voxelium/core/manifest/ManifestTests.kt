/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/ManifestTests.swift (zalithios e28b28b).
 * Fixtures were downloaded from piston-meta.mojang.com on 2026-10-15 and their SHA-1s match the
 * hashes published in version_manifest_v2.json.
 */

package io.github.voxelium.core.manifest

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.download.FileHash
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTests {
    @Test
    fun parsesRealVersionManifest() {
        val m = VersionManifest.decode(Fixture.text("version_manifest_v2"))
        assertEquals(919, m.versions.size)
        assertEquals("26.3", m.latest.release)
        val v = m.entry("1.20.1")
        assertNotNull(v)
        v!!
        assertEquals(VersionType.RELEASE, v.type)
        assertEquals("a5cf661d31d9dfcd3d1645be80f43954f63f63b0", v.sha1)
        val alphas = m.versions(setOf(VersionType.OLD_ALPHA))
        assertTrue(alphas.all { it.type == VersionType.OLD_ALPHA })
        assertFalse(alphas.isEmpty())
    }

    @Test
    fun fixtureHashesMatchManifest() {
        val m = VersionManifest.decode(Fixture.text("version_manifest_v2"))
        for (id in listOf("1.20.1", "1.12.2")) {
            assertEquals(m.entry(id)?.sha1, FileHash.sha1(Fixture.bytes(id)))
        }
    }

    @Test
    fun parsesModernVersionJSON() {
        val v = VersionInfo.decode(Fixture.text("1.20.1"))
        assertEquals("1.20.1", v.id)
        assertEquals("net.minecraft.client.main.Main", v.mainClass)
        assertEquals(VersionInfo.JavaVersion("java-runtime-gamma", 17), v.javaVersion)
        assertEquals("5", v.assetIndex?.id)
        assertEquals("50ce48459eb9e29836662862273d7b15d41c2c33", v.assetIndex?.sha1)
        assertEquals(88, v.libraries.size)
        assertEquals("0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838", v.downloads?.client?.sha1)
        assertEquals(23_028_853L, v.downloads?.client?.size)
        assertEquals(false, v.arguments?.game?.isEmpty())
        assertNull(v.minecraftArguments)
        assertEquals("client-1.12.xml", v.logging?.client?.file?.id)
    }

    @Test
    fun parsesLegacyVersionJSON() {
        val v = VersionInfo.decode(Fixture.text("1.12.2"))
        assertNull(v.arguments)
        assertEquals(true, v.minecraftArguments?.startsWith("--username \${auth_player_name}"))
        val natives = v.libraries.filter { it.isNative }
        assertFalse(natives.isEmpty())
        assertTrue(natives.all { it.natives != null })
    }

    @Test
    fun conditionalArgumentsDecodeBothValueShapes() {
        val json = """[ "--a", {"rules":[{"action":"allow","os":{"name":"osx"}}],"value":["-XstartOnFirstThread"]}, {"rules":[{"action":"allow","os":{"arch":"x86"}}],"value":"-Xss1M"} ]"""
        val args = MojangJson.decodeFromString(ListSerializer(Argument.serializer()), json)
        assertEquals(3, args.size)
        val linux = RulePlatform(osName = "linux", osArch = "aarch64")
        assertEquals(listOf("--a"), args.flatMap { it.resolve(linux) })
        val mac = RulePlatform(osName = "osx", osArch = "x86")
        assertEquals(listOf("--a", "-XstartOnFirstThread", "-Xss1M"), args.flatMap { it.resolve(mac) })
    }

    @Test
    fun parsesAssetIndex() {
        val idx = AssetIndex.decode(Fixture.text("assetindex-5"))
        assertEquals(3598, idx.objects.size)
        assertEquals(3575, idx.uniqueObjects.size)
        assertEquals(651_432_558L, idx.totalSize)
        val icon = idx.objects["icons/icon_128x128.png"]!!
        assertEquals(
            "https://resources.download.minecraft.net/b6/b62ca8ec10d07e6bf5ac8dae0c8c1d2e6a1e3356",
            AssetIndex.url(icon)
        )
    }

    @Test
    fun rejectsMalformedJSON() {
        assertThrows(SerializationException::class.java) { VersionInfo.decode("{\"id\": 1}") }
        assertThrows(Exception::class.java) { VersionManifest.decode("not json") }
    }

    @Test
    fun mavenPaths() {
        val cases = listOf(
            "com.mojang:brigadier:1.1.8" to "com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar",
            "org.lwjgl:lwjgl:3.3.1:natives-macos-arm64" to "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-macos-arm64.jar",
            "de.oceanlabs.mcp:mcp_config:1.20.1@zip" to "de/oceanlabs/mcp/mcp_config/1.20.1/mcp_config-1.20.1.zip"
        )
        for ((name, path) in cases) assertEquals(path, MavenCoordinate.parse(name)!!.path)
    }

    @Test
    fun invalidMavenCoordinates() {
        assertNull(MavenCoordinate.parse("nope"))
        assertNull(MavenCoordinate.parse("a::c"))
    }
}

class RuleTests {
    private val linux = RulePlatform.DEFAULT

    @Test
    fun noRulesAllows() = assertTrue(RuleEvaluator.allows(null, linux))

    @Test
    fun lastMatchingRuleWins() {
        val rules = listOf(
            Rule(Rule.Action.ALLOW),
            Rule(Rule.Action.DISALLOW, os = Rule.OS(name = "osx"))
        )
        assertTrue(RuleEvaluator.allows(rules, linux))
        assertFalse(RuleEvaluator.allows(rules, RulePlatform(osName = "osx", osArch = "arm64")))
    }

    @Test
    fun osOnlyAllowExcludesOthers() {
        val rules = listOf(Rule(Rule.Action.ALLOW, os = Rule.OS(name = "osx")))
        assertFalse(RuleEvaluator.allows(rules, linux))
    }

    @Test
    fun featuresMustMatch() {
        val rules = listOf(Rule(Rule.Action.ALLOW, features = mapOf("is_demo_user" to true)))
        assertFalse(RuleEvaluator.allows(rules, linux))
        val demo = linux.copy(features = linux.features + ("is_demo_user" to true))
        assertTrue(RuleEvaluator.allows(rules, demo))
    }

    @Test
    fun versionRegex() {
        val rules = listOf(Rule(Rule.Action.ALLOW, os = Rule.OS(name = "linux", version = "^6\\.")))
        assertFalse(RuleEvaluator.allows(rules, linux))
        assertTrue(RuleEvaluator.allows(rules, linux.copy(osVersion = "6.1.0")))
    }
}
