/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.voxelium.core.download

import io.github.voxelium.core.TempDir
import io.github.voxelium.core.logging.Logger
import io.github.voxelium.core.manifest.VersionInfo
import io.github.voxelium.core.manifest.VersionManifest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Real downloads through [OkHttpTransport]; skipped unless run with
 * `./gradlew :VoxeliumCore:test -Pvoxelium.networkTests=true`.
 */
class NetworkTests {
    @Test
    fun downloadsManifestAndVerifiedVersionJson() = runBlocking {
        assumeTrue(System.getProperty("voxelium.networkTests") == "true")
        TempDir().use { tmp ->
            val manager = DownloadManager(log = Logger())
            val manifestFile = File(tmp.dir, "version_manifest_v2.json")
            manager.ensure(DownloadItem(VersionManifest.OFFICIAL_URL, manifestFile, null, null))
            val entry = VersionManifest.decode(manifestFile.readText()).entry("1.21.1")!!

            val versionFile = File(tmp.dir, "1.21.1.json")
            val outcome = manager.ensure(DownloadItem(entry.url, versionFile, entry.sha1, null))
            assertEquals(DownloadOutcome.Downloaded(1), outcome)
            assertEquals("1.21.1", VersionInfo.decode(versionFile.readText()).id)
        }
    }
}
