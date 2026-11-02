/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/ForgeTests.swift (installer and launch tests) (zalithios
 * e28b28b). Not ported: planFollowsTheDataFolderAfterAReinstall (iOS-only relocatePlan).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.Fixture
import io.github.voxelium.core.MockTransport
import io.github.voxelium.core.MockTransport.Response
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.accounts.MockHttp
import io.github.voxelium.core.accounts.OfflineIdentity
import io.github.voxelium.core.download.DownloadManager
import io.github.voxelium.core.download.FileHash
import io.github.voxelium.core.download.RetryPolicy
import io.github.voxelium.core.logging.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.time.Duration

class ForgeTests {
    @Test
    fun moduleOptionsInTheFormJNIAccepts() {
        val args = listOf(
            "-DlibraryDirectory=/l", "-p", "/a.jar:/b.jar", "--add-modules", "ALL-MODULE-PATH",
            "--add-opens", "java.base/java.util.jar=cpw.mods.securejarhandler",
            "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming", "-DignoreList=client-extra,x.jar"
        )
        assertEquals(
            listOf(
                "-DlibraryDirectory=/l", "--module-path=/a.jar:/b.jar", "--add-modules=ALL-MODULE-PATH",
                "--add-opens=java.base/java.util.jar=cpw.mods.securejarhandler",
                "--add-exports=jdk.naming.dns/com.sun.jndi.dns=java.naming", "-DignoreList=client-extra,x.jar"
            ),
            ForgeLaunch.jniOptions(args)
        )
        assertEquals("-DignoreList=client-extra,x.jar,voxelium-boot.jar", ForgeLaunch.extendIgnoreList(args, listOf("voxelium-boot.jar")).last())
        assertEquals(listOf("-Xmx1G", "--add-opens"), ForgeLaunch.jniOptions(listOf("-Xmx1G", "--add-opens")))
    }

    @Test
    fun turnsOffTheEarlyLoadingWindow() {
        TempDir().use { tmp ->
            val file = File(tmp.dir, "config/fml.toml")
            ForgeLaunch.disableEarlyWindow(tmp.dir)
            assertEquals("earlyWindowControl = false\n", file.readText())

            file.writeText("#Shows an early loading screen\nearlyWindowControl = true\nmaxThreads = -1\n")
            ForgeLaunch.disableEarlyWindow(tmp.dir)
            assertEquals("#Shows an early loading screen\nearlyWindowControl = false\nmaxThreads = -1\n", file.readText())
        }
    }

    private fun write(text: String, path: String, dir: File) {
        val f = File(dir, path)
        f.parentFile.mkdirs()
        f.writeText(text)
    }

    /** Zips the files under [dir] with /usr/bin/zip. */
    private fun zip(dir: File, out: File) {
        val p = ProcessBuilder("/usr/bin/zip", "-qr", out.path, ".").directory(dir).inheritIO().start()
        assertEquals(0, p.waitFor())
    }

    private fun downloader(t: MockTransport) =
        DownloadManager(transport = t, retry = RetryPolicy(maxAttempts = 1, baseDelay = Duration.ZERO), log = Logger(), sleep = {})

    @Test
    fun installsFromAnInstallerAndWritesTheProcessorPlan() = runBlocking {
        TempDir().use { tmp ->
            val paths = tmp.paths
            val tool = "tool jar".toByteArray()
            val universal = "universal jar"
            val src = File(tmp.dir, "installer-src")
            write(
                """
                {"spec":1,"version":"neoforge-20.4.1","minecraft":"1.20.1","json":"/version.json",
                 "data":{"BINPATCH":{"client":"/data/client.lzma","server":"/data/server.lzma"},
                         "PATCHED":{"client":"[net.neoforged:minecraft-client-patched:20.4.1]","server":"[x:y:1]"},
                         "PATCHED_SHA":{"client":"'0123456789abcdef0123456789abcdef01234567'","server":"''"},
                         "MCP_VERSION":{"client":"'1.20.1-2023'","server":"'1'"}},
                 "processors":[
                   {"sides":["server"],"jar":"net.neoforged.installertools:installertools:1.0:fatjar","args":["--task","EXTRACT_FILES"]},
                   {"jar":"net.neoforged.installertools:installertools:1.0:fatjar",
                    "classpath":["net.neoforged.installertools:installertools:1.0:fatjar"],
                    "args":["--task","PROCESS_MINECRAFT_JAR","--side","{SIDE}","--input","{MINECRAFT_JAR}",
                            "--output","{PATCHED}","--apply-patches","{BINPATCH}","--libs","{ROOT}/libraries/",
                            "--neoform","[net.neoforged:neoform:1.20.1-2023:mappings@tsrg.lzma]","--mcp","{MCP_VERSION}"],
                    "outputs":{"{PATCHED}":"{PATCHED_SHA}"}}],
                 "libraries":[
                   {"name":"net.neoforged.installertools:installertools:1.0:fatjar","downloads":{"artifact":{
                     "path":"net/neoforged/installertools/installertools/1.0/installertools-1.0-fatjar.jar",
                     "url":"https://maven.neoforged.net/releases/net/neoforged/installertools/installertools/1.0/installertools-1.0-fatjar.jar",
                     "sha1":"${FileHash.sha1(tool)}","size":${tool.size}}}},
                   {"name":"net.neoforged:neoforge:20.4.1:universal","downloads":{"artifact":{
                     "path":"net/neoforged/neoforge/20.4.1/neoforge-20.4.1-universal.jar","url":"",
                     "sha1":"${FileHash.sha1(universal.toByteArray())}","size":${universal.length}}}}]}
                """.trimIndent(), "install_profile.json", src
            )
            write(
                """
                {"id":"neoforge-20.4.1","inheritsFrom":"1.20.1","type":"release","mainClass":"net.neoforged.fml.startup.Client",
                 "releaseTime":"2026-10-15T01:40:32.034268352",
                 "arguments":{"game":["--fml.neoForgeVersion","20.4.1"],"jvm":["-DlibraryDirectory=${'$'}{library_directory}"]},
                 "libraries":[
                   {"name":"net.neoforged:neoforge:20.4.1:universal","downloads":{"artifact":{
                     "path":"net/neoforged/neoforge/20.4.1/neoforge-20.4.1-universal.jar","url":"","sha1":"","size":0}}},
                   {"name":"net.neoforged:minecraft-client-patched:20.4.1","downloads":{"artifact":{
                     "path":"net/neoforged/minecraft-client-patched/20.4.1/minecraft-client-patched-20.4.1.jar","url":"","sha1":"","size":0}}}]}
                """.trimIndent(), "version.json", src
            )
            write("patches", "data/client.lzma", src)
            write(universal, "maven/net/neoforged/neoforge/20.4.1/neoforge-20.4.1-universal.jar", src)
            val jar = File(tmp.dir, "installer.jar")
            zip(src, jar)
            val jarData = jar.readBytes()

            // NeoForge-shaped files from Forge's Maven: NeoForge is not offered for 1.20.1, and the installer format is the same.
            val installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-20.4.1/forge-1.20.1-20.4.1-installer.jar"
            val http = MockHttp()
            http.on("$installerUrl.sha1", 200, FileHash.sha1(jarData) + "\n")
            val transport = MockTransport()
            transport.enqueue(installerUrl, Response.Ok(jarData))
            transport.enqueue(
                "https://maven.neoforged.net/releases/net/neoforged/installertools/installertools/1.0/installertools-1.0-fatjar.jar",
                Response.Ok(tool)
            )
            val installer = ForgeInstaller(paths, downloader(transport), http)

            val (id, report) = installer.installProfile(ModLoader.FORGE, "1.20.1", "20.4.1")
            assertEquals("neoforge-20.4.1", id)
            assertTrue(report.succeeded)
            val lib = paths.librariesDir
            assertEquals(universal, File(lib, "net/neoforged/neoforge/20.4.1/neoforge-20.4.1-universal.jar").readText())
            assertArrayEquals(tool, File(lib, "net/neoforged/installertools/installertools/1.0/installertools-1.0-fatjar.jar").readBytes())
            assertTrue(paths.versionJson(id).exists())

            val planFile = ForgeInstaller.processorPlan(id, paths)!!
            assertFalse(ForgeInstaller.processorsDone(planFile))
            val toolPath = File(lib, "net/neoforged/installertools/installertools/1.0/installertools-1.0-fatjar.jar").path
            val patched = File(lib, "net/neoforged/minecraft-client-patched/20.4.1/minecraft-client-patched-20.4.1.jar").path
            val patches = File(paths.versionDir(id), "installer-data/data/client.lzma")
            assertEquals("patches", patches.readText())
            // The server-only processor is left out; tokens, [artifacts] and 'literals' are resolved.
            val expected = listOf(
                "processor installertools PROCESS_MINECRAFT_JAR",
                "jar $toolPath",
                "cp $toolPath",
                "arg --task", "arg PROCESS_MINECRAFT_JAR",
                "arg --side", "arg client",
                "arg --input", "arg ${paths.clientJar("1.20.1").path}",
                "arg --output", "arg $patched",
                "arg --apply-patches", "arg ${patches.path}",
                "arg --libs", "arg ${paths.gameDir.path}/libraries/",
                "arg --neoform", "arg ${lib.path}/net/neoforged/neoform/1.20.1-2023/neoform-1.20.1-2023-mappings.tsrg.lzma",
                "arg --mcp", "arg 1.20.1-2023",
                "out $patched 0123456789abcdef0123456789abcdef01234567",
                "end"
            ).joinToString("\n") + "\n"
            assertEquals(expected, planFile.readText())

            // The launch plan: the patched jar is not "missing" before the processors ran, and
            // Forge versions leave the vanilla client jar off the classpath.
            paths.versionDir("1.20.1").mkdirs()
            paths.versionJson("1.20.1").writeText(Fixture.text("1.20.1"))
            val resolved = VersionResolver(paths).resolve(id)
            assertEquals(planFile, ForgeLaunch.processorPlan(resolved, paths))
            val plan = LaunchPlanner(paths).plan(resolved.info, OfflineIdentity.identity("Steve"), includeClientJar = false)
            assertTrue(plan.classpath.any { it.path == patched })
            assertFalse(plan.missingFiles.any { it.path == patched })
            assertFalse(plan.classpath.any { it.name == "1.20.1.jar" })
            // GameInstaller does not try to download it either.
            assertFalse(GameFilePlanner(paths).installItems(resolved.info).any { it.destination.path == patched })
        }
    }

    /** Nothing is fetched or downloaded for a version Forge is not offered for (also the path modpacks take). */
    @Test
    fun installerRefusesVersionsForgeIsNotOfferedFor() = runBlocking {
        TempDir().use { tmp ->
            val http = MockHttp()
            val transport = MockTransport()
            try {
                ForgeInstaller(tmp.paths, downloader(transport), http).installProfile(ModLoader.FORGE, "1.16.5", "36.2.34")
                fail("expected NotOffered")
            } catch (e: ModLoaderException) {
                assertEquals(ModLoaderException.NotOffered(ModLoader.FORGE, "1.16.5"), e)
            }
            assertTrue(http.requests.isEmpty() && transport.requests.isEmpty())
        }
    }
}
