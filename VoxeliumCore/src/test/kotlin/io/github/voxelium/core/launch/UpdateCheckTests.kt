/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/UpdateCheckTests.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.launch

import io.github.voxelium.core.accounts.MockHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpdateCheckTests {
    private val url = UpdateCheck().latestUrl

    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false) =
        """{"tag_name":"$tag","name":"Voxelium ${tag.drop(1)}","html_url":"https://github.com/Vortroxious/Voxelium-android/releases/tag/$tag",""" +
            """"draft":$draft,"prerelease":$prerelease,"assets":[{"name":"Voxelium-${tag.drop(1)}-arm64-v8a.apk","browser_download_url":"https://example.invalid/a.apk","size":5}]}"""

    @Test
    fun comparesVersionsByNumber() {
        assertTrue(UpdateCheck.isNewer("v2.4", "2.3"))
        assertTrue(UpdateCheck.isNewer("2.10", "2.9"))
        assertTrue(UpdateCheck.isNewer("v2.4", "2.3.1"))
        assertTrue(UpdateCheck.isNewer("3.0", "2.12"))
        assertFalse(UpdateCheck.isNewer("v2.3", "2.3"))
        assertFalse(UpdateCheck.isNewer("2.4", "2.4.0"))
        assertFalse(UpdateCheck.isNewer("v2.2", "2.3"))
        assertFalse(UpdateCheck.isNewer("2.4-beta", "2.4"))
    }

    @Test
    fun reportsANewerRelease() = runBlocking {
        val http = MockHttp()
        http.on(url, 200, release("v2.4"))
        val result = UpdateCheck(http).check("2.3")
        val r = (result as? UpdateCheck.Result.Available)?.release ?: return@runBlocking fail("expected an update, got $result")
        assertEquals("2.4", r.version)
        assertTrue(r.htmlUrl.endsWith("/releases/tag/v2.4"))
        assertEquals("Voxelium-2.4-arm64-v8a.apk", r.assets.single().name)
        assertTrue(http.requests.first().headers["User-Agent"]?.startsWith("Voxelium") == true)
    }

    @Test
    fun upToDateWhenTheSameOrOlder() = runBlocking {
        val http = MockHttp()
        http.on(url, 200, release("v2.3"))
        assertEquals(UpdateCheck.Result.UpToDate("2.3"), UpdateCheck(http).check("2.3"))
    }

    @Test
    fun ignoresDraftsAndPreReleases() = runBlocking {
        val http = MockHttp()
        http.on(url, 200, release("v2.5", prerelease = true))
        try {
            UpdateCheck(http).check("2.3")
            fail("expected BadResponse")
        } catch (e: UpdateCheck.CheckException) {
            assertEquals(UpdateCheck.CheckException.BadResponse, e)
        }
    }

    @Test
    fun reportsHTTPErrors() = runBlocking {
        val http = MockHttp()
        http.on(url, 403, """{"message":"API rate limit exceeded"}""")
        try {
            UpdateCheck(http).check("2.3")
            fail("expected HTTP 403")
        } catch (e: UpdateCheck.CheckException) {
            assertEquals(UpdateCheck.CheckException.Http(403), e)
        }
    }
}
