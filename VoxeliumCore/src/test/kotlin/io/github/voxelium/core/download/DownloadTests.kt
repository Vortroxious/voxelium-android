/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/DownloadTests.swift (zalithios e28b28b).
 */

package io.github.voxelium.core.download

import io.github.voxelium.core.MockTransport
import io.github.voxelium.core.MockTransport.Response
import io.github.voxelium.core.TempDir
import io.github.voxelium.core.logging.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DownloadTests {
    private val body = "hello minecraft".toByteArray()
    private val sha = FileHash.sha1(body)
    private val url = "https://example.invalid/file.jar"

    private fun manager(t: MockTransport, attempts: Int = 3) =
        DownloadManager(transport = t, retry = RetryPolicy(maxAttempts = attempts, baseDelay = Duration.ZERO), log = Logger(), sleep = {})

    @Test
    fun knownSHA1() {
        // `printf 'hello minecraft' | shasum -a1`
        assertEquals("0205c49d7dadcddde7b919c2b0763dd43d1679f0", sha)
    }

    @Test
    fun downloadsAndVerifies() = runBlocking {
        TempDir().use { tmp ->
            val t = MockTransport()
            t.enqueue(url, Response.Ok(body))
            val dest = File(tmp.dir, "a/b/file.jar")
            val out = manager(t).ensure(DownloadItem(url, dest, sha, body.size.toLong()))
            assertEquals(DownloadOutcome.Downloaded(1), out)
            assertArrayEquals(body, dest.readBytes())
        }
    }

    @Test
    fun skipsAlreadyValidFile() = runBlocking {
        TempDir().use { tmp ->
            val dest = File(tmp.dir, "file.jar").apply { writeBytes(body) }
            val t = MockTransport()
            val out = manager(t).ensure(DownloadItem(url, dest, sha, null))
            assertEquals(DownloadOutcome.AlreadyValid, out)
            assertTrue(t.requests.isEmpty())
        }
    }

    @Test
    fun replacesCorruptExistingFile() = runBlocking {
        TempDir().use { tmp ->
            val dest = File(tmp.dir, "file.jar").apply { writeBytes("garbage!!!!!!!!".toByteArray()) } // same size, wrong hash
            val t = MockTransport()
            t.enqueue(url, Response.Ok(body))
            val out = manager(t).ensure(DownloadItem(url, dest, sha, body.size.toLong()))
            assertEquals(DownloadOutcome.Downloaded(1), out)
            assertArrayEquals(body, dest.readBytes())
        }
    }

    @Test
    fun retriesOnHashMismatchThenSucceeds() = runBlocking {
        TempDir().use { tmp ->
            val t = MockTransport()
            t.enqueue(url, Response.Ok("truncated".toByteArray()), Response.Fail("connection lost"), Response.Ok(body))
            val out = manager(t, attempts = 4).ensure(DownloadItem(url, File(tmp.dir, "file.jar"), sha, null))
            assertEquals(DownloadOutcome.Downloaded(3), out)
            assertEquals(3, t.requestCount(url))
        }
    }

    @Test
    fun retriesServerErrors() = runBlocking {
        TempDir().use { tmp ->
            val t = MockTransport()
            t.enqueue(url, Response.Status(503), Response.Status(429), Response.Ok(body))
            val out = manager(t).ensure(DownloadItem(url, File(tmp.dir, "f"), sha, null))
            assertEquals(DownloadOutcome.Downloaded(3), out)
        }
    }

    @Test
    fun doesNotRetryNotFound() = runBlocking {
        TempDir().use { tmp ->
            val t = MockTransport()
            t.enqueue(url, Response.Status(404))
            try {
                manager(t).ensure(DownloadItem(url, File(tmp.dir, "f"), sha, null))
                fail("expected HTTP 404")
            } catch (e: DownloadException) {
                assertEquals(DownloadException.HttpStatus(404, url), e)
            }
            assertEquals(1, t.requestCount(url))
        }
    }

    @Test
    fun givesUpAfterMaxAttemptsAndLeavesNoFile() = runBlocking {
        TempDir().use { tmp ->
            val t = MockTransport()
            t.enqueue(url, Response.Ok("wrong".toByteArray()))
            val dest = File(tmp.dir, "f")
            try {
                manager(t, attempts = 3).ensure(DownloadItem(url, dest, sha, null))
                fail("expected exhausted")
            } catch (e: DownloadException.Exhausted) {
                assertEquals(3, e.attempts)
            }
            assertEquals(3, t.requestCount(url))
            assertFalse(dest.exists())
        }
    }

    @Test
    fun failedDownloadDoesNotClobberExistingFile() = runBlocking {
        // The existing file is invalid, but a failed re-download must not
        // replace it with something worse (or delete it mid-way).
        TempDir().use { tmp ->
            val old = "old".toByteArray()
            val dest = File(tmp.dir, "f").apply { writeBytes(old) }
            val t = MockTransport()
            t.enqueue(url, Response.Status(500))
            runCatching { manager(t, attempts = 2).ensure(DownloadItem(url, dest, sha, null)) }
            assertArrayEquals(old, dest.readBytes())
        }
    }

    @Test
    fun batchCollectsFailuresAndReportsProgress() = runBlocking {
        TempDir().use { tmp ->
            val t = MockTransport()
            val items = (0 until 20).map { i ->
                val u = "https://example.invalid/$i"
                if (i == 7) t.enqueue(u, Response.Status(404)) else t.enqueue(u, Response.Ok(body))
                DownloadItem(u, File(tmp.dir, "$i"), sha, null)
            }
            val seen = AtomicInteger(0)
            val totals = mutableSetOf<Int>()
            val report = manager(t).run(items + items.take(3), concurrency = 4) { p ->
                seen.accumulateAndGet(p.completed) { a, b -> maxOf(a, b) }
                totals.add(p.total)
            }
            assertEquals(setOf(20), totals)
            assertEquals(19, report.outcomes.size)
            assertEquals(1, report.failures.size)
            assertFalse(report.succeeded)
            assertEquals(20, seen.get())
        }
    }

    @Test
    fun backoffIsExponentialAndCapped() {
        val p = RetryPolicy(maxAttempts = 10, baseDelay = 500.milliseconds, maxDelay = 4.seconds)
        assertEquals(500.milliseconds, p.delay(1))
        assertEquals(1.seconds, p.delay(2))
        assertEquals(2.seconds, p.delay(3))
        assertEquals(4.seconds, p.delay(6))
    }

    @Test
    fun integrityResults() {
        TempDir().use { tmp ->
            val f = File(tmp.dir, "x")
            assertEquals(IntegrityResult.Missing, FileHash.verify(f, sha, null))
            f.writeBytes(body)
            assertEquals(IntegrityResult.SizeMismatch(expected = 1, actual = body.size.toLong()), FileHash.verify(f, sha, 1))
            assertEquals(IntegrityResult.Valid, FileHash.verify(f, sha.uppercase(), null))
            assertEquals(IntegrityResult.Unverified, FileHash.verify(f, null, null))
            assertTrue(FileHash.verify(f, "0".repeat(40), null) is IntegrityResult.HashMismatch)
        }
    }
}
