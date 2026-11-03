/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/ModLoaderTests.swift and the ForgeMeta tests of
 * ForgeTests.swift (zalithios e28b28b). The ForgeInstaller and ForgeLaunch tests wait for the
 * Android Forge decision.
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.accounts.HttpHeaders
import io.github.voxelium.core.accounts.MockHttp
import io.github.voxelium.core.accounts.OfflineIdentity
import io.github.voxelium.core.manifest.Library
import io.github.voxelium.core.manifest.MavenCoordinate
import io.github.voxelium.core.manifest.MojangJson
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.paths.AppPaths
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModLoaderTests {
    /** The real Fabric 0.20.0 profile, re-pointed at the 1.20.1 fixture so it can be merged against a vanilla version JSON we have on disk. */
    private fun fabricProfileOn1201(): String {
        val json = Json.parseToJsonElement(Fixture.text("fabric-profile-0.20.0-1.21.11")) as JsonObject
        return JsonObject(json + mapOf("inheritsFrom" to JsonPrimitive("1.20.1"), "id" to JsonPrimitive("fabric-loader-0.20.0-1.20.1"))).toString()
    }

    private fun install(json: String, id: String, paths: AppPaths) {
        val file = paths.versionJson(id)
        file.parentFile.mkdirs()
        file.writeText(json)
    }

    private fun <T : Throwable> expect(expected: T, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected $expected")
        } catch (e: Throwable) {
            assertEquals(expected, e)
        }
    }

    @Test
    fun mergesFabricOntoVanilla() {
        TempDir().use { tmp ->
            install(Fixture.text("1.20.1"), "1.20.1", tmp.paths)
            install(fabricProfileOn1201(), "fabric-loader-0.20.0-1.20.1", tmp.paths)

            val r = VersionResolver(tmp.paths).resolve("fabric-loader-0.20.0-1.20.1")
            assertEquals(listOf("fabric-loader-0.20.0-1.20.1", "1.20.1"), r.chain)
            assertTrue(r.isModded)
            assertEquals("1.20.1", r.baseVersionId)
            val v = r.info
            assertNull(v.inheritsFrom)
            assertEquals("fabric-loader-0.20.0-1.20.1", v.id)
            assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", v.mainClass)
            assertEquals("1.20.1", v.clientJarId)
            assertEquals("5", v.assetIndex?.id)
            assertEquals(17, v.javaVersion?.majorVersion)
            assertTrue(v.downloads?.client != null)

            // Libraries: vanilla first, then Fabric's; the planner keeps one per artifact.
            val fabricLoader = v.libraries.first { it.name.startsWith("net.fabricmc:fabric-loader:") }
            assertTrue(fabricLoader.artifact?.url?.startsWith("https://maven.fabricmc.net/net/fabricmc/fabric-loader/") == true)
            val asm = v.libraries.first { it.name == "org.ow2.asm:asm:9.11" }
            assertEquals("2a2c870691d2220eb93492c88fdcd3711ab0fadb", asm.artifact?.sha1)
            assertEquals(131056L, asm.artifact?.size)
            val cp = GameFilePlanner(tmp.paths).classpathLibraries(v).map { it.name }
            assertTrue(cp.any { it.startsWith("net.fabricmc:fabric-loader:") })
            assertEquals(cp.size, cp.mapNotNull { MavenCoordinate.parse(it)?.key }.toSet().size)

            // Launch plan: Knot main class, vanilla client jar, parent + child JVM arguments, and the modded version's own game folder.
            val instance = tmp.paths.instanceDir(v.id)
            val plan = LaunchPlanner(tmp.paths).plan(v, OfflineIdentity.identity("Steve"), gameDirectory = instance)
            assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", plan.mainClass)
            assertTrue(plan.classpath.last().path.endsWith("versions/1.20.1/1.20.1.jar"))
            assertTrue(plan.jvmArguments.any { it.startsWith("-DFabricMcEmu=") })
            assertTrue(plan.jvmArguments.contains("-cp"))
            assertEquals(instance.path, plan.gameArguments[plan.gameArguments.indexOf("--gameDir") + 1])
        }
    }

    @Test
    fun resolverErrors() {
        TempDir().use { tmp ->
            install(fabricProfileOn1201(), "fabric-loader-0.20.0-1.20.1", tmp.paths)
            expect(VersionResolveException.ParentMissing("fabric-loader-0.20.0-1.20.1", "1.20.1")) {
                VersionResolver(tmp.paths).resolve("fabric-loader-0.20.0-1.20.1")
            }
            install("""{"id":"a","inheritsFrom":"b","mainClass":"M","libraries":[]}""", "a", tmp.paths)
            install("""{"id":"b","inheritsFrom":"a","mainClass":"M","libraries":[]}""", "b", tmp.paths)
            expect(VersionResolveException.Cycle("a")) { VersionResolver(tmp.paths).resolve("a") }

            // Vanilla versions resolve to themselves.
            install(Fixture.text("1.20.1"), "1.20.1", tmp.paths)
            val vanilla = VersionResolver(tmp.paths).resolve("1.20.1")
            assertFalse(vanilla.isModded)
            assertEquals("1.20.1", vanilla.info.clientJarId)
        }
    }

    @Test
    fun loaderVersionLists() = runBlocking {
        HttpHeaders.userAgent = "Voxelium/test"
        val http = MockHttp()
        http.on("https://meta.fabricmc.net/v2/versions/loader/1.21.11", 200, Fixture.text("fabric-loaders-1.21.11"))
        http.on("https://meta.quiltmc.org/v3/versions/loader/1.21.11", 200, Fixture.text("quilt-loaders-1.21.11"))
        val meta = ModLoaderMeta(http)

        val f = meta.versions(ModLoader.FABRIC, "1.21.11")
        assertEquals(listOf("0.20.0", "0.19.5", "0.19.4"), f.map { it.version })
        assertEquals(listOf(false, true, false), f.map { it.stable })
        assertEquals("0.19.5", ModLoaderMeta.recommended(f)?.version)

        val q = meta.versions(ModLoader.QUILT, "1.21.11")
        assertEquals("0.20.0-beta.9", q.first().version)
        assertTrue(q.all { !it.stable })
        assertEquals("0.20.0-beta.9", ModLoaderMeta.recommended(q)?.version) // only betas: newest

        assertTrue(http.requests.first().headers["User-Agent"]?.startsWith("Voxelium/") == true)
    }

    /** Android addition: Quilt's meta lists 1.21.1 builds out of order (checked 2026-11-03). */
    @Test
    fun loaderBuildsAreSortedNewestFirst() = runBlocking {
        val http = MockHttp()
        val unordered = listOf("0.20.0-beta.9", "0.24.0", "0.20.2-beta.1", "0.30.0-beta.8", "0.30.2", "0.30.0", "0.30.10", "0.31.0-beta.10", "0.31.0-beta.2")
        http.on(
            "https://meta.quiltmc.org/v3/versions/loader/1.21.1", 200,
            unordered.joinToString(",", "[", "]") { """{"loader":{"version":"$it"}}""" }
        )
        val q = ModLoaderMeta(http).versions(ModLoader.QUILT, "1.21.1")
        assertEquals(
            listOf("0.31.0-beta.10", "0.31.0-beta.2", "0.30.10", "0.30.2", "0.30.0", "0.30.0-beta.8", "0.24.0", "0.20.2-beta.1", "0.20.0-beta.9"),
            q.map { it.version }
        )
        assertEquals("0.30.10", ModLoaderMeta.recommended(q)?.version)
    }

    @Test
    fun fillsInMissingChecksumsFromMaven() = runBlocking {
        TempDir().use { tmp ->
            val http = MockHttp()
            http.on("https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.20.0/profile/json", 200, Fixture.text("fabric-profile-0.20.0-1.21.11"))
            // Only the loader jar's .sha1 is served; intermediary's lookup fails and is skipped.
            http.on("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.0/fabric-loader-0.20.0.jar.sha1", 200, "7a3a8ce24fa0150aba52f3e1f7cde5ba17195fcf\n")
            val v = ModLoaderInstaller(tmp.paths, ModLoaderMeta(http)).installProfile(ModLoader.FABRIC, "1.21.11", "0.20.0")
            assertEquals("7a3a8ce24fa0150aba52f3e1f7cde5ba17195fcf", v.libraries.first { it.name == "net.fabricmc:fabric-loader:0.20.0" }.artifact?.sha1)
            assertNull(v.libraries.first { it.name.startsWith("net.fabricmc:intermediary:") }.artifact?.sha1)
            assertEquals("2a2c870691d2220eb93492c88fdcd3711ab0fadb", v.libraries.first { it.name == "org.ow2.asm:asm:9.11" }.artifact?.sha1)
            // The saved profile carries the filled-in checksum.
            val saved = VersionInfo.decode(tmp.paths.versionJson(v.id).readText())
            assertEquals("7a3a8ce24fa0150aba52f3e1f7cde5ba17195fcf", saved.libraries.first { it.name == "net.fabricmc:fabric-loader:0.20.0" }.sha1)
        }
    }

    @Test
    fun installsProfileAndChecksTheBaseVersion() = runBlocking {
        TempDir().use { tmp ->
            val http = MockHttp()
            val profile = Fixture.text("quilt-profile-1.21.11")
            val id = (Json.parseToJsonElement(profile) as JsonObject)["id"]!!.jsonPrimitive.content
            val ver = id.removePrefix("quilt-loader-").removeSuffix("-1.21.11")
            http.on("https://meta.quiltmc.org/v3/versions/loader/1.21.11/$ver/profile/json", 200, profile)
            val installer = ModLoaderInstaller(tmp.paths, ModLoaderMeta(http))

            val written = installer.installProfile(ModLoader.QUILT, "1.21.11", ver)
            assertEquals(id, written.id)
            assertEquals("1.21.11", written.inheritsFrom)
            assertTrue(tmp.paths.versionJson(id).exists())

            // A profile built on a different Minecraft version is refused.
            http.on("https://meta.quiltmc.org/v3/versions/loader/1.20.1/$ver/profile/json", 200, profile)
            expect(ModLoaderException.BadProfile("it builds on 1.21.11, not 1.20.1")) {
                installer.installProfile(ModLoader.QUILT, "1.20.1", ver)
            }
        }
    }
}

class ForgeMetaTests {
    @Test
    fun neoForgeBuildNumbersFollowMinecraft() {
        assertEquals("21.1.", ForgeMeta.neoForgePrefix("1.21.1"))
        assertEquals("21.0.", ForgeMeta.neoForgePrefix("1.21"))
        assertEquals("20.2.", ForgeMeta.neoForgePrefix("1.20.2"))
        assertEquals("26.1.0.", ForgeMeta.neoForgePrefix("26.1"))
        assertEquals("26.1.2.", ForgeMeta.neoForgePrefix("26.1.2"))
        // 1.20.1's NeoForge used Forge's installer; older versions have none.
        assertNull(ForgeMeta.neoForgePrefix("1.20.1"))
        assertNull(ForgeMeta.neoForgePrefix("1.19.2"))
        assertNull(ForgeMeta.neoForgePrefix("25w14craftmine"))

        val list = """{"isSnapshot":false,"versions":["21.0.167","21.1.9","21.1.99","21.1.100","21.1.101-beta","21.10.1","0.25w14craftmine.3-beta"]}"""
        val builds = ForgeMeta.neoForgeBuilds(list, "1.21.1")
        assertEquals(listOf("21.1.101-beta", "21.1.100", "21.1.99", "21.1.9"), builds.map { it.version })
        assertEquals(listOf(false, true, true, true), builds.map { it.stable })
        assertEquals("21.1.100", ModLoaderMeta.recommended(builds)?.version)
    }

    @Test
    fun forgeBuildsAndTheRecommendedOne() = runBlocking {
        val xml = """
            <metadata><versioning><versions>
            <version>1.7.10-10.13.4.1614-1.7.10</version><version>1.20.1-47.4.9</version>
            <version>1.20.1-47.4.10</version><version>1.20.1-47.4.28</version><version>1.20.10-1.0</version>
            </versions></versioning></metadata>
        """.trimIndent()
        val http = MockHttp()
        http.on(ForgeMeta.FORGE_METADATA, 200, xml)
        http.on(ForgeMeta.FORGE_PROMOTIONS, 200, """{"promos":{"1.20.1-recommended":"47.4.10","1.20.1-latest":"47.4.28"}}""")
        val builds = ModLoaderMeta(http).versions(ModLoader.FORGE, "1.20.1")
        assertEquals(listOf("47.4.28", "47.4.10", "47.4.9"), builds.map { it.version })
        assertEquals("47.4.10", ModLoaderMeta.recommended(builds)?.version)
        // Without promotions the newest build is offered.
        assertEquals("47.4.28", ModLoaderMeta.recommended(ForgeMeta.forgeBuilds(xml, null, "1.20.1"))?.version)
        try {
            ModLoaderMeta(http).versions(ModLoader.FORGE, "1.99")
            fail("expected NoBuilds")
        } catch (e: ModLoaderException) {
            assertEquals(ModLoaderException.NoBuilds(ModLoader.FORGE, "1.99"), e)
        }
    }

    @Test
    fun emptyDownloadURLsMeanTheInstallerMakesTheFile() {
        val lib = MojangJson.decodeFromString(
            Library.serializer(),
            """{"name":"net.minecraftforge:forge:1.21.11-61.2.0:client","downloads":{"artifact":
               {"path":"net/minecraftforge/forge/1.21.11-61.2.0/forge-1.21.11-61.2.0-client.jar","url":"","sha1":"","size":0}}}"""
        )
        assertTrue(lib.artifact?.isProducedByInstaller == true)
        // It round-trips as an empty URL.
        val again = MojangJson.decodeFromString(Library.serializer(), MojangJson.encodeToString(Library.serializer(), lib))
        assertEquals(lib, again)
    }

    @Test
    fun forgeOnlyFrom117() = runBlocking {
        assertTrue(ModLoader.FORGE.isAvailable("1.17.1"))
        assertTrue(ModLoader.FORGE.isAvailable("1.20.1"))
        assertTrue(ModLoader.FORGE.isAvailable("26.1.2"))
        assertFalse(ModLoader.FORGE.isAvailable("1.16.5"))
        assertFalse(ModLoader.FORGE.isAvailable("1.12.2"))
        assertFalse(ModLoader.NEOFORGE.isAvailable("1.20.1"))
        assertTrue(ModLoader.FABRIC.isAvailable("1.16.5"))

        // Nothing is fetched for a version Forge is not offered for.
        val http = MockHttp()
        try {
            ModLoaderMeta(http).versions(ModLoader.FORGE, "1.16.5")
            fail("expected NotOffered")
        } catch (e: ModLoaderException) {
            assertEquals(ModLoaderException.NotOffered(ModLoader.FORGE, "1.16.5"), e)
        }
        assertTrue(http.requests.isEmpty())
        assertEquals(
            "Voxelium offers Forge for Minecraft 1.17 and newer, not 1.16.5.",
            ModLoaderException.NotOffered(ModLoader.FORGE, "1.16.5").message
        )
    }
}
