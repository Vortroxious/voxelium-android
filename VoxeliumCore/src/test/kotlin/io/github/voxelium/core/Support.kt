/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Tests/VoxeliumCoreTests/Support.swift (zalithios e28b28b).
 */

package io.github.voxelium.core

import io.github.voxelium.core.download.HttpTransport
import io.github.voxelium.core.download.TransportResponse
import io.github.voxelium.core.paths.AppPaths
import java.io.File
import java.io.IOException
import java.nio.file.Files

object Fixture {
    fun text(name: String): String =
        requireNotNull(Fixture::class.java.getResource("/fixtures/$name.json")) { "missing fixture $name" }.readText()

    fun bytes(name: String): ByteArray =
        requireNotNull(Fixture::class.java.getResource("/fixtures/$name.json")) { "missing fixture $name" }.readBytes()
}

/** A fresh temp directory per test; call [close] (or use `use`) to remove it. */
class TempDir : AutoCloseable {
    val dir: File = Files.createTempDirectory("vx-test-").toFile()
    val paths: AppPaths get() = AppPaths(dir)

    override fun close() {
        dir.deleteRecursively()
    }
}

/** Scripted transport: each URL gets a queue of responses, consumed in order. */
class MockTransport : HttpTransport {
    sealed class Response {
        data class Ok(val data: ByteArray) : Response()
        data class Status(val code: Int, val data: ByteArray = ByteArray(0)) : Response()
        data class Fail(val reason: String) : Response()
    }

    private val lock = Any()
    private val queues = mutableMapOf<String, ArrayDeque<Response>>()
    private val requestLog = mutableListOf<String>()

    val requests: List<String> get() = synchronized(lock) { requestLog.toList() }

    fun enqueue(url: String, vararg responses: Response) = synchronized(lock) {
        queues.getOrPut(url) { ArrayDeque() }.addAll(responses)
    }

    fun requestCount(url: String): Int = synchronized(lock) { requestLog.count { it == url } }

    override suspend fun download(url: String): TransportResponse {
        val response = synchronized(lock) {
            requestLog.add(url)
            val q = queues[url]
            if (q.isNullOrEmpty()) {
                Response.Status(404)
            } else {
                // Keep the last response sticky so retries past the script repeat it.
                if (q.size > 1) q.removeFirst() else q.first()
            }
        }
        val tmp = File.createTempFile("vx-mock-", null)
        return when (response) {
            is Response.Ok -> TransportResponse(tmp.also { it.writeBytes(response.data) }, 200)
            is Response.Status -> TransportResponse(tmp.also { it.writeBytes(response.data) }, response.code)
            is Response.Fail -> {
                tmp.delete()
                throw IOException(response.reason)
            }
        }
    }
}
